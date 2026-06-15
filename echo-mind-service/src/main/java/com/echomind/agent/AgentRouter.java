package com.echomind.agent;

import com.echomind.dto.IntentResult;
import com.echomind.knowledge.MultiPathRAGRetriever;
import com.echomind.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 两层路由编排入口
 * 意图路由 → 降级保护
 *
 * Prompt 上下文注入顺序:
 *   1. System Prompt（Agent 配置）
 *   2. Memory Context（L3长期画像 + L2历史摘要 + L1当前对话）
 *   3. RAG 知识库（多路并行检索：LLM 多Query扩展 + 向量检索 + BM25关键词 → RRF融合）
 *   4. Function Calling（Spring AI @Tool 方式，LLM 自主调用业务工具获取数据）
 *   5. User Message（当前用户提问）
 */
@Slf4j
@Component
public class AgentRouter {

    private final IntentRouter intentRouter;
    private final FallbackStrategy fallbackStrategy;
    private final AgentRegistry agentRegistry;
    private final ChatClient chatClient;
    private final MemoryManager memoryManager;
    private final BusinessTools businessTools;
    private final MultiPathRAGRetriever ragRetriever;

    public AgentRouter(IntentRouter intentRouter,
                   FallbackStrategy fallbackStrategy,
                   AgentRegistry agentRegistry,
                   ChatClient chatClient,
                   MemoryManager memoryManager,
                   BusinessTools businessTools,
                   MultiPathRAGRetriever ragRetriever) {
        this.intentRouter = intentRouter;
        this.fallbackStrategy = fallbackStrategy;
        this.agentRegistry = agentRegistry;
        this.chatClient = chatClient;
        this.memoryManager = memoryManager;
        this.businessTools = businessTools;
        this.ragRetriever = ragRetriever;
    }

    /**
     * 意图路由 + 执行 Agent（含降级保护）
     */
    public AgentResult execute(String userId, String message, String sessionId, IntentResult intent) {
        long start = System.currentTimeMillis();

        // Layer 1: 意图路由
        String targetAgent = intentRouter.route(intent);

        // Layer 2: 执行（含降级保护）
        String answer = executeWithFallback(userId, message, sessionId, targetAgent, intent);

        long elapsed = System.currentTimeMillis() - start;

        return new AgentResult(answer, targetAgent, elapsed);
    }

    private String executeWithFallback(String userId, String message, String sessionId,
                                       String agentCode, IntentResult intent) {
        int retry = 0;
        while (retry <= 2) {
            try {
                return callAgent(userId, message, sessionId, agentCode, intent);
            } catch (Exception e) {
                log.error("Agent执行异常: agent={}, retry={}", agentCode, retry, e);
                agentCode = fallbackStrategy.fallback(agentCode, retry);
                retry++;
            }
        }
        return fallbackStrategy.getFallbackMessage();
    }

    private String callAgent(String userId, String message, String sessionId,
                         String agentCode, IntentResult intent) {
        AgentRegistry.AgentConfig config = agentRegistry.getAgent(agentCode);
        if (config == null) {
            config = agentRegistry.getAgent("general");
        }

        // ========== 三级记忆逐级注入 ==========
        String memoryContext = memoryManager.buildContext(sessionId, Long.valueOf(userId), message);

        // ========== RAG 知识库检索（多路并行：LLM 多Query扩展 + 向量检索 + BM25关键词 → RRF融合） ==========
        String ragKnowledge = ragRetriever.retrieve(message);
        if (!ragKnowledge.isBlank()) {
            log.debug("RAG 知识库命中: session={}, msg={}", sessionId,
                    message.substring(0, Math.min(30, message.length())));
        }

        // ========== 构建 Prompt ==========
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(config.systemPrompt()).append("\n\n");

        if (!memoryContext.isBlank()) {
            promptBuilder.append(memoryContext).append("\n\n");
        }
        if (!ragKnowledge.isBlank()) {
            promptBuilder.append("【仅供参考】\n");
            promptBuilder.append(ragKnowledge).append("\n\n");
        }
        promptBuilder.append("用户: ").append(message);

        // ========== 调用 LLM（Spring AI @Tool 自动处理 Function Calling 循环） ==========
        UserContext.setUserId(Long.valueOf(userId));
        try {
            String answer = chatClient.prompt()
                    .user(promptBuilder.toString())
                    .tools(businessTools)
                    .call()
                    .content();
            return answer != null ? answer : "抱歉，我暂时无法回答这个问题，请稍后再试。";
        } finally {
            UserContext.clear();
        }
    }

    public record AgentResult(String answer, String agentUsed, long elapsedMs) {}
}