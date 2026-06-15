package com.echomind.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标采集器
 * 采集 Agent 执行过程中的各项性能指标
 */
@Slf4j
@Component
public class MetricsCollector {

    private final Map<String, AgentMetricsData> metricsMap = new ConcurrentHashMap<>();
    private final MonitorConfig monitorConfig;

    public MetricsCollector(MonitorConfig monitorConfig) {
        this.monitorConfig = monitorConfig;
    }

    /**
     * 记录一次 Agent 调用
     */
    public void recordCall(String agentCode, long latencyMs, int tokensUsed, boolean success) {
        AgentMetricsData metrics = metricsMap.computeIfAbsent(agentCode, k -> {
            AgentMetricsData m = new AgentMetricsData();
            m.setAgentCode(k);
            return m;
        });

        // 更新指标（滑动平均）
        long total = metrics.getTotalRequests() + 1;
        metrics.setAvgLatencyMs(
                (metrics.getAvgLatencyMs() * metrics.getTotalRequests() + latencyMs) / total);
        metrics.setTotalTokensUsed(metrics.getTotalTokensUsed() + tokensUsed);
        metrics.setAvgTokensPerRequest(
                (double) metrics.getTotalTokensUsed() / total);

        if (!success) {
            metrics.setErrorRate(
                    (metrics.getErrorRate() * metrics.getTotalRequests() + 1) / total);
        }

        metrics.setTotalRequests(total);
    }

    /**
     * 记录用户满意度
     */
    public void recordSatisfaction(String agentCode, double score) {
        AgentMetricsData metrics = metricsMap.get(agentCode);
        if (metrics != null) {
            long total = metrics.getTotalRequests();
            metrics.setSatisfactionScore(
                    (metrics.getSatisfactionScore() * Math.max(1, total - 1) + score)
                    / Math.max(1, total));
        }
    }

    public Map<String, AgentMetricsData> getAllMetrics() {
        return Map.copyOf(metricsMap);
    }

    public AgentMetricsData getMetrics(String agentCode) {
        return metricsMap.get(agentCode);
    }
}
