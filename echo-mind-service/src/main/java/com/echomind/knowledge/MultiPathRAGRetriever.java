package com.echomind.knowledge;

import com.echomind.mcp.VectorStoreManager;
import com.echomind.memory.ChromaDBClient.ChromaDocument;
import com.echomind.util.TokenCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 多路并行 RAG 检索编排器
 *
 * 三路并行检索 + RRF 融合，解决单一向量检索的召回不足问题。
 *
 * 检索架构：
 * <pre>
 *   用户提问
 *     ├── LLM 多 Query 扩展（1 → 3~5 个 query 变体）
 *     │
 *     ├── 对每个 query 变体，两路并行检索：
 *     │     ├── 路1: 向量检索（ChromaDB 语义相似度）
 *     │     └── 路2: BM25 关键词检索（内存倒排索引精确匹配）
 *     │
 *     └── RRF 融合 → 排序 → 截断 → 格式化注入
 * </pre>
 *
 * 互补原理：
 *   - 向量检索擅长语义相似但精确词语匹配弱
 *     "怎么退货" vs "申请售后" → 语义接近，向量能召回来
 *   - BM25 擅长精确词语匹配但不懂语义
 *     "优惠券怎么用" → 包含"优惠券"的文档直接命中
 *   - RRF 不依赖原始分数的绝对大小，只依赖相对排名来融合
 *     不同检索方式的分数分布差异很大，RRF 能公平融合
 */
@Slf4j
@Service
public class MultiPathRAGRetriever {

    private final VectorStoreManager vectorStoreManager;
    private final KeywordRetriever keywordRetriever;
    private final MultiQueryExpander queryExpander;
    private final KnowledgeRegistry knowledgeRegistry;
    private final TokenCounter tokenCounter;

    /** ChromaDB 知识库集合名 */
    @Value("${echomind.rag.knowledge-collection:knowledge_base}")
    private String knowledgeCollection;

    /** 每路检索的 Top-K */
    @Value("${echomind.rag.per-path-top-k:5}")
    private int perPathTopK;

    /** 向量相似度阈值（distance ≤ 此值才保留） */
    @Value("${echomind.rag.similarity-threshold:1.0}")
    private double similarityThreshold;

    /** BM25 最低分数阈值 */
    @Value("${echomind.rag.bm25-min-score:0.1}")
    private double bm25MinScore;

    /** RRF 融合后最终返回 Top-K */
    @Value("${echomind.rag.final-top-k:5}")
    private int finalTopK;

    /** 知识项最大文字长度（超出截断） */
    @Value("${echomind.rag.max-item-length:500}")
    private int maxItemLength;

    /** 注入上下文的最大 token 数 */
    @Value("${echomind.rag.max-context-tokens:800}")
    private int maxContextTokens;

    /** 是否启用多 Query 扩展 */
    @Value("${echomind.rag.multi-query-enabled:true}")
    private boolean multiQueryEnabled;

    /** 并行检索超时（秒） */
    private static final int PARALLEL_TIMEOUT_SEC = 5;

    /** RRF 平滑参数 k */
    private static final double RRF_K = 60.0;

    public MultiPathRAGRetriever(VectorStoreManager vectorStoreManager,
                                 KeywordRetriever keywordRetriever,
                                 MultiQueryExpander queryExpander,
                                 KnowledgeRegistry knowledgeRegistry,
                                 TokenCounter tokenCounter) {
        this.vectorStoreManager = vectorStoreManager;
        this.keywordRetriever = keywordRetriever;
        this.queryExpander = queryExpander;
        this.knowledgeRegistry = knowledgeRegistry;
        this.tokenCounter = tokenCounter;
    }

    // ==================== 核心检索入口 ====================

    /**
     * 多路并行检索 + RRF 融合
     *
     * @param query 用户原始提问
     * @return 格式化后的知识上下文（空字符串表示无结果）
     */
    public String retrieve(String query) {
        long start = System.currentTimeMillis();

        try {
            // Step 1: 多 Query 扩展
            List<String> queries = multiQueryEnabled
                    ? queryExpander.expand(query)
                    : List.of(query);
            log.debug("多路 RAG: 原始query='{}', 扩展后={}个", query, queries.size());

            // Step 2: 对每个 query 变体，两路并行检索
            // 每个 query 产生一个 CompletableFuture，内部并行执行向量 + BM25
            List<CompletableFuture<List<RankedHit>>> allFutures = new ArrayList<>();

            for (String q : queries) {
                // 向量检索
                CompletableFuture<List<RankedHit>> vecFuture = CompletableFuture.supplyAsync(
                        () -> searchVector(q));
                // BM25 检索
                CompletableFuture<List<RankedHit>> bm25Future = CompletableFuture.supplyAsync(
                        () -> searchBM25(q));

                allFutures.add(vecFuture);
                allFutures.add(bm25Future);
            }

            // Step 3: 等待所有检索完成（带超时）
            CompletableFuture<Void> all = CompletableFuture.allOf(
                    allFutures.toArray(new CompletableFuture[0]));

            all.get(PARALLEL_TIMEOUT_SEC, TimeUnit.SECONDS);

            // 收集所有命中
            List<List<RankedHit>> allRankings = new ArrayList<>();
            for (CompletableFuture<List<RankedHit>> future : allFutures) {
                try {
                    List<RankedHit> hits = future.getNow(Collections.emptyList());
                    if (!hits.isEmpty()) {
                        allRankings.add(hits);
                    }
                } catch (Exception ignored) {}
            }

            if (allRankings.isEmpty()) {
                log.debug("多路 RAG 所有路径均无结果: query='{}'", query);
                return "";
            }

            // Step 4: RRF 融合
            List<ScoredHit> fused = rrfFusion(allRankings);

            // Step 5: 截断 + Token 预算 → 格式化
            List<String> items = new ArrayList<>();
            for (ScoredHit hit : fused) {
                String text = truncateText(hit.content(), maxItemLength);
                if (!text.isBlank()) {
                    items.add(text);
                }
            }

            if (items.isEmpty()) return "";

            items = trimByTokenBudget(items, maxContextTokens);
            String context = formatContext(items, query);

            long elapsed = System.currentTimeMillis() - start;
            log.info("多路 RAG 检索完成: query='{}', query数={}, 检索路径数={}, "
                     + "融合后={}条, 注入={}条, 耗时={}ms",
                    query.substring(0, Math.min(30, query.length())),
                    queries.size(), allRankings.size(), fused.size(), items.size(), elapsed);

            return context;

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("多路 RAG 检索超时 ({}s): query='{}'", PARALLEL_TIMEOUT_SEC, query);
            return "";
        } catch (Exception e) {
            log.error("多路 RAG 检索异常 (不影响主流程): query='{}'", query, e);
            return "";
        }
    }

