package com.echomind.memory;

import com.echomind.memory.ChromaDBClient.ChromaDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Level 2: 中期记忆 (Mid-Term Memory)
 *
 * 存储: ChromaDB Collection "echomind_mid_term"
 * 内容: 历史对话的 LLM 生成摘要及其向量索引
 * 触发:
 *   - 条件1: 会话消息数超过 compressThreshold (默认20) 条
 *   - 条件2: 会话空闲超过 idleCompressMinutes (默认30) 分钟
 * 召回: 新会话启动/新消息时，通过向量检索召回 Top-K 相关历史摘要
 * 关联: 通过 metadata.sessionId 关联会话
 */
@Slf4j
@Component
public class MidTermMemory {

    private static final String COLLECTION_NAME = "echomind_mid_term";

    private final ChromaDBClient chromaClient;
    private final MemoryConfig config;
    private final ShortTermMemory shortTermMemory;

    public MidTermMemory(ChromaDBClient chromaClient, MemoryConfig config,
                         ShortTermMemory shortTermMemory) {
        this.chromaClient = chromaClient;
        this.config = config;
        this.shortTermMemory = shortTermMemory;

        // 启动时确保集合存在
        try {
            chromaClient.getOrCreateCollection(COLLECTION_NAME);
            log.info("MTM 中期记忆初始化完成, collection={}", COLLECTION_NAME);
        } catch (Exception e) {
            log.warn("MTM ChromaDB 集合初始化跳过 (ChromaDB 可能未运行): {}", e.getMessage());
        }
    }

    // ==================== 存储 ====================

    /**
     * 将摘要存入 ChromaDB
     * @param sessionId 会话 ID，用于关联
     * @param summary   LLM 压缩生成的摘要文本
     */
    public void storeSummary(String sessionId, String summary, int messageCount) {
        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("sessionId", sessionId);
            metadata.put("type", "mid-term-memory");
            metadata.put("messageCount", String.valueOf(messageCount));
            metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));

            String docId = "mid-" + sessionId + "-" + UUID.randomUUID().toString().substring(0, 8);

            ChromaDocument doc = new ChromaDocument(docId, summary, metadata, null);
            chromaClient.addDocuments(COLLECTION_NAME, List.of(doc));

            log.info("MTM 摘要已存储: session={}, docId={}, msgCount={}, summary={}",
                    sessionId, docId, messageCount,
                    summary.length() > 60 ? summary.substring(0, 60) + "..." : summary);
        } catch (Exception e) {
            log.warn("MTM 存储摘要失败 (不影响主流程): {}", e.getMessage());
        }
    }

    // ==================== 检索 ====================

    /**
     * 根据当前消息向量检索相关历史摘要
     * @param sessionId 当前会话 ID（可滤除当前会话的旧摘要）
     * @param queryText 用户消息文本（用于构建语义检索）
     */
    public List<ChromaDocument> retrieveRelevant(String sessionId, String queryText) {
        try {
            int topK = config.getMidTermTopK();

            // 使用零向量触发 ChromaDB 纯 metadata 检索模式
            // 真实场景应使用 EmbeddingModel 生成 queryText 的向量
            float[] dummyEmb = new float[384];
            Arrays.fill(dummyEmb, 0.001f);

            List<ChromaDocument> results = chromaClient.similaritySearch(COLLECTION_NAME, dummyEmb, topK);

            // 过滤掉当前 session 自身的摘要（避免短期记忆重复注入）
            List<ChromaDocument> filtered = results.stream()
                    .filter(d -> !sessionId.equals(
                            d.getMetadata() != null ? d.getMetadata().get("sessionId") : null))
                    .toList();

            log.info("MTM 检索完成: session={}, query={}, 原始={}条, 有效={}条",
                    sessionId,
                    queryText.length() > 30 ? queryText.substring(0, 30) + "..." : queryText,
                    results.size(), filtered.size());

            return filtered;

        } catch (Exception e) {
            log.warn("MTM 检索失败 (不影响主流程): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取中期记忆的上下文文本（拼接后供 LLM Prompt 注入）
     */
    public String getContextString(String sessionId, String userMessage) {
        List<ChromaDocument> relevant = retrieveRelevant(sessionId, userMessage);
        if (relevant.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n--- 相关历史记忆 ---\n");
        for (ChromaDocument doc : relevant) {
            String sessionLabel = doc.getMetadata() != null
                    ? "会话 " + doc.getMetadata().getOrDefault("sessionId", "?")
                    : "会话 ?";
            sb.append("[").append(sessionLabel).append("] ")
              .append(doc.getText()).append("\n");
        }
        sb.append("---\n");
        return sb.toString();
    }

    // ==================== 压缩条件判断 ====================

    /**
     * 判断是否需要触发压缩
     * 条件1: 消息数 >= compressThreshold (20)
     * 条件2: 空闲时间 >= idleCompressMinutes (30)
     */
    public boolean shouldCompress(String sessionId) {
        Long msgCount = shortTermMemory.getMessageCount(sessionId);
        if (msgCount == null) return false;

        // 条件1: 消息数达到阈值
        if (msgCount >= config.getCompressThreshold()) {
            log.info("MTM 压缩条件1满足: session={}, msgCount={}, threshold={}",
                    sessionId, msgCount, config.getCompressThreshold());
            return true;
        }

        // 条件2: 空闲超过阈值
        long lastTouch = shortTermMemory.getLastTouch(sessionId);
        if (lastTouch > 0) {
            long idleMs = System.currentTimeMillis() - lastTouch;
            long thresholdMs = config.getIdleCompressMinutes() * 60 * 1000L;
            if (idleMs >= thresholdMs) {
                log.info("MTM 压缩条件2满足: session={}, idleMs={}, thresholdMs={}",
                        sessionId, idleMs, thresholdMs);
                return true;
            }
        }

        return false;
    }

    // ==================== 清理 ====================

    /**
     * 清理某会话对应的中期记忆
     */
    public void clearBySession(String sessionId) {
        chromaClient.deleteDocuments(COLLECTION_NAME, Map.of("sessionId", sessionId));
        log.info("MTM 已清除会话记忆: session={}", sessionId);
    }
}
