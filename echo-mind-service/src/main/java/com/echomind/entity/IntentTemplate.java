package com.echomind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_intent_template")
public class IntentTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("intent_code")
    private String intentCode;
    @TableField("intent_name")
    private String intentName;
    private String description;
    private String keywords;  // JSON array
    private String examples;  // JSON array
    private Integer priority;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
