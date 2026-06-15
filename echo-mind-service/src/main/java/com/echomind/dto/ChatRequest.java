package com.echomind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private Long userId;
    private String message;
    private String sessionId;
    private Boolean stream = false;
}
