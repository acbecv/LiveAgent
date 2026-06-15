package com.echomind.agent;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心
 * 管理所有 Agent 实例的注册和发现
 */
@Component
public class AgentRegistry {

    private final Map<String, AgentConfig> agents = new ConcurrentHashMap<>();

    public AgentRegistry() {
        // 注册所有 Agent
        for (AgentType type : AgentType.values()) {
            agents.put(type.getCode(), new AgentConfig(type));
        }
    }

    public AgentConfig getAgent(String code) {
        return agents.get(code);
    }

    public Map<String, AgentConfig> getAllAgents() {
        return Map.copyOf(agents);
    }

    public record AgentConfig(
            String code,
            String description,
            String systemPrompt,
            Integer maxTokens,
            Double temperature
    ) {
        public AgentConfig(AgentType type) {
            this(type.getCode(), type.getDescription(), type.getSystemPrompt(),
                 type.getMaxTokens(), type.getTemperature());
        }
    }
}
