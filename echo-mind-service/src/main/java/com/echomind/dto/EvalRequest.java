package com.echomind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalRequest {
    private String dataset;
    private Integer sampleSize;
    private String[] dimensions; // accuracy, relevance, completeness, friendliness
}
