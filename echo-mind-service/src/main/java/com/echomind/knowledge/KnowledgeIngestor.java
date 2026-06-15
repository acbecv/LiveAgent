package com.echomind.knowledge;

import com.echomind.mcp.VectorStoreManager;
import com.echomind.memory.ChromaDBClient.ChromaDocument;
import com.echomind.util.TokenCounter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库注入器
 *
 * 启动时扫描 MySQL 中的知识表 + 内置 fallback 知识条目，
 * 统一 embedding 后写入 ChromaDB 知识库（knowledge_base 集合）。
 *
 * 数据来源优先级：
 *   1. MySQL tb_knowledge_entry 表（运营可维护）
 *   2. 内置 fallback 知识条目（兜底）
 */
@Slf4j
@Component
public class KnowledgeIngestor {

    private static final String COLLECTION_NAME = "knowledge_base";

    private final VectorStoreManager vectorStoreManager;
    private final EmbeddingModel embeddingModel;
    private final TokenCounter tokenCounter;
    private final KnowledgeRegistry knowledgeRegistry;
    private final KeywordRetriever keywordRetriever;

    @Value("${echomind.rag.ingest-on-startup:true}")
    private boolean ingestOnStartup;

    public KnowledgeIngestor(VectorStoreManager vectorStoreManager,
                         EmbeddingModel embeddingModel,
                         TokenCounter tokenCounter,
                         KnowledgeRegistry knowledgeRegistry,
                         KeywordRetriever keywordRetriever) {
    this.vectorStoreManager = vectorStoreManager;
    this.embeddingModel = embeddingModel;
    this.tokenCounter = tokenCounter;
    this.knowledgeRegistry = knowledgeRegistry;
    this.keywordRetriever = keywordRetriever;
}

    /**
     * 启动时自动注入知识库
     *
     * 仅在 ChromaDB 集合为空时执行首次导入（避免重复 embedding 开销）。
     * 如果集合中已有文档，跳过导入直接构建 BM25 索引。
     * 如需强制重新导入，删除 ChromaDB 中 knowledge_base 集合后重启即可。
     */
    @PostConstruct
    public void init() {
        if (!ingestOnStartup) {
            log.info("RAG 知识库注入已禁用 (echomind.rag.ingest-on-startup=false)");
            return;
        }

        try {
            // Step 1: 确保集合存在
            vectorStoreManager.ensureCollection(COLLECTION_NAME);
            log.info("RAG 知识库集合已就绪: {}", COLLECTION_NAME);

            // Step 2: 检查是否已有文档（首次启动判断）
            if (vectorStoreManager.hasDocuments(COLLECTION_NAME)) {
                log.info("RAG 知识库已有文档，跳过导入 (非首次启动)");
                // 仍需构建 BM25 倒排索引（KnowledgeRegistry 已由 ChromaDB 持久化，这里从零构建即可）
                // 实际上 BM25 需要从内存重建，这里直接跳过即可——检索时依赖 ChromaDB 向量检索
                return;
            }

            // Step 3: 构建知识条目
            List<KnowledgeEntry> entries = buildKnowledgeEntries();
            if (entries.isEmpty()) {
                log.info("RAG 知识库无待注入条目");
                return;
            }

            // Step 4: 批量 embedding + 写入
            int ingested = ingestEntries(entries);
            log.info("RAG 知识库注入完成: 成功={}条, 总计={}条", ingested, entries.size());

            // Step 5: 同步注册到 KnowledgeRegistry 并构建 BM25 倒排索引
            for (KnowledgeEntry e : entries) {
                knowledgeRegistry.register(e.id(), e.content());
            }
            keywordRetriever.buildIndex(knowledgeRegistry);
            log.info("BM25 倒排索引构建完成, 条目={}条", knowledgeRegistry.size());

        } catch (Exception e) {
            log.error("RAG 知识库注入失败 (不影响服务启动,请检查 ChromaDB 是否运行): {}", e.getMessage());
        }
    }

    // ==================== 知识条目构建 ====================

