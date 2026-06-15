package com.echomind.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "echomind.monitor")
public class MonitorConfig {
    private int metricsWindowMinutes = 60;
    private int weightAdjustIntervalMinutes = 10;
}
