package com.echomind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("tb_agent_metrics")
public class AgentMetrics {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("agent_code")
    private String agentCode;
    @TableField("metric_date")
    private LocalDate metricDate;
    @TableField("metric_hour")
    private Integer metricHour;
    private Integer requestCount;
    @TableField("avg_latency_ms")
    private Double avgLatencyMs;
    @TableField("p95_latency_ms")
    private Double p95LatencyMs;
    private Double satisfactionScore;
    private Double resolutionRate;
    private Double errorRate;
    private Double routingWeight;
    private Long totalTokens;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
