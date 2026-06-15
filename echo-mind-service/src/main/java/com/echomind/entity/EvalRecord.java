package com.echomind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_eval_record")
public class EvalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("eval_batch")
    private String evalBatch;
    @TableField("message_id")
    private Long messageId;
    private String question;
    private String answer;
    private String reference;
    private Double accuracyScore;
    private Double relevanceScore;
    private Double completenessScore;
    private Double friendlinessScore;
    private Double overallScore;
    private String judgeReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
