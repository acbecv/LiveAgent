package com.echomind.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 准确性评测器
 */
@Slf4j
@Component
public class AccuracyEvaluator {
    public double evaluate(String question, String answer, String reference) {
        // 实际应使用 LLM Judge，当前为占位实现
        return 4.0 + new Random().nextDouble();
    }
}
