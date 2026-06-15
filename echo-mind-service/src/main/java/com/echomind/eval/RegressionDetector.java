package com.echomind.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 回归检测器
 * 对比当前评测结果和基线，检测质量是否回退
 */
@Slf4j
@Component
public class RegressionDetector {

    private static final double REGRESSION_THRESHOLD = -0.3;

    /**
     * 检测是否有回归
     */
    public RegressionResult detect(EvalService.EvalReport currentReport,
                                   EvalService.EvalReport baselineReport) {
        if (baselineReport == null) {
            return new RegressionResult(false, "无基线数据");
        }

        double accuracyDelta = currentReport.getAccuracyAvg() - baselineReport.getAccuracyAvg();
        double relevanceDelta = currentReport.getRelevanceAvg() - baselineReport.getRelevanceAvg();

        boolean hasRegression = accuracyDelta < REGRESSION_THRESHOLD
                || relevanceDelta < REGRESSION_THRESHOLD;

        StringBuilder message = new StringBuilder();
        message.append("准确性变化: ").append(String.format("%+.2f", accuracyDelta));
        message.append(" | 相关性变化: ").append(String.format("%+.2f", relevanceDelta));

        if (hasRegression) {
            message.append(" | ⚠️ 检测到回归！");
        }

        return new RegressionResult(hasRegression, message.toString());
    }

    public record RegressionResult(boolean hasRegression, String message) {}
}
