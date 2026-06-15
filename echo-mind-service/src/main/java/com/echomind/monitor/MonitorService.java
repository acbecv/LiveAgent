package com.echomind.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.echomind.entity.AgentMetrics;
import com.echomind.mapper.AgentMetricsMapper;
import com.echomind.agent.AgentRouter;
import com.echomind.agent.AgentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

/**
 * Monitor 闭环监控入口
 * 采集指标 → 分析评估 → 决策调整 → 反馈路由
 */
@Slf4j
@Service
public class MonitorService {

    private final MetricsCollector metricsCollector;
    private final DecisionEngine decisionEngine;
    private final WeightAdjuster weightAdjuster;
    private final AlertManager alertManager;
    private final AgentMetricsMapper agentMetricsMapper;
    private final AgentRegistry agentRegistry;

    public MonitorService(MetricsCollector metricsCollector,
                          DecisionEngine decisionEngine,
                          WeightAdjuster weightAdjuster,
                          AlertManager alertManager,
                          AgentMetricsMapper agentMetricsMapper,
                          AgentRegistry agentRegistry) {
        this.metricsCollector = metricsCollector;
        this.decisionEngine = decisionEngine;
        this.weightAdjuster = weightAdjuster;
        this.alertManager = alertManager;
        this.agentMetricsMapper = agentMetricsMapper;
        this.agentRegistry = agentRegistry;
    }

    @PostConstruct
    public void init() {
        log.info("Monitor 闭环监控服务已启动");
    }

    /**
     * 定时执行监控闭环 (每 10 分钟)
     */
    @Scheduled(fixedRateString = "${echomind.monitor.weight-adjust-interval-minutes:10}000")
    public void scheduledMonitorCycle() {
        log.info("开始监控闭环: 采集分析 → 决策调整");

        // 1. 分析评估
        Map<String, Double> adjustedWeights = decisionEngine.executeDecision();

        // 2. 检查告警
        metricsCollector.getAllMetrics().values().forEach(alertManager::checkAlerts);

        // 3. 持久化指标到数据库
        persistMetrics();

        log.info("监控闭环完成: 已调整 {} 个Agent权重", adjustedWeights.size());
    }

    /**
     * 记录一次 Agent 调用
     */
    public void recordCall(String agentCode, long latencyMs, int tokensUsed, boolean success) {
        metricsCollector.recordCall(agentCode, latencyMs, tokensUsed, success);
    }

    /**
     * 获取当前所有指标
     */
    public Map<String, AgentMetricsData> getAllMetrics() {
        return metricsCollector.getAllMetrics();
    }

    /**
     * 获取当前路由权重
     */
    public Map<String, Double> getCurrentWeights() {
        return weightAdjuster.getWeights();
    }

    private void persistMetrics() {
        try {
            Map<String, AgentMetricsData> allMetrics = metricsCollector.getAllMetrics();
            LocalDate today = LocalDate.now();
            int hour = LocalTime.now().getHour();

            for (Map.Entry<String, AgentMetricsData> entry : allMetrics.entrySet()) {
                AgentMetricsData m = entry.getValue();

                // 查找是否存在同 agent + 同日期 + 同小时的记录
                LambdaQueryWrapper<AgentMetrics> wrapper = new LambdaQueryWrapper<AgentMetrics>()
                        .eq(AgentMetrics::getAgentCode, m.getAgentCode())
                        .eq(AgentMetrics::getMetricDate, today)
                        .eq(AgentMetrics::getMetricHour, hour);
                AgentMetrics existing = agentMetricsMapper.selectOne(wrapper);

                AgentMetrics entity = new AgentMetrics();
                entity.setAgentCode(m.getAgentCode());
                entity.setMetricDate(today);
                entity.setMetricHour(hour);
                entity.setRequestCount((int) m.getTotalRequests());
                entity.setAvgLatencyMs(m.getAvgLatencyMs());
                entity.setP95LatencyMs(m.getP95LatencyMs());
                entity.setSatisfactionScore(m.getSatisfactionScore());
                entity.setErrorRate(m.getErrorRate());
                entity.setRoutingWeight(weightAdjuster.getWeight(m.getAgentCode()));
                entity.setTotalTokens(m.getTotalTokensUsed());

                if (existing != null) {
                    entity.setId(existing.getId());
                    agentMetricsMapper.updateById(entity);
                } else {
                    agentMetricsMapper.insert(entity);
                }
            }
            log.info("指标持久化完成: {} 条", allMetrics.size());
        } catch (Exception e) {
            log.error("指标持久化异常", e);
        }
    }
}
