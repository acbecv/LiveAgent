package com.echomind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {
    private String category;     // 意图类别编码
    private String categoryName; // 意图类别名称
    private Double confidence;   // 置信度 0-1
    private String source;       // llm / embedding / pattern / fused
}
