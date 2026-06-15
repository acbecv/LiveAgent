package com.echomind.eval;

import com.echomind.eval.EvalService.EvalReport;
import com.echomind.eval.EvalService.EvalResult;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 评测报告生成器
 */
@Component
public class EvalReportGenerator {
    public String generateHtml(EvalReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>EchoMind 评测报告</h2>");
        html.append("<p>批次: ").append(report.getBatchId()).append("</p>");
        html.append("<p>综合评分: ").append(String.format("%.2f", report.getOverallScore())).append("</p>");
        html.append("<p>评价样本数: ").append(report.getSampleCount()).append("</p>");
        html.append("</body></html>");
        return html.toString();
    }
}