    // ==================== 检索路径 ====================

    /**
     * 路1: 向量检索 → ChromaDB
     */
    private List<RankedHit> searchVector(String query) {
        try {
            List<ChromaDocument> docs = vectorStoreManager.search(query, knowledgeCollection, perPathTopK);
            if (docs == null || docs.isEmpty()) return Collections.emptyList();

            List<RankedHit> hits = new ArrayList<>();
            for (int i = 0; i < docs.size(); i++) {
                ChromaDocument doc = docs.get(i);
                if (doc.getScore() <= similarityThreshold && doc.getText() != null) {
                    hits.add(new RankedHit(
                            doc.getText(),
                            i + 1,  // rank starting from 1
                            "vector",
                            query));
                }
            }
            return hits;
        } catch (Exception e) {
            log.debug("向量检索路径异常: query='{}', err={}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 路2: BM25 关键词检索 → 内存倒排索引
     */
    private List<RankedHit> searchBM25(String query) {
        try {
            List<KeywordRetriever.ScoredEntry> entries = keywordRetriever.search(
                    query, knowledgeRegistry, perPathTopK);
            if (entries.isEmpty()) return Collections.emptyList();

            List<RankedHit> hits = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                KeywordRetriever.ScoredEntry entry = entries.get(i);
                if (entry.score() >= bm25MinScore) {
                    hits.add(new RankedHit(
                            entry.content(),
                            i + 1,
                            "bm25",
                            query));
                }
            }
            return hits;
        } catch (Exception e) {
            log.debug("BM25 检索路径异常: query='{}', err={}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== RRF 融合 ====================

    /**
     * Reciprocal Rank Fusion
     *
     * score(doc) = Σ 1 / (k + rank_i)
     *
     * 不依赖原始分数的绝对大小，只依赖相对排名。
     * 跨不同检索方式的分数不可比（向量 distance vs BM25 score），
     * RRF 通过排名来融合，天然解决了这个问题。
     */
    private List<ScoredHit> rrfFusion(List<List<RankedHit>> allRankings) {
        // content → 累积 RRF 分数
        Map<String, ScoredHit> merged = new LinkedHashMap<>();

        for (List<RankedHit> ranking : allRankings) {
            for (RankedHit hit : ranking) {
                double rrfScore = 1.0 / (RRF_K + hit.rank());
                merged.merge(
                        hit.content(),
                        new ScoredHit(hit.content(), rrfScore),
                        (existing, incoming) -> new ScoredHit(
                                existing.content(),
                                existing.score() + incoming.score()));
            }
        }

        // 按 RRF 分数降序排列，取 Top-K
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(ScoredHit::score).reversed())
                .limit(finalTopK)
                .toList();
    }

    // ==================== 格式化 ====================

    private String formatContext(List<String> items, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- 知识库相关内容 ---\n");
        sb.append("（以下信息来自系统知识库，经过多路检索融合，请优先参考以回答用户问题）\n\n");

        for (int i = 0; i < items.size(); i++) {
            sb.append("【知识点 ").append(i + 1).append("】\n");
            sb.append(items.get(i)).append("\n\n");
        }
        sb.append("--- 知识库内容结束 ---\n");

        return sb.toString();
    }

    // ==================== 辅助 ====================

    private String truncateText(String text, int maxLength) {
        if (text == null || text.isBlank()) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private List<String> trimByTokenBudget(List<String> items, int maxTokens) {
        List<String> trimmed = new ArrayList<>();
        int currentTokens = 0;
        for (String item : items) {
            int itemTokens = tokenCounter.count(item);
            if (currentTokens + itemTokens <= maxTokens) {
                trimmed.add(item);
                currentTokens += itemTokens;
            }
        }
        return trimmed;
    }

    // ==================== 内部类型 ====================

    /** 单条检索路径的命中结果 */
    record RankedHit(String content, int rank, String source, String query) {}

    /** RRF 融合后的合并命中 */
    record ScoredHit(String content, double score) {}
}