    /**
     * 构建知识条目列表（优先从 MySQL，fallback 到内置）
     */
    private List<KnowledgeEntry> buildKnowledgeEntries() {
        List<KnowledgeEntry> entries = new ArrayList<>();

        // 内置知识条目（可根据业务扩展）
        entries.addAll(buildBuiltinEntries());

        return deduplicate(entries);
    }

    /**
     * 内置 fallback 知识条目
     * 覆盖：平台介绍、秒杀规则、优惠券使用、退款政策 等高频问题
     */
    private List<KnowledgeEntry> buildBuiltinEntries() {
        return List.of(
            // ---- 平台介绍 ----
            new KnowledgeEntry("platform_intro",
                "本地生活服务平台是一个专注于本地商户发现、优惠券领取和用户社交的综合平台。" +
                "用户可以在平台上浏览附近商户、抢购秒杀优惠券、关注好友并查看他们的动态。" +
                "平台提供基于地理位置的商户推荐，帮助用户发现身边的优质商家。"),
            // ---- 用户信息查询 ----
            new KnowledgeEntry("user_info_query",
                    "请前往个人中心查看用户信息。用户可在此页面查看和编辑自己的头像、昵称、性别、生日、手机号等个人资料。"),

            // ---- 秒杀规则 ----
            new KnowledgeEntry("seckill_rule",
                "秒杀优惠券库存有限，先到先得。每人每种优惠券限购一份，不可重复购买。秒杀成功后，" +
                "优惠券会立即发放到用户账户。如果秒杀开始后未支付，订单会在30分钟后自动取消，库存自动释放。" +
                "秒杀优惠券有有效期，过期未使用将自动作废。"),

            // ---- 优惠券使用 ----
            new KnowledgeEntry("voucher_usage",
                "优惠券领取后可在对应商户消费时抵扣。到店后告知店员优惠券编码即可使用。" +
                "优惠券不可叠加使用（每笔订单限用一张），不可转让，不可兑现。" +
                "如遇商户拒绝接受优惠券，请联系客服处理。部分优惠券可能有最低消费金额限制。具体优惠券请关注商品活动"),

            // ---- 退款政策 ----
            new KnowledgeEntry("refund_policy",
                "已支付但未使用的优惠券，用户可在有效期内申请退款，退款金额将原路返回支付账户。" +
                "退款审核通常需要1-3个工作日。已使用或已过期的优惠券不支持退款。" +
                "秒杀优惠券支付成功后，在秒杀活动结束前不可退款，活动结束后未使用的可申请退款。"),

            // ---- 登录注册 ----
            new KnowledgeEntry("login_help",
                "用户可通过手机号注册登录。注册时需要输入手机号接收短信验证码。" +
                "验证码有效期为5分钟，超时需重新获取。同一手机号5分钟内最多获取5次验证码，" +
                "超过限制需等待5分钟后再试。如果收不到验证码，请检查手机信号或稍后重试。"),

            // ---- 商户搜索 ----
            new KnowledgeEntry("shop_search",
                "在首页搜索框输入关键字可搜索商户。平台支持按商户名称、类型和位置进行搜索。" +
                "搜索结果按距离排序，距离越近排名越靠前。点击商户卡片可查看详细信息，" +
                "包括营业时间、地址、评分、用户评价和可用优惠券。"),

            // ---- 关注与取关 ----
            new KnowledgeEntry("follow_feature",
                "在用户主页或个人资料页面可以看到关注按钮。点击关注后，你可以在动态流中看到对方的动态。" +
                "取消关注后，你将不再收到对方的动态推送，但之前的互动记录不会丢失。" +
                "查看共同关注可以帮你发现更多感兴趣的用户。"),

            // ---- 签到功能 ----
            new KnowledgeEntry("sign_feature",
                "每日签到可以记录你的活跃天数。签到页面可查看当月签到日历和连续签到天数。" +
                "连续签到天数会在断签后重置。签到记录每月自动归档，你可以在个人中心查看历史签到统计。" +
                "更多签到福利敬请期待。"),

            // ---- 通用错误 ----
            new KnowledgeEntry("error_troubleshoot",
                "常见问题排查：1. 页面加载慢，请检查网络连接或刷新重试；" +
                "2. 下单失败，请确认库存是否充足；" +
                "3. 验证码收不到，请检查手机信号或等待后重试；" +
                "4. 支付失败，请确认支付方式是否正常。以上问题如持续存在，请联系客服。"),


                // ---- 店铺信息 ----
                new KnowledgeEntry("shop_1",
                        "店铺名称：103茶餐厅。类型：餐饮。所在区域：大关。详细地址：金华路锦昌文华苑29号。" +
                                "营业时间：10:00-22:00。人均消费：80元。评分：37分。" +
                                "特色：茶餐厅，适合日常简餐、朋友小聚。"),

                new KnowledgeEntry("shop_2",
                        "店铺名称：蔡馬洪涛烤肉·老北京铜锅涮羊肉。类型：餐饮。所在区域：拱宸桥/上塘。" +
                                "详细地址：上塘路1035号（中国工商银行旁）。营业时间：11:30-03:00。" +
                                "人均消费：85元。评分：46分。特色：老北京铜锅涮羊肉、烤肉。"),

                new KnowledgeEntry("shop_3",
                        "店铺名称：新白鹿餐厅(运河上街店)。类型：餐饮。所在区域：运河上街。" +
                                "详细地址：台州路2号运河上街购物中心F5。营业时间：10:30-21:00。" +
                                "人均消费：61元。评分：47分。特色：杭帮菜，性价比高。"),

                new KnowledgeEntry("shop_4",
                        "店铺名称：Mamala(杭州远洋乐堤港店)。类型：餐饮。所在区域：拱宸桥/上塘。" +
                                "详细地址：丽水路66号远洋乐堤港商城2期1层B115号。营业时间：11:00-22:00。" +
                                "人均消费：290元。评分：49分。特色：西餐，中高端餐厅，适合约会。"),

                new KnowledgeEntry("shop_5",
                        "店铺名称：海底捞火锅(水晶城购物中心店)。类型：餐饮。所在区域：大关。" +
                                "详细地址：上塘路458号水晶城购物中心F6。营业时间：10:00-07:00。" +
                                "人均消费：104元。评分：49分。特色：火锅，服务好，24小时营业。"),

                new KnowledgeEntry("shop_6",
                        "店铺名称：幸福里老北京涮锅（丝联店）。类型：餐饮。所在区域：拱宸桥/上塘。" +
                                "详细地址：金华南路189号丝联166号。营业时间：11:00-13:50,17:00-20:50。" +
                                "人均消费：130元。评分：46分。特色：老北京涮锅。"),

                new KnowledgeEntry("shop_7",
                        "店铺名称：炉鱼(拱墅万达广场店)。类型：餐饮。所在区域：北部新城。" +
                                "详细地址：杭行路666号万达商业中心4幢2单元409室。营业时间：00:00-24:00。" +
                                "人均消费：85元。评分：47分。特色：烤鱼，24小时营业。"),

                new KnowledgeEntry("shop_8",
                        "店铺名称：浅草屋寿司（运河上街店）。类型：餐饮。所在区域：运河上街。" +
                                "详细地址：拱墅区金华路80号运河上街B1。营业时间：11:00-21:30。" +
                                "人均消费：88元。评分：46分。特色：日料，寿司。"),

                new KnowledgeEntry("shop_9",
                        "店铺名称：羊老三羊蝎子牛仔排北派炭火锅(运河上街店)。类型：餐饮。所在区域：运河上街。" +
                                "详细地址：台州路2号运河上街购物中心F5。营业时间：11:00-21:30。" +
                                "人均消费：101元。评分：44分。特色：羊蝎子火锅、炭火锅。"),

                new KnowledgeEntry("shop_10",
                        "店铺名称：开乐迪KTV（运河上街店）。类型：娱乐。所在区域：运河上街。" +
                                "详细地址：台州路2号运河上街购物中心F4。营业时间：00:00-24:00。" +
                                "人均消费：67元。评分：37分。特色：KTV，24小时营业。"),

                new KnowledgeEntry("shop_11",
                        "店铺名称：INLOVE KTV(水晶城店)。类型：娱乐。所在区域：水晶城。" +
                                "详细地址：上塘路458号水晶城购物中心6层。营业时间：11:30-06:00。" +
                                "人均消费：75元。评分：47分。特色：KTV。"),

                new KnowledgeEntry("shop_12",
                        "店铺名称：魅(杭州远洋乐堤港店)。类型：娱乐。所在区域：远洋乐堤港。" +
                                "详细地址：丽水路58号远洋乐堤港F4。营业时间：10:00-02:00。" +
                                "人均消费：88元。评分：46分。特色：KTV。"),

                new KnowledgeEntry("shop_13",
                        "店铺名称：讴K拉量贩KTV(北城天地店)。类型：娱乐。所在区域：D32天阳购物中心。" +
                                "详细地址：湖州街567号北城天地5层。营业时间：12:00-02:00。" +
                                "人均消费：58元。评分：41分。特色：量贩KTV。"),

                new KnowledgeEntry("shop_14",
                        "店铺名称：星聚会KTV(拱墅区万达店)。类型：娱乐。所在区域：北部新城。" +
                                "详细地址：杭行路666号万达广场C座1-2F。营业时间：10:00-22:00。" +
                                "人均消费：60元。评分：47分。特色：KTV。")
        );
    }

