package com.echomind.eval;

import com.echomind.dto.EvalRequest;
import com.echomind.entity.EvalRecord;
import com.echomind.mapper.EvalRecordMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 端到端评测服务入口
 * LLM-as-Judge 四维度打分 → 聚合报告 → 回归检测
 */
@Slf4j
@Service
public class EvalService {

    private final LlmJudge llmJudge;
    private final RegressionDetector regressionDetector;
    private final EvalRecordMapper evalRecordMapper;
    private final Map<String, EvalReport> reportHistory = new LinkedHashMap<>();

    public EvalService(LlmJudge llmJudge,
                       RegressionDetector regressionDetector,
                       EvalRecordMapper evalRecordMapper) {
        this.llmJudge = llmJudge;
        this.regressionDetector = regressionDetector;
        this.evalRecordMapper = evalRecordMapper;
    }

    /**
     * 执行评测
     */
    public EvalReport runEval(EvalRequest request) {
        String batchId = "eval-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        log.info("开始评测: batch={}, dataset={}, sampleSize={}", batchId, request.getDataset(), request.getSampleSize());

        // 1. 准备评测样本（模拟数据）
        List<EvalSample> samples = prepareSamples(request);

        // 2. 逐条评测
        List<EvalResult> results = new ArrayList<>();
        for (EvalSample sample : samples) {
            LlmJudge.JudgeResult judgeResult = llmJudge.evaluate(sample.question, sample.answer, sample.reference);

            EvalResult result = new EvalResult();
            result.setQuestion(sample.question);
            result.setAnswer(sample.answer);
            result.setReference(sample.reference);
            result.setAccuracyScore(judgeResult.accuracy());
            result.setRelevanceScore(judgeResult.relevance());
            result.setCompletenessScore(judgeResult.completeness());
            result.setFriendlinessScore(judgeResult.friendliness());
            result.setOverallScore(judgeResult.overall());
            result.setJudgeReason(judgeResult.reason());
            results.add(result);

            // 持久化
            saveEvalRecord(batchId, sample, judgeResult);
        }

        // 3. 生成聚合报告
        EvalReport report = generateReport(batchId, results, request.getDimensions());

        // 4. 回归检测
        String lastBatch = getLastBatch();
        if (lastBatch != null && !reportHistory.isEmpty()) {
            EvalReport baseline = reportHistory.get(lastBatch);
            if (baseline != null) {
                RegressionDetector.RegressionResult regression = regressionDetector.detect(report, baseline);
                report.setRegression(regression);
            }
        }

        reportHistory.put(batchId, report);
        log.info("评测完成: batch={}, overall={}", batchId, report.getOverallScore());

        return report;
    }

    private List<EvalSample> prepareSamples(EvalRequest request) {
        // 模拟评测样本
        return List.of(
                new EvalSample("帮我推荐一家评分高的川菜馆",
                        "为您推荐以下评分较高的川菜馆：\n1. 川味轩 (评分 4.8) 地址：朝阳区建国路88号\n2. 蜀香园 (评分 4.7) 地址：海淀区中关村大街1号\n3. 巴蜀风 (评分 4.6) 地址：西城区金融街15号",
                        "川味轩评分4.8，位于朝阳区；蜀香园评分4.7，位于海淀区；巴蜀风评分4.6，位于西城区"),
                new EvalSample("这个店有优惠券吗？",
                        "川味轩目前有以下可用优惠券：\n1. 满100减20（满100元可用，有效期至2024-12-31）\n2. 8折优惠券（全场通用，有效期至2024-12-31）",
                        "川味轩有满100减20和8折两种优惠券"),
                new EvalSample("秒杀活动什么时候开始？",
                        "目前平台有以下秒杀活动：\n1. 每日10点场：每天上午10:00开始\n2. 每日20点场：每天晚上20:00开始\n3. 周末特惠场：每周六日上午10:00开始\n请关注活动页面获取最新信息。",
                        "每日秒杀活动：10:00和20:00两场；周末特惠场周六日10:00开始")
        );
    }

    private EvalReport generateReport(String batchId, List<EvalResult> results, String[] dimensions) {
        DoubleSummaryStatistics accuracyStats = results.stream().mapToDouble(EvalResult::getAccuracyScore).summaryStatistics();
        DoubleSummaryStatistics relevanceStats = results.stream().mapToDouble(EvalResult::getRelevanceScore).summaryStatistics();
        DoubleSummaryStatistics completenessStats = results.stream().mapToDouble(EvalResult::getCompletenessScore).summaryStatistics();
        DoubleSummaryStatistics friendlinessStats = results.stream().mapToDouble(EvalResult::getFriendlinessScore).summaryStatistics();
        DoubleSummaryStatistics overallStats = results.stream().mapToDouble(EvalResult::getOverallScore).summaryStatistics();

        EvalReport report = new EvalReport();
        report.setBatchId(batchId);
        report.setOverallScore(overallStats.getAverage());
        report.setAccuracyAvg(accuracyStats.getAverage());
        report.setRelevanceAvg(relevanceStats.getAverage());
        report.setCompletenessAvg(completenessStats.getAverage());
        report.setFriendlinessAvg(friendlinessStats.getAverage());
        report.setSampleCount(results.size());
        report.setResults(results);

        return report;
    }

    private void saveEvalRecord(String batchId, EvalSample sample, LlmJudge.JudgeResult judgeResult) {
        try {
            EvalRecord record = new EvalRecord();
            record.setEvalBatch(batchId);
            record.setQuestion(sample.question());
            record.setAnswer(sample.answer());
            record.setReference(sample.reference());
            record.setAccuracyScore((double) Math.round(judgeResult.accuracy() * 100) / 100);
            record.setRelevanceScore((double) Math.round(judgeResult.relevance() * 100) / 100);
            record.setCompletenessScore((double) Math.round(judgeResult.completeness() * 100) / 100);
            record.setFriendlinessScore((double) Math.round(judgeResult.friendliness() * 100) / 100);
            record.setOverallScore((double) Math.round(judgeResult.overall() * 100) / 100);
            record.setJudgeReason(judgeResult.reason());
            evalRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("评测记录持久化失败", e);
        }
    }

    private String getLastBatch() {
        List<String> batches = new ArrayList<>(reportHistory.keySet());
        return batches.isEmpty() ? null : batches.get(batches.size() - 1);
    }

    public EvalReport getReport(String batchId) {
        return reportHistory.get(batchId);
    }

    @Data
    public static class EvalReport {
        private String batchId;
        private double overallScore;
        private double accuracyAvg;
        private double relevanceAvg;
        private double completenessAvg;
        private double friendlinessAvg;
        private int sampleCount;
        private List<EvalResult> results;
        private RegressionDetector.RegressionResult regression;
    }

    @Data
    public static class EvalResult {
        private String question;
        private String answer;
        private String reference;
        private double accuracyScore;
        private double relevanceScore;
        private double completenessScore;
        private double friendlinessScore;
        private double overallScore;
        private String judgeReason;
    }

    private record EvalSample(String question, String answer, String reference) {}
}
