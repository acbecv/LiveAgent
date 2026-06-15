package com.echomind.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ChromaDB HTTP 客户端
 * 通过 REST API 与 ChromaDB 交互，支持向量存储与检索
 *
 * ChromaDB REST API 版本兼容: v1.x
 */
@Slf4j
@Component
@Lazy
public class ChromaDBClient {

    @Value("${echomind.chromadb.url:http://localhost:8000}")
    private String baseUrl;

    @Value("${echomind.chromadb.tenant:default_tenant}")
    private String tenant;

    @Value("${echomind.chromadb.database:default_database}")
    private String database;

    private final HttpClient httpClient;

    /** 名称 → UUID 缓存，避免每次都查 */
    private final Map<String, String> collectionIdCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ChromaDBClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 根据名称获取集合 UUID（查缓存 → GET API → 解析响应）
     */
    private String resolveCollectionId(String collectionName) {
        // 缓存命中
        String cached = collectionIdCache.get(collectionName);
        if (cached != null) return cached;

        // GET 获取集合信息
        try {
            String url = buildCollectionGetUrl(collectionName);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JSONObject json = JSON.parseObject(resp.body());
                String id = json.getString("id");
                if (id != null) {
                    collectionIdCache.put(collectionName, id);
                    return id;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 构建 ChromaDB v2 API 的集合列表 URL（用于创建/列出集合）
     * 格式：/api/v2/tenants/{tenant}/databases/{database}/collections
     */
    private String buildCollectionsListUrl() {
        return String.format("%s/api/v2/tenants/%s/databases/%s/collections",
                baseUrl, tenant, database);
    }

    /**
     * 构建 ChromaDB v2 API 的集合操作 URL（add/query/get/delete）
     * 格式：/api/v2/tenants/{tenant}/databases/{database}/collections/{name}/{action}
     */
    private String buildCollectionActionUrl(String collectionName, String action) {
        return String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s/%s",
                baseUrl, tenant, database, collectionName, action);
    }

    /**
     * 构建 ChromaDB v2 API 的集合获取 URL（GET 集合信息）
     */
    private String buildCollectionGetUrl(String collectionName) {
        return String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s",
                baseUrl, tenant, database, collectionName);
    }

    // ==================== 集合管理 ====================

    /**
     * 获取或创建集合，返回集合的 UUID
     */
    public String getOrCreateCollection(String collectionName) {
        // 缓存命中
        String cached = collectionIdCache.get(collectionName);
        if (cached != null) return cached;

        // 尝试获取已有集合
        String id = resolveCollectionId(collectionName);
        if (id != null) return id;

        // 创建新集合
        try {
            JSONObject body = new JSONObject();
            body.put("name", collectionName);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(buildCollectionsListUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                JSONObject json = JSON.parseObject(resp.body());
                id = json.getString("id");
                if (id != null) {
                    collectionIdCache.put(collectionName, id);
                    log.info("ChromaDB 集合创建成功: {} (id={})", collectionName, id);
                    return id;
                }
            }
            log.warn("ChromaDB 集合创建响应异常: status={}, body={}", resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.warn("ChromaDB 集合创建失败 (不影响运行): {}", e.getMessage());
        }
        return null;
    }

    // ==================== 文档管理 (含向量) ====================

    /**
     * 检查集合中是否已有文档
     * @return true = 集合非空, false = 集合为空或不存在
     */
    public boolean hasDocuments(String collectionName) {
        try {
            String collId = resolveCollectionId(collectionName);
            if (collId == null) return false;

            JSONObject body = new JSONObject();
            body.put("limit", 1);
            body.put("include", JSONArray.of());

            String getUrl = buildCollectionActionUrl(collId, "get");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(getUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JSONObject json = JSON.parseObject(resp.body());
                JSONArray ids = json.getJSONArray("ids");
                return ids != null && !ids.isEmpty();
            }
        } catch (Exception e) {
            log.debug("检查集合文档数失败: collection={}, err={}", collectionName, e.getMessage());
        }
        return false;
    }

    /**
     * 向 ChromaDB 中添加文档（由外部传入 embedding 向量）
     */
    public void addDocuments(String collectionName, List<ChromaDocument> documents) {
        if (documents == null || documents.isEmpty()) return;

        try {
            JSONObject body = new JSONObject();
            JSONArray ids = new JSONArray();
            JSONArray metadatas = new JSONArray();
            JSONArray embeddings = new JSONArray();
            JSONArray texts = new JSONArray();

            for (ChromaDocument doc : documents) {
                ids.add(doc.id);
                metadatas.add(doc.metadata != null ? JSONObject.from(doc.metadata) : new JSONObject());
                texts.add(doc.text);

                JSONArray emb = new JSONArray();
                if (doc.embedding != null) {
                    for (float v : doc.embedding) emb.add((double) v);
                } else {
                    // ChromaDB v2 要求 embeddings 字段必须存在
                    // 如果外部未提供 embedding，使用零向量占位（ChromaDB 会自动生成）
                    for (int i = 0; i < 384; i++) emb.add(0.0);
                }
                embeddings.add(emb);
            }

            body.put("ids", ids);
            body.put("metadatas", metadatas);
            body.put("documents", texts);
            body.put("embeddings", embeddings);

            String collId = resolveCollectionId(collectionName);
            if (collId == null) {
                log.warn("ChromaDB 添加文档失败: 集合不存在 collection={}", collectionName);
                return;
            }
            String collUrl = buildCollectionActionUrl(collId, "add");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(collUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                log.debug("ChromaDB 添加文档成功: {} 条, collection={}", documents.size(), collectionName);
            } else {
                log.warn("ChromaDB 添加文档响应异常: status={}, body={}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("ChromaDB 添加文档失败: {}", e.getMessage());
        }
    }

    /**
     * 向量相似度检索
     */
    public List<ChromaDocument> similaritySearch(String collectionName, float[] queryEmbedding, int topK) {
        List<ChromaDocument> results = new ArrayList<>();
        try {
            JSONObject body = new JSONObject();
            JSONArray emb = new JSONArray();
            if (queryEmbedding != null) {
                for (float v : queryEmbedding) emb.add((double) v);
            } else {
                // 如果 embedding 为空，使用默认零向量
                for (int i = 0; i < 384; i++) emb.add(0.0);
            }
            body.put("query_embeddings", JSONArray.of(emb));
            body.put("n_results", topK);
            body.put("include", JSONArray.of("documents", "metadatas", "distances"));

            String collId = resolveCollectionId(collectionName);
            if (collId == null) {
                log.warn("ChromaDB 检索失败: 集合不存在 collection={}", collectionName);
                return results;
            }
            String queryUrl = buildCollectionActionUrl(collId, "query");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JSONObject json = JSON.parseObject(resp.body());
                JSONArray idsArr = json.getJSONArray("ids");
                JSONArray docsArr = json.getJSONArray("documents");
                JSONArray metaArr = json.getJSONArray("metadatas");
                JSONArray distArr = json.getJSONArray("distances");

                if (idsArr != null && !idsArr.isEmpty()) {
                    JSONArray idList = idsArr.getJSONArray(0);
                    JSONArray docList = docsArr != null ? docsArr.getJSONArray(0) : null;
                    JSONArray metaList = metaArr != null ? metaArr.getJSONArray(0) : null;
                    JSONArray distList = distArr != null ? distArr.getJSONArray(0) : null;

                    for (int i = 0; i < idList.size(); i++) {
                        ChromaDocument doc = new ChromaDocument();
                        doc.id = idList.getString(i);
                        doc.text = docList != null && i < docList.size() ? docList.getString(i) : "";
                        Map<String, String> meta = new HashMap<>();
                        if (metaList != null && i < metaList.size()) {
                            JSONObject metaObj = metaList.getJSONObject(i);
                            if (metaObj != null) {
                                for (String key : metaObj.keySet()) {
                                    meta.put(key, String.valueOf(metaObj.get(key)));
                                }
                            }
                        }
                        doc.metadata = meta;
                        doc.score = distList != null && i < distList.size() ? distList.getDoubleValue(i) : 0.0;
                        results.add(doc);
                    }
                }
            } else {
                log.warn("ChromaDB 检索响应异常: status={}, body={}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("ChromaDB 检索失败: {}", e.getMessage());
        }
        return results;
    }

    /**
     * 通过 metadata 过滤删除文档
     */
    public void deleteDocuments(String collectionName, Map<String, String> filter) {
        try {
            JSONObject body = new JSONObject();
            JSONObject where = new JSONObject();
            for (Map.Entry<String, String> entry : filter.entrySet()) {
                where.put(entry.getKey(), entry.getValue());
            }
            body.put("where", where);

            String collId = resolveCollectionId(collectionName);
            if (collId == null) {
                log.warn("ChromaDB 删除文档失败: 集合不存在 collection={}", collectionName);
                return;
            }
            String delUrl = buildCollectionActionUrl(collId, "delete");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(delUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("ChromaDB 删除文档: collection={}, filter={}", collectionName, filter);
        } catch (Exception e) {
            log.warn("ChromaDB 删除文档失败: {}", e.getMessage());
        }
    }

    // ==================== 文档模型 ====================

    public static class ChromaDocument {
        private String id;
        private String text;
        private Map<String, String> metadata;
        private float[] embedding;
        private double score; // 仅用于检索结果

        public ChromaDocument() {}

        public ChromaDocument(String id, String text, Map<String, String> metadata, float[] embedding) {
            this.id = id;
            this.text = text;
            this.metadata = metadata;
            this.embedding = embedding;
        }

        public String getId() { return id; }
        public String getText() { return text; }
        public Map<String, String> getMetadata() { return metadata; }
        public float[] getEmbedding() { return embedding; }
        public double getScore() { return score; }
    }
}
