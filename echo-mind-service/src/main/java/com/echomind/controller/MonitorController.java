package com.echomind.controller;

import com.echomind.dto.Result;
import com.echomind.monitor.MonitorService;
import com.echomind.monitor.AgentMetricsData;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控接口
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * 获取所有 Agent 指标
     */
    @GetMapping("/metrics")
    public Result<Map<String, Object>> getMetrics() {
        Map<String, AgentMetricsData> allMetrics = monitorService.getAllMetrics();
        Map<String, Double> allWeights = monitorService.getCurrentWeights();

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> agents = new HashMap<>();

        for (Map.Entry<String, AgentMetricsData> entry : allMetrics.entrySet()) {
            AgentMetricsData m = entry.getValue();
            Map<String, Object> agentData = new HashMap<>();
            agentData.put("avgLatencyMs", m.getAvgLatencyMs());
            agentData.put("p95LatencyMs", m.getP95LatencyMs());
            agentData.put("satisfactionScore", m.getSatisfactionScore());
            agentData.put("resolutionRate", m.getResolutionRate());
            agentData.put("errorRate", m.getErrorRate());
            agentData.put("totalRequests", m.getTotalRequests());
            agentData.put("routingWeight", allWeights.getOrDefault(entry.getKey(), 0.1));
            agents.put(entry.getKey(), agentData);
        }

        result.put("agents", agents);

        Map<String, Object> systemMetrics = new HashMap<>();
        systemMetrics.put("totalRequests", allMetrics.values().stream()
                .mapToLong(AgentMetricsData::getTotalRequests).sum());
        systemMetrics.put("overallSatisfaction", allMetrics.values().stream()
                .mapToDouble(AgentMetricsData::getSatisfactionScore).average().orElse(0));
        result.put("system", systemMetrics);
        result.put("weights", allWeights);

        return Result.ok(result);
    }

    /**
     * 主动触发一次监控闭环
     */
    @PostMapping("/cycle")
    public Result<String> triggerCycle() {
        Map<String, Double> weights = monitorService.getCurrentWeights();
        return Result.ok("监控闭环完成，当前权重: " + weights);
    }
}
