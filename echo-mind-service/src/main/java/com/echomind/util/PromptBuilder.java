package com.echomind.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Prompt 构建器
 */
@Slf4j
@Component
public class PromptBuilder {

    public String build(String systemPrompt, String context, String knowledge, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");

        if (context != null && !context.isBlank()) {
            sb.append("【对话上下文】\n").append(context).append("\n\n");
        }

        if (knowledge != null && !knowledge.isBlank()) {
            sb.append("【相关知识】\n").append(knowledge).append("\n\n");
        }

        sb.append("用户: ").append(userMessage).append("\n");
        sb.append("助手: ");

        return sb.toString();
    }
}
