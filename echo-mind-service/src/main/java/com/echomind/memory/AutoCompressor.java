package com.echomind.memory;

import com.echomind.mapper.MessageMapper;
import com.echomind.entity.Message;
import com.echomind.memory.ChromaDBClient.ChromaDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自动压缩引擎
 *
 * 触发条件:
 *   1. 会话消息数超过 compressThreshold (默认20)
 *   2. 会话空闲超过 idleCompressMinutes (默认30分钟)
 * 流程:
 *   读取短期记忆中未压缩的历史消息 → LLM 压缩为摘要 → 存入 ChromaDB (MTM)
 */
@Slf4j
@Component
public class AutoCompressor {

    private static final String COMPRESS_PROMPT = """
            你是一位对话摘要专家。请将以下多轮对话压缩为一段简洁的摘要，
            保留关键信息：用户的核心需求、提到的商户/优惠券名称、用户的偏好和意图。

            对话内容:
            {conversation}

            请用2-3句话概括以上对话的核心内容，重点关注业务相关的信息。
            """;

    private final ChatClient chatClient;
    private final MidTermMemory midTermMemory;
    private final ShortTermMemory shortTermMemory;
    private final MessageMapper messageMapper;

    public AutoCompressor(ChatClient chatClient,
                          MidTermMemory midTermMemory,
                          ShortTermMemory shortTermMemory,
                          MessageMapper messageMapper) {
        this.chatClient = chatClient;
        this.midTermMemory = midTermMemory;
        this.shortTermMemory = shortTermMemory;
        this.messageMapper = messageMapper;
    }

    /**
     * 执行压缩：读取短期记忆中的消息 → LLM 摘要 → 存入 ChromaDB
     * 异步执行，不阻塞主流程
     */
    @Async
    public void compressIfNeeded(String sessionId) {
        if (!midTermMemory.shouldCompress(sessionId)) return;

        log.info("压缩触发: session={}", sessionId);
        compressConversation(sessionId);
    }

    /**
     * 强制压缩
     */
    @Async
    public void compressConversation(String sessionId) {
        try {
            // 1. 从短期记忆中读取消息
            List<String> messages = shortTermMemory.getRecentMessages(sessionId);
            if (messages == null || messages.isEmpty()) {
                log.debug("压缩跳过: 无消息 session={}", sessionId);
                return;
            }

            // 2. 构建对话文本
            String conversationText = buildConversationText(messages);
            if (conversationText.isBlank()) return;

            // 3. LLM 压缩
            String summary = chatClient.prompt()
                    .user(u -> u.text(COMPRESS_PROMPT.replace("{conversation}", conversationText)))
                    .call()
                    .content();

            if (summary == null || summary.isBlank()) {
                log.warn("LLM 压缩返回空结果, session={}", sessionId);
                return;
            }

            // 4. 存入 ChromaDB 中期记忆
            midTermMemory.storeSummary(sessionId, summary, messages.size());

            log.info("压缩完成: session={}, 原始{}条消息, 摘要={}",
                    sessionId, messages.size(),
                    summary.length() > 60 ? summary.substring(0, 60) + "..." : summary);

        } catch (Exception e) {
            log.error("压缩异常 session={}", sessionId, e);
        }
    }

    private String buildConversationText(List<String> messages) {
        StringBuilder sb = new StringBuilder();
        for (String msg : messages) {
            String[] parts = msg.split("::", 2);
            if (parts.length == 2) {
                sb.append(parts[0].equals("user") ? "用户" : "助手")
                  .append(": ").append(parts[1]).append("\n");
            }
        }
        return sb.toString();
    }
}