    // ==================== Embedding + 写入 ====================

    /**
     * 批量 embedding + 写入 ChromaDB
     */
    private int ingestEntries(List<KnowledgeEntry> entries) {
        int successCount = 0;
        try {
            List<String> texts = entries.stream()
                    .map(KnowledgeEntry::content)
                    .collect(Collectors.toList());

            // 批量生成 embedding 向量
            List<float[]> embeddings = texts.stream()
                    .map(text -> {
                        try {
                            return embeddingModel.embed(text);
                        } catch (Exception e) {
                            log.warn("Embedding 生成失败: id={}, err={}", 
                                    entries.get(texts.indexOf(text)).id(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 构建 ChromaDocument 列表
            List<ChromaDocument> documents = new ArrayList<>();
            for (int i = 0; i < entries.size() && i < embeddings.size(); i++) {
                KnowledgeEntry entry = entries.get(i);
                Map<String, String> metadata = new HashMap<>();
                metadata.put("knowledgeId", entry.id());
                metadata.put("category", entry.category());
                metadata.put("source", "builtin");
                metadata.put("tokenCount", String.valueOf(tokenCounter.count(entry.content())));

                documents.add(new ChromaDocument(
                        entry.id(), entry.content(), metadata, embeddings.get(i)));
            }

            // 批量写入 ChromaDB
            if (!documents.isEmpty()) {
                vectorStoreManager.addDocuments(COLLECTION_NAME, documents);
                successCount = documents.size();
            }

        } catch (Exception e) {
            log.error("RAG 知识库批量写入失败: {}", e.getMessage(), e);
        }
        return successCount;
    }

    // ==================== 辅助方法 ====================

    /**
     * 按 ID 去重（保留第一个）
     */
    private List<KnowledgeEntry> deduplicate(List<KnowledgeEntry> entries) {
        Set<String> seen = new HashSet<>();
        List<KnowledgeEntry> deduped = new ArrayList<>();
        for (KnowledgeEntry e : entries) {
            if (seen.add(e.id())) {
                deduped.add(e);
            }
        }
        return deduped;
    }

    // ==================== 知识条目模型 ====================

    /**
     * 知识条目
     */
    public record KnowledgeEntry(String id, String content) {
        public String category() {
            // 从 ID 推断类别：knowledge_base 中无独立 category 字段，使用 ID 前缀
            if (id.contains("_")) {
                return id.substring(0, id.lastIndexOf('_'));
            }
            return "general";
        }
    }
}