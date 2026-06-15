package com.echomind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private String sessionId;
    private String role;  // user / assistant / system
    private String content;
    private String intent;
    private Double intentConfidence;
    private String agentUsed;
    private Integer tokensUsed;
    @TableField("latency_ms")
    private Integer latencyMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
