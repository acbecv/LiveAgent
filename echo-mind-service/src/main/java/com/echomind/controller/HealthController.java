package com.echomind.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查
 */
@RestController
public class HealthController {

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        System.out.println("Health check at " + System.currentTimeMillis());
        return Map.of(
                "status", "UP",
                "service", "echo-mind-service",
                "timestamp", System.currentTimeMillis()
        );
    }
}
