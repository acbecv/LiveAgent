package com.echomind.intent;

import com.echomind.dto.IntentResult;
import com.echomind.mapper.IntentTemplateMapper;
import com.echomind.entity.IntentTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Path 2: Embedding 相似度匹配路径
 * 将用户消息转为 Embedding 向量，与意图模板库做余弦相似度匹配
 */
@Slf4j
@Component
public class EmbeddingIntentRecognizer implements IntentRecognizer {

    private final EmbeddingModel embeddingModel;
    private final IntentTemplateMapper intentTemplateMapper;

    public EmbeddingIntentRecognizer(EmbeddingModel embeddingModel,
                                     IntentTemplateMapper intentTemplateMapper) {
        this.embeddingModel = embeddingModel;
        this.intentTemplateMapper = intentTemplateMapper;
    }

    @Override
    public IntentResult recognize(String userId, String message, String sessionId) {
        try {
            // 1. 生成用户消息向量
            float[] messageVector = embeddingModel.embed(message);

            // 2. 加载意图模板库
            List<IntentTemplate> templates = intentTemplateMapper.selectList(null);
            if (templates.isEmpty()) {
                return new IntentResult("unknown", "未知意图", 0.3, "embedding");
            }

            // 3. 计算与每个模板的相似度
            List<ScoredIntent> scored = new ArrayList<>();
            for (IntentTemplate template : templates) {
                double similarity = cosineSimilarity(messageVector, parseVector(template.getKeywords()));
                scored.add(new ScoredIntent(template.getIntentCode(), template.getIntentName(), similarity));
            }

            // 4. 取 Top-3
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            List<ScoredIntent> top3 = scored.stream().limit(3).collect(Collectors.toList());

            if (top3.isEmpty() || top3.get(0).score < 0.3) {
                return new IntentResult("unknown", "未知意图", 0.3, "embedding");
            }

            ScoredIntent best = top3.get(0);
            return new IntentResult(best.code, best.name, best.score, "embedding");

        } catch (Exception e) {
            log.error("Embedding意图识别异常", e);
            return new IntentResult("unknown", "未知意图", 0.2, "embedding");
        }
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) return 0;
        double dot = 0, norm1 = 0, norm2 = 0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10);
    }

    private float[] parseVector(String text) {
        // 简化：将关键词文本转为 float 数组（实际应使用embeddingModel）
        // 此处返回一个占位向量
        float[] vec = new float[1536];
        if (text != null) {
            Arrays.fill(vec, 0.001f);
        }
        return vec;
    }

    private record ScoredIntent(String code, String name, double score) {}
}
