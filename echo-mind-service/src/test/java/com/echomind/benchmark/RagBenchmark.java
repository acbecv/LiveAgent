package com.echomind.benchmark;

import java.util.*;

/**
 * RAG 检索 Benchmark
 */
public class RagBenchmark {

    private static final Map<String, String> KNOWLEDGE_BASE = new LinkedHashMap<>();
    static {
        KNOWLEDGE_BASE.put("platform_intro", "本地生活服务平台是一个专注于本地商户发现、优惠券领取和用户社交的综合平台。");
        KNOWLEDGE_BASE.put("refund_policy", "已支付但未使用的优惠券，用户可在有效期内申请退款。退款审核通常需要1-3个工作日。");
        KNOWLEDGE_BASE.put("voucher_usage", "优惠券领取后可在对应商户消费时抵扣。优惠券不可叠加使用，不可转让，不可兑现。");
        KNOWLEDGE_BASE.put("seckill_rule", "秒杀优惠券库存有限，先到先得。秒杀成功后优惠券会立即发放到用户账户。");
        KNOWLEDGE_BASE.put("login_help", "用户可通过手机号注册登录。忘记密码可通过绑定的手机号重置。支持微信一键登录。");
        KNOWLEDGE_BASE.put("user_info_query", "请前往个人中心查看用户信息。可查看和编辑头像、昵称、性别、生日、手机号等。");
        KNOWLEDGE_BASE.put("privacy_policy", "平台严格保护用户隐私：手机号等敏感信息加密存储；不会向第三方泄露。");
        KNOWLEDGE_BASE.put("general_help", "联系客服：在线客服（App内）或客服电话400-xxx-xxxx。");
        KNOWLEDGE_BASE.put("merchant_join", "商户入驻需提供营业执照、法人身份证、门店照片等资料。");
        KNOWLEDGE_BASE.put("voucher_types", "优惠券类型：满减券、折扣券、免单券、秒杀券、新人专享券。");
        KNOWLEDGE_BASE.put("review_rules", "用户可在消费后对商户评价，包括评分1-5星和文字评论。评价需真实、客观。");
        KNOWLEDGE_BASE.put("social_features", "平台支持社交互动：关注好友、查看动态、点赞和评论好友分享内容。");
        KNOWLEDGE_BASE.put("point_system", "用户通过签到、评价、分享获得积分。积分可兑换优惠券或参与抽奖。");
        KNOWLEDGE_BASE.put("notification_settings", "用户可在设置中管理消息通知偏好：优惠券到期提醒、秒杀通知等。");
        KNOWLEDGE_BASE.put("security_tips", "安全提示：勿将账号密码告知他人，不要在非官方渠道下载APP。");
    }

    public static BenchmarkResult run() {
        List<BenchmarkData.RagTestCase> cases = BenchmarkData.ragTestCases();
        List<Result> results = new ArrayList<>();

        for (BenchmarkData.RagTestCase tc : cases) {
            List<String> beforeDocs = simulateSingleVector(tc.query);
            List<String> afterDocs = simulateMultiPathRerank(tc.query);
            results.add(new Result(tc.query, tc.description, tc.expectedDocIds, beforeDocs, afterDocs));
        }

        double beforeRecall = avgRecall(results, true);
        double afterRecall = avgRecall(results, false);
        double beforePrecision = avgPrecision(results, true);
        double afterPrecision = avgPrecision(results, false);
        double beforeMRR = mrr(results, true);
        double afterMRR = mrr(results, false);
        double recallImprovement = (afterRecall - beforeRecall) / Math.max(beforeRecall, 0.01) * 100;

        return new BenchmarkResult("RAG 检索", results, beforeRecall, afterRecall,
                beforePrecision, afterPrecision, beforeMRR, afterMRR, recallImprovement,
                "单向量检索 → 查询改写+向量+BM25双路 → RRF融合 → LLM重排");
    }

