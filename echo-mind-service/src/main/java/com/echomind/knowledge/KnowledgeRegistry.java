package com.echomind.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识条目内存注册表
 *
 * 维护所有知识条目的全量文本，供 BM25 关键词检索使用。
 * 由 KnowledgeIngestor 在启动注入时同步填充。
 *
 * 为什么不用 ChromaDB 做全文本检索？
 *   ChromaDB 主要用于向量检索（语义相似），而 BM25 是基于关键词的传统检索方法。
 *   直接在内存中做更快、更可靠（无网络开销、不依赖额外服务）。
 */
@Slf4j
@Component
public class KnowledgeRegistry {

    /** 知识条目：ID → 完整内容 */
    private final Map<String, KnowledgeEntry> entries = new ConcurrentHashMap<>();

    /** 注册知识条目 */
    public void register(String id, String content) {
        entries.put(id, new KnowledgeEntry(id, content));
    }

    /** 批量注册 */
    public void registerAll(List<KnowledgeEntry> list) {
        for (KnowledgeEntry e : list) {
            entries.put(e.id, e);
        }
    }

    /** 获取所有条目 */
    public Collection<KnowledgeEntry> getAll() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** 条目数量 */
    public int size() {
        return entries.size();
    }

    /** 是否为空 */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 知识条目
     */
    public record KnowledgeEntry(String id, String content) {}
}