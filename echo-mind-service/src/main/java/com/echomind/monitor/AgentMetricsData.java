package com.echomind.monitor;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 指标实体
 * 采集响应延迟、满意度、解决率、Token消耗、错误率等
 */
@Data
public class AgentMetricsData {
    private String agentCode;

    // 性能指标
    private double avgLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;

    // 质量指标
    private double satisfactionScore = 0.5;
    private double resolutionRate = 0.5;
    private double escalationRate;

    // 成本指标
    private long totalTokensUsed;
    private double avgTokensPerRequest;

    // 稳定性指标
    private double errorRate;
    private double fallbackRate;

    // 请求量
    private long totalRequests;

    // 综合得分 (用于路由权重计算)
    public double computeCompositeScore() {
        return 0.35 * satisfactionScore
             + 0.25 * resolutionRate
             + 0.20 * (1 - Math.min(normalizeLatency(avgLatencyMs), 1.0))
             + 0.10 * (1 - errorRate)
             + 0.10 * (1 - Math.min(normalizeCost(avgTokensPerRequest), 1.0));
    }

    private double normalizeLatency(double latency) {
        return latency / 5000.0; // 假设 5000ms 为最大阈值
    }

    private double normalizeCost(double cost) {
        return cost / 2000.0; // 假设 2000 tokens 为最大阈值
    }
}
