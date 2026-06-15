package com.echomind.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 三级记忆统一入口
 *
 * 通过 sessionId 和 userId 关联三层记忆，在意图识别、Agent 路由和响应生成阶段逐级注入。
 *
 * 架构:
 *   Level 1 - ShortTermMemory (Redis List)  → 亚毫秒级，当前会话上下文
 *   Level 2 - MidTermMemory    (ChromaDB)   → 历史摘要向量检索
 *   Level 3 - LongTermMemory   (ChromaDB+MySQL) → 用户画像/偏好
 *
 * 调用链路:
 *   ChatController → storeMessage() → buildContext() [注入到 AgentRouter]
 *                 → triggerCompression() [异步]
 *                 → extractUserProfile() [异步]
 */
@Slf4j
@Service
public class MemoryManager {

    private final ShortTermMemory shortTermMemory;
    private final MidTermMemory midTermMemory;
    private final LongTermMemory longTermMemory;
    private final AutoCompressor autoCompressor;
    private final UserProfileExtractor userProfileExtractor;

    public MemoryManager(ShortTermMemory shortTermMemory,
                         MidTermMemory midTermMemory,
                         LongTermMemory longTermMemory,
                         AutoCompressor autoCompressor,
                         UserProfileExtractor userProfileExtractor) {
        this.shortTermMemory = shortTermMemory;
        this.midTermMemory = midTermMemory;
        this.longTermMemory = longTermMemory;
        this.autoCompressor = autoCompressor;
        this.userProfileExtractor = userProfileExtractor;
    }

    // ==================== 写入 ====================

    /**
     * 每次对话消息后调用
     * 1. 写入短期记忆 (Redis)
     * 2. 更新最后活跃时间
     * 3. 检查是否需要触发压缩
     */
    public void onUserMessage(String sessionId, Long userId, String message) {
        shortTermMemory.addMessage(sessionId, "user", message);
        shortTermMemory.touch(sessionId);
        autoCompressor.compressIfNeeded(sessionId);
    }

    public void onAssistantMessage(String sessionId, Long userId, String answer) {
        shortTermMemory.addMessage(sessionId, "assistant", answer);
        shortTermMemory.touch(sessionId);
    }

    /**
     * 存储消息到短期记忆（保留原接口兼容）
     */
    public void storeMessage(String sessionId, Long userId, String role, String content) {
        shortTermMemory.addMessage(sessionId, role, content);
        shortTermMemory.touch(sessionId);
    }

    // ==================== 读取（逐级注入） ====================

    /**
     * 构建完整的 Prompt 上下文（三级记忆融合）
     *
     * 注入顺序:
     *   1. Level 3: 长期记忆 — 用户画像/偏好
     *   2. Level 2: 中期记忆 — 相关历史摘要（基于当前消息检索）
     *   3. Level 1: 短期记忆 — 当前会话最近 N 轮消息
     */
    public String buildContext(String sessionId, Long userId, String currentMessage) {
        StringBuilder ctx = new StringBuilder();
        String newline = "\n";

        // Level 3: 长期记忆 — 用户画像
        String ltm = longTermMemory.getContextString(userId);
        if (!ltm.isBlank()) ctx.append(ltm).append(newline);

        // Level 2: 中期记忆 — 相关历史摘要
        String mtm = midTermMemory.getContextString(sessionId, currentMessage);
        if (!mtm.isBlank()) ctx.append(mtm).append(newline);

        // Level 1: 短期记忆 — 最近消息
        String stm = shortTermMemory.getContextString(sessionId);
        if (!stm.isBlank()) ctx.append(stm).append(newline);

        return ctx.toString();
    }

    // ==================== 后处理 ====================

    /**
     * 触发异步压缩检查（对话回复完成后调用）
     */
    public void triggerCompression(String sessionId) {
        autoCompressor.compressIfNeeded(sessionId);
    }

    /**
     * 异步提取并保存用户画像（会话结束后调用）
     */
    public void extractUserProfile(Long userId, String message, String intent) {
        userProfileExtractor.extractAndSave(userId, message, intent);
    }

    // ==================== 维护 ====================

    /**
     * 清除会话所有记忆
     */
    public void clearSession(String sessionId) {
        shortTermMemory.clear(sessionId);
        midTermMemory.clearBySession(sessionId);
    }
}
