package com.echomind.agent;

import com.echomind.dto.IntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Layer 1: 意图路由
 * 根据意图类别路由到对应的 Agent
 */
@Slf4j
@Component
public class IntentRouter {

    private static final Map<String, String> INTENT_AGENT_MAP = Map.ofEntries(
            Map.entry("shop_query", "general"),
            Map.entry("shop_recommend", "general"),
            Map.entry("shop_compare", "general"),
            Map.entry("voucher_query", "voucher"),
            Map.entry("voucher_use", "voucher"),
            Map.entry("seckill_query", "seckill"),
            Map.entry("user_profile", "user"),
            Map.entry("user_follow", "user"),
            Map.entry("user_signin", "user"),
            Map.entry("general_help", "general"),
            Map.entry("general_chat", "general"),
            Map.entry("complaint", "complaint"),
            Map.entry("unknown", "general"),
            Map.entry("fallback_human", "general")
    );

    /**
     * 根据意图路由到对应 Agent
     */
    public String route(IntentResult intent) {
        String agentCode = INTENT_AGENT_MAP.getOrDefault(intent.getCategory(), "general");
        log.info("意图路由: intent={}, confidence={}, agent={}",
                intent.getCategory(), intent.getConfidence(), agentCode);
        return agentCode;
    }
}
