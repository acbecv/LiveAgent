package com.echomind.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 权重动态调整器
 * 根据在线表现计算并调整各 Agent 的路由权重
 */
@Slf4j
@Component
public class WeightAdjuster {

    private final Map<String, Double> currentWeights = new HashMap<>();
    private final MetricsCollector metricsCollector;

    public WeightAdjuster(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        // 初始化默认权重
        currentWeights.put("general", 0.35);
        currentWeights.put("voucher", 0.20);
        currentWeights.put("seckill", 0.10);
        currentWeights.put("user", 0.15);
        currentWeights.put("technical", 0.10);
        currentWeights.put("complaint", 0.10);
    }

    /**
     * 根据综合得分调整权重
     */
    public Map<String, Double> adjustWeights() {
        Map<String, AgentMetricsData> allMetrics = metricsCollector.getAllMetrics();

        for (Map.Entry<String, AgentMetricsData> entry : allMetrics.entrySet()) {
            String agentCode = entry.getKey();
            AgentMetricsData metrics = entry.getValue();

            double compositeScore = metrics.computeCompositeScore();
            double oldWeight = currentWeights.getOrDefault(agentCode, 0.1);
            double newWeight = oldWeight * 0.7 + compositeScore * 0.3;

            currentWeights.put(agentCode, Math.max(0.05, Math.min(0.5, newWeight)));
            log.info("权重调整: agent={}, composite={}, weight: {} -> {}",
                    agentCode, String.format("%.3f", compositeScore),
                    String.format("%.3f", oldWeight), String.format("%.3f", currentWeights.get(agentCode)));
        }

        // 归一化
        normalizeWeights();

        return getWeights();
    }

    private void normalizeWeights() {
        double total = currentWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) {
            currentWeights.replaceAll((k, v) -> v / total);
        }
    }

    public double getWeight(String agentCode) {
        return currentWeights.getOrDefault(agentCode, 0.1);
    }

    public Map<String, Double> getWeights() {
        return Map.copyOf(currentWeights);
    }
}
