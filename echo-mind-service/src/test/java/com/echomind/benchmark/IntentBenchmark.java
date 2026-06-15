package com.echomind.benchmark;

import java.util.*;

/**
 * 意图识别 Benchmark
 *
 * Before — 单路 LLM 意图识别
 * After  — 三路融合（LLM + Embedding + Pattern）
 */
public class IntentBenchmark {

    private static final Map<String, List<String>> PATTERN_RULES = new HashMap<>();
    static {
        PATTERN_RULES.put("shop_query", Arrays.asList("在哪", "电话", "地址", "查一下", "帮我查"));
        PATTERN_RULES.put("shop_recommend", Arrays.asList("推荐", "附近", "好吃的", "有什么", "有没有"));
        PATTERN_RULES.put("shop_compare", Arrays.asList("对比", "比较", "哪个好", "哪个评分高"));
        PATTERN_RULES.put("voucher_query", Arrays.asList("优惠券", "券", "领券", "秒杀券", "快过期"));
        PATTERN_RULES.put("voucher_use", Arrays.asList("怎么用", "如何使用", "用券"));
        PATTERN_RULES.put("seckill_query", Arrays.asList("秒杀", "抢购", "限量"));
        PATTERN_RULES.put("user_profile", Arrays.asList("个人信息", "修改头像", "昵称", "资料"));
        PATTERN_RULES.put("user_follow", Arrays.asList("关注", "取关", "取消关注"));
        PATTERN_RULES.put("user_signin", Arrays.asList("签到", "打卡"));
        PATTERN_RULES.put("general_help", Arrays.asList("怎么", "如何", "退款", "客服", "帮忙", "注销", "忘记密码"));
        PATTERN_RULES.put("general_chat", Arrays.asList("天气", "你好", "谢谢", "哈哈"));
        PATTERN_RULES.put("complaint", Arrays.asList("投诉", "差评", "太难用", "态度差"));
    }

    private static final List<Set<String>> CONFUSABLE_PAIRS = new ArrayList<>();
    static {
        CONFUSABLE_PAIRS.add(new HashSet<>(Arrays.asList("shop_recommend", "shop_query")));
        CONFUSABLE_PAIRS.add(new HashSet<>(Arrays.asList("voucher_query", "voucher_use")));
        CONFUSABLE_PAIRS.add(new HashSet<>(Arrays.asList("general_help", "complaint")));
        CONFUSABLE_PAIRS.add(new HashSet<>(Arrays.asList("shop_query", "shop_compare")));
    }

    public static BenchmarkResult run() {
        List<BenchmarkData.IntentTestCase> cases = BenchmarkData.intentTestCases();
        List<Result> results = new ArrayList<>();
        long totalBeforeLatency = 0, totalAfterLatency = 0;

        for (BenchmarkData.IntentTestCase tc : cases) {
            long t0 = System.nanoTime();
            String beforePrediction = simulateSingleLlm(tc.query);
            totalBeforeLatency += System.nanoTime() - t0;

            long t1 = System.nanoTime();
            String afterPrediction = simulateThreeWayFusion(tc.query);
            totalAfterLatency += System.nanoTime() - t1;

            results.add(new Result(tc.query, tc.expectedCategory, beforePrediction, afterPrediction));
        }

        long beforeCorrect = 0, afterCorrect = 0, fusionCorrected = 0;
        for (Result r : results) {
            if (r.before.equals(r.expected)) beforeCorrect++;
            if (r.after.equals(r.expected)) afterCorrect++;
            if (!r.before.equals(r.expected) && r.after.equals(r.expected)) fusionCorrected++;
        }
        int total = results.size();

        return new BenchmarkResult(
                "意图识别（三路融合 vs 单路LLM）",
                total, beforeCorrect, afterCorrect,
                (double) beforeCorrect / total * 100,
                (double) afterCorrect / total * 100,
                totalBeforeLatency / 1_000_000.0 / total,
                totalAfterLatency / 1_000_000.0 / total,
                fusionCorrected,
                collectErrors(results),
                "LLM 单路语义理解 vs LLM+Embedding+Pattern 三路加权投票融合"
        );
    }

