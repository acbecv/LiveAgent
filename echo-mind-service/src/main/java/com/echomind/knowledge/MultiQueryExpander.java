package com.echomind.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多 Query 扩展器
 *
 * 使用 LLM 将用户原始提问扩展为多个语义等价但表述不同的查询，
 * 每个变体从不同角度覆盖知识库，提升召回率。
 *
 * 典型场景：
 *   用户："怎么退货"
 *   → 扩展：["退货流程", "如何申请退款", "售后处理步骤"]
 *
 * 为什么需要扩展？
 *   单一 query 表述可能不完整或与知识库用词不一致，
 *   多 query 并行检索可以覆盖更多相关文档。
 */
@Slf4j
@Component
public class MultiQueryExpander {

    private static final String EXPAND_PROMPT = """
            你是一个查询扩展专家。请将用户的提问扩展为 3-4 个语义等价但表述不同的查询。

            规则:
            - 每个查询从不同角度覆盖用户意图
            - 使用不同的措辞和关键词
            - 保持简洁，每个查询不超过 20 字
            - 输出格式：每行一个查询，不要编号、不要前缀

            用户提问: {query}

            请输出扩展后的查询（每行一个）：
            """;

    private final ChatClient chatClient;

    public MultiQueryExpander(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 扩展查询
     *
     * @param query 用户原始提问
     * @return 扩展后的查询列表（至少包含原始 query）
     */
    public List<String> expand(String query) {
        List<String> queries = new ArrayList<>();
        queries.add(query); // 始终包含原始 query

        try {
            String result = chatClient.prompt()
                    .user(u -> u.text(EXPAND_PROMPT.replace("{query}", query)))
                    .call()
                    .content();

            if (result != null && !result.isBlank()) {
                List<String> expanded = Arrays.stream(result.split("\\n"))
                        .map(String::trim)
                        .filter(s -> !s.isBlank() && !s.equals(query))
                        .distinct()
                        .collect(Collectors.toList());

                queries.addAll(expanded);
                log.debug("Query 扩展完成: 原始='{}' → 扩展后={}个 query", query, queries.size());
            }
        } catch (Exception e) {
            log.warn("Query 扩展失败，使用原始查询降级: query='{}'", query);
        }

        return queries;
    }
}