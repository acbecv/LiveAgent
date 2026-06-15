package com.echomind.intent;

import com.echomind.config.IntentWeightsConfig;
import com.echomind.dto.IntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 加权投票融合器
 * 综合三路结果，计算加权分数，输出最终意图
 */
@Slf4j
@Component
public class VotingIntentFuser {

    private final IntentWeightsConfig weightsConfig;

    public VotingIntentFuser(IntentWeightsConfig weightsConfig) {
        this.weightsConfig = weightsConfig;
    }

    /**
     * 融合三路意图识别结果
     * Score = w1*LLM_confidence + w2*Emb_confidence + w3*Pat_confidence
     */
    public IntentResult fuse(List<IntentResult> results) {
        if (results == null || results.isEmpty()) {
            return new IntentResult("unknown", "未知意图", 0.0, "fused");
        }

        if (results.size() == 1) {
            return results.get(0);
        }

        // 1. 按意图类别聚合分数
        Map<String, List<IntentResult>> grouped = results.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(IntentResult::getCategory));

        // 2. 为每个类别计算加权总分
        List<FusedCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<IntentResult>> entry : grouped.entrySet()) {
            double totalScore = 0;
            String categoryName = entry.getValue().get(0).getCategoryName();

            for (IntentResult r : entry.getValue()) {
                double weight = switch (r.getSource()) {
                    case "llm" -> weightsConfig.getWeights().getLlm();
                    case "embedding" -> weightsConfig.getWeights().getEmbedding();
                    case "pattern" -> weightsConfig.getWeights().getPattern();
                    default -> 0;
                };
                totalScore += r.getConfidence() * weight;
            }

            candidates.add(new FusedCandidate(entry.getKey(), categoryName, totalScore));
        }

        // 3. 取最高分
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        FusedCandidate best = candidates.get(0);

        log.debug("融合结果: category={}, score={}, candidates={}", best.code, best.score, candidates);

        return new IntentResult(best.code, best.name, best.score, "fused");
    }

    private record FusedCandidate(String code, String name, double score) {}
}