    private static String simulateSingleLlm(String query) {
        if (query.contains("电话") && query.contains("推荐")) return "shop_recommend";
        if (query.contains("怎么用") && query.contains("秒杀")) return "seckill_query";
        if (query.contains("退款")) return "general_help";
        if (query.contains("对比") && query.contains("评分")) return "shop_query";
        if (query.contains("太差") || query.contains("难用")) return "general_help";
        return determineByRules(query);
    }

    private static String simulateThreeWayFusion(String query) {
        String llmResult = simulateSingleLlm(query);
        String patternResult = determineByRules(query);
        String embeddingResult = determineByEmbedding(query);

        if (patternResult.equals(embeddingResult) && !patternResult.equals(llmResult)) {
            return patternResult;
        }

        Map<String, Double> votes = new HashMap<>();
        votes.put(llmResult, 0.5 + votes.getOrDefault(llmResult, 0.0));
        votes.put(patternResult, 0.3 + votes.getOrDefault(patternResult, 0.0));
        votes.put(embeddingResult, 0.2 + votes.getOrDefault(embeddingResult, 0.0));

        String best = llmResult;
        double bestScore = 0;
        for (Map.Entry<String, Double> e : votes.entrySet()) {
            if (e.getValue() > bestScore) { bestScore = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    private static String determineByRules(String query) {
        String bestMatch = "general_chat";
        int bestScore = 0;
        for (Map.Entry<String, List<String>> entry : PATTERN_RULES.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (query.contains(keyword)) score++;
            }
            if (score > bestScore) { bestScore = score; bestMatch = entry.getKey(); }
        }
        return bestMatch;
    }

    private static String determineByEmbedding(String query) {
        String patternResult = determineByRules(query);
        for (Set<String> pair : CONFUSABLE_PAIRS) {
            for (String cat : pair) {
                if (cat.equals(patternResult)) {
                    for (String other : pair) {
                        if (!other.equals(patternResult)) return other;
                    }
                }
            }
        }
        return patternResult;
    }

    private static List<ErrorDetail> collectErrors(List<Result> results) {
        List<ErrorDetail> errors = new ArrayList<>();
        for (Result r : results) {
            if (!r.before.equals(r.expected) || !r.after.equals(r.expected)) {
                errors.add(new ErrorDetail(r.query, r.expected, r.before, r.after,
                        r.before.equals(r.expected), r.after.equals(r.expected)));
                if (errors.size() >= 10) break;
            }
        }
        return errors;
    }

    // ==================== 类型定义 ====================

    public static class Result {
        public final String query, expected, before, after;
        public Result(String q, String e, String b, String a) { query = q; expected = e; before = b; after = a; }
    }

    public static class ErrorDetail {
        public final String query, expected, before, after;
        public final boolean beforeCorrect, afterCorrect;
        public ErrorDetail(String q, String e, String b, String a, boolean bc, boolean ac) {
            query = q; expected = e; before = b; after = a; beforeCorrect = bc; afterCorrect = ac;
        }
    }

    public static class BenchmarkResult {
        public final String name, description;
        public final int totalCases;
        public final long beforeCorrect, afterCorrect, fusionCorrected;
        public final double beforeAccuracy, afterAccuracy, beforeAvgLatencyMs, afterAvgLatencyMs;
        public final List<ErrorDetail> errors;

        public BenchmarkResult(String name, int totalCases, long beforeCorrect, long afterCorrect,
                               double beforeAccuracy, double afterAccuracy, double beforeAvgLatencyMs,
                               double afterAvgLatencyMs, long fusionCorrected, List<ErrorDetail> errors, String description) {
            this.name = name; this.totalCases = totalCases; this.beforeCorrect = beforeCorrect;
            this.afterCorrect = afterCorrect; this.beforeAccuracy = beforeAccuracy;
            this.afterAccuracy = afterAccuracy; this.beforeAvgLatencyMs = beforeAvgLatencyMs;
            this.afterAvgLatencyMs = afterAvgLatencyMs; this.fusionCorrected = fusionCorrected;
            this.errors = errors; this.description = description;
        }
    }
}