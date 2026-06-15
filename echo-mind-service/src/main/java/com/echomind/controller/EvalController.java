package com.echomind.controller;

import com.echomind.dto.EvalRequest;
import com.echomind.dto.Result;
import com.echomind.eval.EvalService;
import com.echomind.eval.EvalService.EvalReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 评测接口
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /**
     * 执行评测
     */
    @PostMapping("/run")
    public Result<Map<String, Object>> runEval(@RequestBody EvalRequest request) {
        log.info("收到评测请求: dataset={}, sampleSize={}", request.getDataset(), request.getSampleSize());

        EvalReport report = evalService.runEval(request);

        Map<String, Object> result = new HashMap<>();
        result.put("overallScore", Math.round(report.getOverallScore() * 10) / 10.0);

        Map<String, Double> dimensionScores = new HashMap<>();
        dimensionScores.put("accuracy", Math.round(report.getAccuracyAvg() * 10) / 10.0);
        dimensionScores.put("relevance", Math.round(report.getRelevanceAvg() * 10) / 10.0);
        dimensionScores.put("completeness", Math.round(report.getCompletenessAvg() * 10) / 10.0);
        dimensionScores.put("friendliness", Math.round(report.getFriendlinessAvg() * 10) / 10.0);
        result.put("dimensionScores", dimensionScores);

        if (report.getRegression() != null) {
            Map<String, Object> regression = new HashMap<>();
            regression.put("hasRegression", report.getRegression().hasRegression());
            regression.put("message", report.getRegression().message());
            result.put("regression", regression);
        }

        result.put("sampleCount", report.getSampleCount());
        result.put("batchId", report.getBatchId());

        return Result.ok(result);
    }

    /**
     * 获取评测报告
     */
    @GetMapping("/report/{batchId}")
    public Result<EvalReport> getReport(@PathVariable String batchId) {
        EvalReport report = evalService.getReport(batchId);
        if (report == null) {
            return Result.fail("评测报告不存在: " + batchId);
        }
        return Result.ok(report);
    }
}
