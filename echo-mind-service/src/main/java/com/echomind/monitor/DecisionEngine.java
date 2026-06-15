package com.echomind.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 决策引擎
 * 分析指标数据，给出权重调整建议
 */
@Slf4j
@Component
public class DecisionEngine {

    private final MetricsCollector metricsCollector;
    private final WeightAdjuster weightAdjuster;

    public DecisionEngine(MetricsCollector metricsCollector, WeightAdjuster weightAdjuster) {
        this.metricsCollector = metricsCollector;
        this.weightAdjuster = weightAdjuster;
    }

    /**
     * 执行一次决策周期：分析指标 → 调整权重
     */
    public Map<String, Double> executeDecision() {
        log.info("决策引擎: 开始分析指标...");

        Map<String, AgentMetricsData> metrics = metricsCollector.getAllMetrics();

        for (Map.Entry<String, AgentMetricsData> entry : metrics.entrySet()) {
            AgentMetricsData m = entry.getValue();
            log.debug("Agent分析: {} | 延迟={}ms | 满意度={} | 解决率={} | 错误率={}",
                    entry.getKey(),
                    String.format("%.0f", m.getAvgLatencyMs()),
                    String.format("%.2f", m.getSatisfactionScore()),
                    String.format("%.2f", m.getResolutionRate()),
                    String.format("%.2f", m.getErrorRate()));
        }

        return weightAdjuster.adjustWeights();
    }
}
