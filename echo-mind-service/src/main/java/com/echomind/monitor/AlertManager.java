package com.echomind.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 告警管理器
 * 在指标异常时发送告警
 */
@Slf4j
@Component
public class AlertManager {

    private static final double LATENCY_THRESHOLD_MS = 3000;
    private static final double ERROR_RATE_THRESHOLD = 0.1;
    private static final double SATISFACTION_THRESHOLD = 0.3;

    /**
     * 检查指标并触发告警
     */
    public void checkAlerts(AgentMetricsData metrics) {
        if (metrics == null) return;

        boolean alert = false;
        StringBuilder message = new StringBuilder("[EchoMind告警] Agent=" + metrics.getAgentCode());

        if (metrics.getAvgLatencyMs() > LATENCY_THRESHOLD_MS) {
            message.append(String.format(" | 延迟过高: %.0fms(阈值:%.0fms)",
                    metrics.getAvgLatencyMs(), LATENCY_THRESHOLD_MS));
            alert = true;
        }

        if (metrics.getErrorRate() > ERROR_RATE_THRESHOLD) {
            message.append(String.format(" | 错误率过高: %.2f(阈值:%.2f)",
                    metrics.getErrorRate(), ERROR_RATE_THRESHOLD));
            alert = true;
        }

        if (metrics.getSatisfactionScore() < SATISFACTION_THRESHOLD) {
            message.append(String.format(" | 满意度过低: %.2f(阈值:%.2f)",
                    metrics.getSatisfactionScore(), SATISFACTION_THRESHOLD));
            alert = true;
        }

        if (alert) {
            log.warn(message.toString());
        }
    }
}
