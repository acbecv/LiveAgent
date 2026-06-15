package com.echomind.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Layer 3: 降级策略
 * 当目标 Agent 不可用时的降级方案
 * 1. 重试 → 2. 备用 Agent → 3. GeneralAgent → 4. 预设话术 → 5. 转人工
 */
@Slf4j
@Component
public class FallbackStrategy {

    private static final String[] FALLBACK_CHAIN = {"general", "fallback"};
    private static final String FALLBACK_MESSAGE = "抱歉，智能客服暂时繁忙，请稍后再试或拨打客服热线 400-xxx-xxxx。";
    private static final int MAX_RETRY = 2;

    /**
     * 执行降级，返回可用的 Agent code
     */
    public String fallback(String originalAgent, int retryCount) {
        log.info("降级策略触发: agent={}, retry={}", originalAgent, retryCount);

        // 1. 重试
        if (retryCount < MAX_RETRY) {
            log.info("降级策略 1: 重试 agent={} (第{}次)", originalAgent, retryCount + 1);
            return originalAgent;
        }

        // 2. 备用 Agent
        for (String fallbackAgent : FALLBACK_CHAIN) {
            if (!fallbackAgent.equals(originalAgent)) {
                log.info("降级策略 2: 切换到备用 agent={}", fallbackAgent);
                return fallbackAgent;
            }
        }

        // 3. 最后返回 fallback
        log.info("降级策略 3: 最终降级到 fallback agent");
        return "fallback";
    }

    /**
     * 获取预设降级话术
     */
    public String getFallbackMessage() {
        return FALLBACK_MESSAGE;
    }
}
