package com.echomind.intent;

import com.echomind.dto.IntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Path 1: LLM 语义理解路径
 * 使用大模型分析用户消息的语义，识别意图类别
 */
@Slf4j
@Component
public class LlmIntentRecognizer implements IntentRecognizer {

    private static final String INTENT_PROMPT = """
        你是一个专业的客服意图分析器。请分析以下用户消息的意图，只返回JSON格式结果。

        意图类别:
        - shop_query: 查询某商户信息（如地址、电话、评分等）
        - shop_recommend: 推荐附近/同类商户
        - shop_compare: 对比多个商户
        - voucher_query: 查询可用优惠券
        - voucher_use: 询问如何使用优惠券
        - voucher_order_query: 查询用户拥有/已领的优惠券订单
        - seckill_query: 秒杀活动信息
        - user_profile: 个人信息查询
        - user_follow: 关注/取关操作
        - user_signin: 签到相关
        - general_help: 使用帮助/指引
        - general_chat: 非业务闲聊
        - complaint: 投诉/建议
        - unknown: 无法识别

        用户消息: {message}

        请严格按以下JSON格式返回，不要包含其他内容:
        {"category": "意图编码", "categoryName": "意图名称", "confidence": 置信度(0-1)}
        """;

    private final ChatClient chatClient;

    public LlmIntentRecognizer(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public IntentResult recognize(String userId, String message, String sessionId) {
        try {
            String response = chatClient.prompt()
                    .user(u -> u.text(INTENT_PROMPT.replace("{message}", message)))
                    .call()
                    .content();

            // 从返回JSON中解析结构化内容
            // 简化处理：直接调用LLM结构化输出
            String cleanedResponse = response != null ? response.trim() : "";

            // 尝试从 JSON 中提取字段 (简化版本)
            String category = extractJsonValue(cleanedResponse, "category");
            String categoryName = extractJsonValue(cleanedResponse, "categoryName");
            String confidenceStr = extractJsonValue(cleanedResponse, "confidence");

            if (category == null) {
                return new IntentResult("unknown", "未知意图", 0.5, "llm");
            }

            double confidence = 0.5;
            try {
                confidence = Double.parseDouble(confidenceStr);
            } catch (Exception ignored) {}

            return new IntentResult(category, categoryName, confidence, "llm");

        } catch (Exception e) {
            log.error("LLM意图识别异常", e);
            return new IntentResult("unknown", "未知意图", 0.3, "llm");
        }
    }

    private String extractJsonValue(String json, String key) {
        // 简单JSON字段提取
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) return null;

        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex < 0) return null;

        int start = colonIndex + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
            if (json.charAt(start) == '\"') {
                start++;
                break;
            }
            start++;
        }

        int end = start;
        while (end < json.length() && json.charAt(end) != '\"' && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }

        return json.substring(start, end).trim();
    }
}
