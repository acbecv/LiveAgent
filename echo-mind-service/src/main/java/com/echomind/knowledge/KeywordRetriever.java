package com.echomind.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BM25 关键词检索器
 *
 * 与向量检索互补：
 *   - 向量检索擅长语义相似（"怎么退货" ↔ "售后申请"）
 *   - BM25 擅长精确词语匹配（"优惠券" → 直接命中含"优惠券"的文档）
 *
 * 实现说明：
 *   中文分词采用字符级 bigram + unigram 混合策略，
 *   避免依赖外部分词器，保持启动轻量。
 *
 * BM25 公式：
 *   score(D,Q) = Σ IDF(qi) × f(qi,D) × (k1+1) / (f(qi,D) + k1×(1-b+b×|D|/avgdl))
 */
@Slf4j
@Component
public class KeywordRetriever {

    /** 词 → (文档ID → 词频) 倒排索引 */
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>();

    /** 文档长度缓存 */
    private final Map<String, Integer> docLengths = new HashMap<>();

    /** 平均文档长度（懒计算） */
    private volatile double avgDocLength = 0;

    /** 总文档数 */
    private int totalDocs = 0;

    /** 索引是否已构建 */
    private volatile boolean indexed = false;

    // BM25 超参数
    private static final double K1 = 1.5;  // 词频饱和度
    private static final double B  = 0.75; // 长度归一化

    /**
     * 从 KnowledgeRegistry 全量构建倒排索引
     */
    public synchronized void buildIndex(KnowledgeRegistry registry) {
        if (registry.isEmpty()) return;

        invertedIndex.clear();
        docLengths.clear();
        totalDocs = 0;

        for (KnowledgeRegistry.KnowledgeEntry entry : registry.getAll()) {
            List<String> tokens = tokenize(entry.content());
            docLengths.put(entry.id(), tokens.size());
            totalDocs++;

            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens) {
                termFreq.merge(token, 1, Integer::sum);
            }

            for (Map.Entry<String, Integer> tf : termFreq.entrySet()) {
                invertedIndex
                    .computeIfAbsent(tf.getKey(), k -> new HashMap<>())
                    .put(entry.id(), tf.getValue());
            }
        }

        avgDocLength = docLengths.values().stream().mapToInt(Integer::intValue).average().orElse(1);
        indexed = true;
        log.info("BM25 倒排索引构建完成: 文档={}篇, 词项={}个, avgDocLength={}",
                totalDocs, invertedIndex.size(), String.format("%.1f", avgDocLength));
    }

    /**
     * BM25 检索：返回 Top-K 文档（按 score 降序）
     *
     * @param query 查询文本
     * @param registry 知识条目注册表
     * @param topK 返回数量
     * @return 按 BM25 分数降序的条目列表
     */
    public List<ScoredEntry> search(String query, KnowledgeRegistry registry, int topK) {
        if (!indexed || registry.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        // 候选文档 → 分数
        Map<String, Double> scores = new HashMap<>();
        Set<String> candidateDocIds = new HashSet<>();

        // 统计每个文档和查询的匹配
        for (String token : queryTokens) {
            Map<String, Integer> postings = invertedIndex.get(token);
            if (postings != null) {
                candidateDocIds.addAll(postings.keySet());

                double idf = idf(token);
                for (Map.Entry<String, Integer> posting : postings.entrySet()) {
                    String docId = posting.getKey();
                    int tf = posting.getValue();
                    int docLen = docLengths.getOrDefault(docId, 1);
                    double score = idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docLen / avgDocLength));
                    scores.merge(docId, score, Double::sum);
                }
            }
        }

        // 排序 + Top-K
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    KnowledgeRegistry.KnowledgeEntry entry = findEntry(registry, e.getKey());
                    return new ScoredEntry(e.getKey(), entry != null ? entry.content() : "", e.getValue());
                })
                .toList();
    }

    /**
     * IDF 计算
     */
    private double idf(String term) {
        Map<String, Integer> postings = invertedIndex.get(term);
        if (postings == null || postings.isEmpty()) return 0;
        return Math.log((totalDocs - postings.size() + 0.5) / (postings.size() + 0.5) + 1.0);
    }

    // ==================== 分词 ====================

    /**
     * 混合分词策略：
     *   - ASCII 字母/数字：连续序列为一个 token（最小 2 字符）
     *   - 中文字符：unigram + bigram 滑动窗口
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (Character.isLetterOrDigit(c) && c < 128) {
                // ASCII 单词
                StringBuilder sb = new StringBuilder();
                while (i < text.length() && Character.isLetterOrDigit(text.charAt(i)) && text.charAt(i) < 128) {
                    sb.append(text.charAt(i));
                    i++;
                }
                String word = sb.toString().toLowerCase();
                if (word.length() >= 2) {
                    tokens.add(word);
                }
            } else if (Character.isWhitespace(c) || isPunctuation(c)) {
                i++;
            } else {
                // CJK 字符：unigram + bigram
                int start = i;
                while (i < text.length() && !Character.isWhitespace(text.charAt(i))
                        && !isPunctuation(text.charAt(i))
                        && !(Character.isLetterOrDigit(text.charAt(i)) && text.charAt(i) < 128)) {
                    i++;
                }
                String cjk = text.substring(start, i);
                // unigram
                for (int j = 0; j < cjk.length(); j++) {
                    tokens.add(String.valueOf(cjk.charAt(j)));
                }
                // bigram
                for (int j = 0; j < cjk.length() - 1; j++) {
                    tokens.add(cjk.substring(j, j + 2));
                }
            }
        }
        return tokens;
    }

    private boolean isPunctuation(char c) {
        return "，。！？；：\"\"''（）【】《》…—～、,.!?;:\"'()[]{}<>@#$%^&*+=/\\|`~".indexOf(c) >= 0;
        //                    ↑↑ 转义双引号
    }

    // ==================== 辅助 ====================

    private KnowledgeRegistry.KnowledgeEntry findEntry(KnowledgeRegistry registry, String id) {
        return registry.getAll().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * BM25 评分结果
     */
    public record ScoredEntry(String id, String content, double score) {}
}