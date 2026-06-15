package com.echomind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "echomind.intent")
public class IntentWeightsConfig {
    private Weights weights = new Weights();
    private Threshold confidenceThreshold = new Threshold();

    @Data
    public static class Weights {
        private double llm = 0.5;
        private double embedding = 0.3;
        private double pattern = 0.2;
    }

    @Data
    public static class Threshold {
        private double high = 0.8;
        private double medium = 0.6;
    }
}
