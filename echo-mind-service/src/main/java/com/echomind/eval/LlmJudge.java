package com.echomind.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * LLM-as-Judge 裁判
 * 使用大模型对 AI 回答进行四维度评分
 */
@Slf4j
@Component
public class LlmJudge {

    private static final String JUDGE_PROMPT = """
            你是一位专业的 AI 客服质量评测员。请对以下 AI 客服回答进行四维度评分。

            用户问题: {question}
            AI回答: {answer}
            参考知识: {reference}

            请从以下四个维度评分 (1-5分):

            1. 准确性 (Accuracy): 回答中的事实信息是否准确？是否存在捏造/幻觉？
            2. 相关性 (Relevance): 回答是否切中用户的核心问题？
            3. 完整性 (Completeness): 回答是否覆盖了用户的所有疑问点？
            4. 友好度 (Friendliness): 语气是否友好专业？

            请严格按以下JSON格式返回评分结果:
            {
                "accuracy": 分数,
                "relevance": 分数,
                "completeness": 分数,
                "friendliness": 分数,
                "overall": 平均分,
                "reason": "简要评价"
            }
            """;

    private final ChatClient chatClient;

    public LlmJudge(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 四维度评分
     */
    public JudgeResult evaluate(String question, String answer, String reference) {
        try {
            String prompt = JUDGE_PROMPT
                    .replace("{question}", question)
                    .replace("{answer}", answer)
                    .replace("{reference}", reference != null ? reference : "无");

            String response = chatClient.prompt()
                    .user(u -> u.text(prompt))
                    .call()
                    .content();

            return parseResult(response);

        } catch (Exception e) {
            log.error("LLM评判异常", e);
            return new JudgeResult(3.0, 3.0, 3.0, 3.0, 3.0, "评测异常，默认中等评分");
        }
    }

    private JudgeResult parseResult(String response) {
        if (response == null) {
            return new JudgeResult(3.0, 3.0, 3.0, 3.0, 3.0, "无法解析");
        }

        try {
            double accuracy = extractScore(response, "accuracy");
            double relevance = extractScore(response, "relevance");
            double completeness = extractScore(response, "completeness");
            double friendliness = extractScore(response, "friendliness");
            double overall = extractScore(response, "overall");
            String reason = extractReason(response);

            return new JudgeResult(accuracy, relevance, completeness, friendliness, overall, reason);
        } catch (Exception e) {
            return new JudgeResult(3.0, 3.0, 3.0, 3.0, 3.0, "解析异常");
        }
    }

    private double extractScore(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return 3.0;

        int colon = json.indexOf(":", idx + search.length());
        if (colon < 0) return 3.0;

        int end = colon + 1;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
            end++;
        }

        try {
            return Double.parseDouble(json.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return 3.0;
        }
    }

    private String extractReason(String json) {
        String search = "\"reason\"";
        int idx = json.indexOf(search);
        if (idx < 0) return "";

        int colon = json.indexOf(":", idx + search.length());
        int start = json.indexOf("\"", colon + 1);
        if (start < 0) return "";

        int end = json.indexOf("\"", start + 1);
        if (end < 0) return json.substring(start + 1);

        return json.substring(start + 1, end);
    }

    public record JudgeResult(
            double accuracy,
            double relevance,
            double completeness,
            double friendliness,
            double overall,
            String reason
    ) {}
}
