package com.echomind.controller;

import com.echomind.dto.ChatRequest;
import com.echomind.dto.ChatResponse;
import com.echomind.dto.IntentResult;
import com.echomind.intent.IntentRecognitionService;
import com.echomind.memory.MemoryManager;
import com.echomind.agent.AgentRouter;
import com.echomind.monitor.MonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;

/**
 * 对话接口
 *
 * 完整处理链路:
 *   1. 记忆注入 (L3+L2+L1) → 意图识别 → Agent 路由 → 响应生成
 *   2. 响应后: 更新短期记忆 → 异步压缩 → 异步更新用户画像
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {

    private final IntentRecognitionService intentService;
    private final AgentRouter agentRouter;
    private final MemoryManager memoryManager;
    private final MonitorService monitorService;

    public ChatController(IntentRecognitionService intentService,
                          AgentRouter agentRouter,
                          MemoryManager memoryManager,
                          MonitorService monitorService) {
        this.intentService = intentService;
        this.agentRouter = agentRouter;
        this.memoryManager = memoryManager;
        this.monitorService = monitorService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        long start = System.currentTimeMillis();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        String userId = String.valueOf(request.getUserId());

        log.info("对话请求: userId={}, message={}, sessionId={}",
                request.getUserId(), request.getMessage(), sessionId);

        try {
            // Step 1: 存储用户消息到短期记忆，触发压缩检查
            memoryManager.onUserMessage(sessionId, request.getUserId(), request.getMessage());

            // Step 2: 三路融合意图识别
            IntentResult intent = intentService.recognize(userId, request.getMessage(), sessionId);

            // Step 3: Agent 路由 + 执行（内部注入三级记忆）
            AgentRouter.AgentResult agentResult = agentRouter.execute(
                    userId, request.getMessage(), sessionId, intent);

            // Step 4: 存储 AI 回复到短期记忆
            memoryManager.onAssistantMessage(sessionId, request.getUserId(), agentResult.answer());

            // Step 5: 异步更新用户画像
            memoryManager.extractUserProfile(request.getUserId(), request.getMessage(), intent.getCategory());

            // Step 6: 记录监控指标
            monitorService.recordCall(agentResult.agentUsed(), agentResult.elapsedMs(), 0, true);

            ChatResponse.ChatData data = ChatResponse.ChatData.builder()
                    .answer(agentResult.answer())
                    .intent(intent.getCategory())
                    .intentConfidence(intent.getConfidence())
                    .agentUsed(agentResult.agentUsed())
                    .sessionId(sessionId)
                    .sources(new ArrayList<>())
                    .metrics(new ChatResponse.Metrics(
                            System.currentTimeMillis() - start, 0))
                    .build();

            return ChatResponse.success(data);

        } catch (Exception e) {
            log.error("对话处理异常", e);
            monitorService.recordCall("system", System.currentTimeMillis() - start, 0, false);
            return ChatResponse.fail("智能客服服务暂时不可用，请稍后再试");
        }
    }

    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
        String userId = String.valueOf(request.getUserId());

        log.info("流式对话请求: userId={}, message={}, sessionId={}",
                request.getUserId(), request.getMessage(), sessionId);

        try {
            // Step 1: 存储用户消息到短期记忆
            memoryManager.onUserMessage(sessionId, request.getUserId(), request.getMessage());

            // Step 2: 意图识别
            IntentResult intent = intentService.recognize(userId, request.getMessage(), sessionId);

            // Step 3: Agent 路由执行（获取完整回答）
            AgentRouter.AgentResult agentResult = agentRouter.execute(
                    userId, request.getMessage(), sessionId, intent);

            String answer = agentResult.answer();

            // Step 4: 存储 AI 回复
            memoryManager.onAssistantMessage(sessionId, request.getUserId(), answer);

            // Step 5: 异步更新用户画像
            memoryManager.extractUserProfile(request.getUserId(), request.getMessage(), intent.getCategory());

            // Step 6: 记录监控指标
            monitorService.recordCall(agentResult.agentUsed(), agentResult.elapsedMs(), 0, true);

            // 构建真正的 SSE 流式响应
            return Flux.concat(
                    // 1. 思考中
                    Flux.just(ServerSentEvent.<String>builder()
                            .event("message")
                            .data("{\"type\":\"thinking\",\"content\":\"正在处理您的请求...\"}")
                            .build()),

                    // 2. 逐字流式输出（模拟逐 token 推送）
                    Flux.fromArray(answer.split(""))
                            .delayElements(Duration.ofMillis(30))
                            .map(ch -> ServerSentEvent.<String>builder()
                                    .event("message")
                                    .data("{\"type\":\"token\",\"content\":\"" + escapeJson(ch) + "\"}")
                                    .build()),

                    // 3. 完成信号
                    Flux.just(ServerSentEvent.<String>builder()
                            .event("message")
                            .data("{\"type\":\"done\",\"content\":null}")
                            .build())
            );

        } catch (Exception e) {
            log.error("流式对话处理异常", e);
            monitorService.recordCall("system", 0, 0, false);
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("message")
                    .data("{\"type\":\"error\",\"content\":\"服务异常，请稍后重试\"}")
                    .build());
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
