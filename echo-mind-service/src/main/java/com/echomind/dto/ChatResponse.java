package com.echomind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private Integer code;
    private String message;
    private ChatData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatData {
        private String answer;
        private String intent;
        private Double intentConfidence;
        private String agentUsed;
        private String sessionId;
        private List<Source> sources;
        private Metrics metrics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String id;
        private String name;
        private Double relevance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        private Long latencyMs;
        private Integer tokensUsed;
    }

    public static ChatResponse success(ChatData data) {
        return ChatResponse.builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    public static ChatResponse fail(String msg) {
        return ChatResponse.builder()
                .code(500)
                .message(msg)
                .build();
    }
}