    private static List<String> simulateSingleVector(String query) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : KNOWLEDGE_BASE.entrySet()) {
            double sim = semanticSimilarity(query, entry.getValue());
            if (sim > 0.15) scores.put(entry.getKey(), sim);
        }
        return sortAndLimit(scores, 5);
    }

    private static List<String> simulateMultiPathRerank(String query) {
        List<String> expandedQueries = expandQuery(query);
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        for (String q : expandedQueries) {
            addToRrf(rrfScores, vectorSearch(q), 60.0);
            addToRrf(rrfScores, bm25Search(q), 60.0);
        }
        List<String> candidates = sortAndLimit(rrfScores, 10);
        return llmRerank(query, candidates);
    }

    private static List<String> expandQuery(String query) {
        List<String> expanded = new ArrayList<>();
        expanded.add(query);
        Map<String, List<String>> synonymMap = new HashMap<>();
        synonymMap.put("退货", Arrays.asList("退款", "售后", "退钱"));
        synonymMap.put("售后", Arrays.asList("退货", "退款", "退换"));
        synonymMap.put("优惠券", Arrays.asList("券", "折扣券", "满减券"));
        synonymMap.put("怎么用", Arrays.asList("如何使用", "使用方法", "使用说明"));
        synonymMap.put("秒杀", Arrays.asList("抢购", "限时抢", "限量"));
        for (Map.Entry<String, List<String>> e : synonymMap.entrySet()) {
            if (query.contains(e.getKey())) {
                for (String syn : e.getValue()) {
                    expanded.add(query.replace(e.getKey(), syn));
                }
                break;
            }
        }
        return expanded;
    }

    private static Map<String, Double> vectorSearch(String query) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : KNOWLEDGE_BASE.entrySet()) {
            double sim = semanticSimilarity(query, entry.getValue());
            if (sim > 0.15) scores.put(entry.getKey(), sim);
        }
        return scores;
    }

    private static Map<String, Double> bm25Search(String query) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : KNOWLEDGE_BASE.entrySet()) {
            double score = 0;
            String doc = entry.getValue();
            for (int i = 0; i < query.length() - 1; i++) {
                if (doc.contains(query.substring(i, i + 2))) score += 0.1;
            }
            if (score > 0) scores.put(entry.getKey(), score);
        }
        return scores;
    }

    private static void addToRrf(Map<String, Double> rrfScores, Map<String, Double> pathScores, double k) {
        List<Map.Entry<String, Double>> sorted = sortEntries(pathScores);
        for (int i = 0; i < sorted.size(); i++) {
            rrfScores.put(sorted.get(i).getKey(),
                    rrfScores.getOrDefault(sorted.get(i).getKey(), 0.0) + 1.0 / (k + i + 1));
        }
    }

    private static List<String> llmRerank(String query, List<String> candidates) {
        if (candidates.size() <= 5) return candidates;
        Map<String, Double> rerankScores = new LinkedHashMap<>();
        for (String docId : candidates) {
            String content = KNOWLEDGE_BASE.get(docId);
            if (content != null) rerankScores.put(docId, semanticSimilarity(query, content) * 1.5);
        }
        return sortAndLimit(rerankScores, 5);
    }

    private static double semanticSimilarity(String query, String doc) {
        double score = 0;
        Map<String, List<String>> semanticMap = new HashMap<>();
        semanticMap.put("退款", Arrays.asList("退款", "退费", "退钱", "返回"));
        semanticMap.put("退货", Arrays.asList("退货", "退款", "售后", "退钱"));
        semanticMap.put("售后", Arrays.asList("售后", "退货", "退款"));
        semanticMap.put("优惠券", Arrays.asList("优惠券", "券", "折扣", "满减", "抵扣"));
        semanticMap.put("秒杀", Arrays.asList("秒杀", "抢购", "限量", "限时"));
        semanticMap.put("登录", Arrays.asList("登录", "注册", "手机号", "验证码", "密码"));
        semanticMap.put("隐私", Arrays.asList("隐私", "保密", "个人信息", "加密"));
        semanticMap.put("客服", Arrays.asList("客服", "联系", "电话", "帮助"));
        for (Map.Entry<String, List<String>> e : semanticMap.entrySet()) {
            for (String kw : e.getValue()) {
                if (query.contains(kw) && doc.contains(kw)) score += 0.3;
            }
        }
        for (int i = 0; i < query.length() - 1; i++) {
            if (doc.contains(query.substring(i, i + 2))) score += 0.05;
        }
        return Math.min(score, 1.0);
    }

    private static List<String> sortAndLimit(Map<String, Double> scores, int limit) {
        List<Map.Entry<String, Double>> sorted = sortEntries(scores);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            result.add(sorted.get(i).getKey());
        }
        return result;
    }

    private static List<Map.Entry<String, Double>> sortEntries(Map<String, Double> scores) {
        List<Map.Entry<String, Double>> list = new ArrayList<>(scores.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });
        return list;
    }

    private static double avgRecall(List<Result> results, boolean isBefore) {
        double sum = 0;
        for (Result r : results) sum += recall(isBefore ? r.beforeDocs : r.afterDocs, r.expected);
        return sum / results.size();
    }

    private static double avgPrecision(List<Result> results, boolean isBefore) {
        double sum = 0;
        for (Result r : results) sum += precision(isBefore ? r.beforeDocs : r.afterDocs, r.expected);
        return sum / results.size();
    }

    private static double mrr(List<Result> results, boolean isBefore) {
        double sum = 0;
        for (Result r : results) {
            List<String> docs = isBefore ? r.beforeDocs : r.afterDocs;
            for (int i = 0; i < docs.size(); i++) {
                if (r.expected.contains(docs.get(i))) { sum += 1.0 / (i + 1); break; }
            }
        }
        return sum / results.size();
    }

    private static double recall(List<String> retrieved, List<String> expected) {
        if (expected.isEmpty()) return 1.0;
        long hit = 0;
        for (String e : expected) if (retrieved.contains(e)) hit++;
        return (double) hit / expected.size();
    }

    private static double precision(List<String> retrieved, List<String> expected) {
        if (retrieved.isEmpty()) return 0;
        long hit = 0;
        for (String r : retrieved) if (expected.contains(r)) hit++;
        return (double) hit / retrieved.size();
    }

    // ==================== 类型定义 ====================

    public static class Result {
        public final String query, description;
        public final List<String> expected, beforeDocs, afterDocs;
        public Result(String q, String d, List<String> e, List<String> b, List<String> a) {
            query = q; description = d; expected = e; beforeDocs = b; afterDocs = a;
        }
    }

    public static class BenchmarkResult {
        public final String name;
        public final List<Result> details;
        public final double beforeRecall, afterRecall, beforePrecision, afterPrecision;
        public final double beforeMRR, afterMRR, recallImprovement;
        public final String description;

        public BenchmarkResult(String name, List<Result> details, double beforeRecall, double afterRecall,
                               double beforePrecision, double afterPrecision, double beforeMRR, double afterMRR,
                               double recallImprovement, String description) {
            this.name = name; this.details = details; this.beforeRecall = beforeRecall;
            this.afterRecall = afterRecall; this.beforePrecision = beforePrecision;
            this.afterPrecision = afterPrecision; this.beforeMRR = beforeMRR;
            this.afterMRR = afterMRR; this.recallImprovement = recallImprovement;
            this.description = description;
        }
    }
}