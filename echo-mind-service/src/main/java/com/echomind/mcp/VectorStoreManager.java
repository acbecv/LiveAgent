package com.echomind.mcp;

import com.echomind.memory.ChromaDBClient;
import com.echomind.memory.ChromaDBClient.ChromaDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 向量库存管理器
 * 通过 ChromaDBClient 实现向量存储与检索的统一入口
 */
@Slf4j
@Service
public class VectorStoreManager {

    private final ChromaDBClient chromaDBClient;
    private final EmbeddingModel embeddingModel;

    @Value("${echomind.vector.default-collection:knowledge_base}")
    private String defaultCollection;

    @Value("${echomind.vector.default-top-k:5}")
    private int defaultTopK;

    public VectorStoreManager(ChromaDBClient chromaDBClient, EmbeddingModel embeddingModel) {
        this.chromaDBClient = chromaDBClient;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 向量检索 —— 将查询文本转为 embedding，调用 ChromaDB 相似度检索
     *
     * @param query 查询文本
     * @return 按相似度排序的文档列表
     */
    public List<ChromaDocument> search(String query) {
        return search(query, defaultCollection, defaultTopK);
    }

    /**
     * 向量检索（指定集合和返回数量）
     *
     * @param query          查询文本
     * @param collectionName ChromaDB 集合名
     * @param topK           返回结果数量
     * @return 按相似度排序的文档列表
     */
    public List<ChromaDocument> search(String query, String collectionName, int topK) {
        if (query == null || query.isBlank()) {
            log.warn("向量检索查询文本为空，跳过检索");
            return Collections.emptyList();
        }

        try {
            // 1. 将查询文本转为 embedding 向量
            float[] queryEmbedding = embeddingModel.embed(query);
            log.debug("查询文本 embedding 完成，维度: {}", queryEmbedding.length);

            // 2. 调用 ChromaDB 进行相似度检索
            List<ChromaDocument> results = chromaDBClient.similaritySearch(collectionName, queryEmbedding, topK);
            log.info("向量检索完成: query='{}', collection={}, topK={}, 命中={}条",
                    query, collectionName, topK, results.size());
            return results;

        } catch (Exception e) {
            log.error("向量检索失败: query='{}', collection={}", query, collectionName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 向指定集合添加文档（由外部传入 embedding）
     */
    public void addDocuments(String collectionName, List<ChromaDocument> documents) {
        chromaDBClient.addDocuments(collectionName, documents);
        log.info("向量库添加文档: collection={}, count={}", collectionName, documents.size());
    }

    /**
     * 确保集合存在（首次检索前可调用）
     */
    public void ensureCollection(String collectionName) {
        chromaDBClient.getOrCreateCollection(collectionName);
    }

    /**
     * 检查集合中是否已有文档（用于判断是否需要首次导入）
     */
    public boolean hasDocuments(String collectionName) {
        return chromaDBClient.hasDocuments(collectionName);
    }
}
