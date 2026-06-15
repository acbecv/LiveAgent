package com.echomind.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Benchmark 测试数据集
 */
public class BenchmarkData {

    public static List<IntentTestCase> intentTestCases() {
        List<IntentTestCase> list = new ArrayList<>();
        list.add(new IntentTestCase("附近有什么好吃的川菜馆", "shop_recommend"));
        list.add(new IntentTestCase("川味轩在哪里", "shop_query"));
        list.add(new IntentTestCase("帮我查一下蜀香园的电话", "shop_query"));
        list.add(new IntentTestCase("推荐几家评分高的火锅店", "shop_recommend"));
        list.add(new IntentTestCase("这家店和隔壁那家哪个评分高", "shop_compare"));
        list.add(new IntentTestCase("附近有KTV吗", "shop_recommend"));
        list.add(new IntentTestCase("我想去吃烤肉有没有推荐的", "shop_recommend"));
        list.add(new IntentTestCase("对比一下川味轩和蜀香园", "shop_compare"));
        list.add(new IntentTestCase("有没有可以用的优惠券", "voucher_query"));
        list.add(new IntentTestCase("这个优惠券怎么用", "voucher_use"));
        list.add(new IntentTestCase("我领了一张券去哪看", "voucher_query"));
        list.add(new IntentTestCase("优惠券快过期了提醒我一下", "voucher_query"));
        list.add(new IntentTestCase("满100减20的券还有吗", "voucher_query"));
        list.add(new IntentTestCase("怎么用这个秒杀券", "voucher_use"));
        list.add(new IntentTestCase("今天有什么秒杀活动", "seckill_query"));
        list.add(new IntentTestCase("秒杀什么时候开始", "seckill_query"));
        list.add(new IntentTestCase("刚才秒杀没抢到怎么办", "seckill_query"));
        list.add(new IntentTestCase("我的个人信息在哪看", "user_profile"));
        list.add(new IntentTestCase("怎么修改头像", "user_profile"));
        list.add(new IntentTestCase("关注了这个商家怎么取消", "user_follow"));
        list.add(new IntentTestCase("今天签到有什么奖励", "user_signin"));
        list.add(new IntentTestCase("怎么退款", "general_help"));
        list.add(new IntentTestCase("这个平台是干什么的", "general_help"));
        list.add(new IntentTestCase("忘记密码了怎么办", "general_help"));
        list.add(new IntentTestCase("怎么联系客服", "general_help"));
        list.add(new IntentTestCase("如何注销账号", "general_help"));
        list.add(new IntentTestCase("今天天气真好", "general_chat"));
        list.add(new IntentTestCase("你好呀", "general_chat"));
        list.add(new IntentTestCase("谢谢你的帮助", "general_chat"));
        list.add(new IntentTestCase("这个功能太难用了我要投诉", "complaint"));
        list.add(new IntentTestCase("商家态度太差了", "complaint"));
        return list;
    }

    public static List<RagTestCase> ragTestCases() {
        List<RagTestCase> list = new ArrayList<>();
        list.add(new RagTestCase("怎么退货", Arrays.asList("refund_policy", "platform_intro"), "语义等价查询"));
        list.add(new RagTestCase("申请售后", Arrays.asList("refund_policy", "platform_intro"), "同义词不重叠"));
        list.add(new RagTestCase("买了东西不想要了", Arrays.asList("refund_policy"), "口语化表述"));
        list.add(new RagTestCase("优惠券怎么用", Arrays.asList("voucher_usage"), "关键词精确匹配"));
        list.add(new RagTestCase("秒杀活动规则", Arrays.asList("seckill_rule"), "关键词精确匹配"));
        list.add(new RagTestCase("登录注册方法", Arrays.asList("login_help"), "关键词精确匹配"));
        list.add(new RagTestCase("抢到的优惠券过期了能退钱吗", Arrays.asList("refund_policy", "voucher_usage", "seckill_rule"), "跨领域混合"));
        list.add(new RagTestCase("点赞后怎么取消", Arrays.asList("platform_intro"), "隐含操作"));
        list.add(new RagTestCase("怎么修改绑定手机号", Arrays.asList("login_help", "user_info_query"), "复合意图"));
        list.add(new RagTestCase("平台有什么功能和特色", Arrays.asList("platform_intro"), "概括性问题"));
        list.add(new RagTestCase("客服电话多少", Arrays.asList("general_help"), "简单查询"));
        list.add(new RagTestCase("隐私怎么保护", Arrays.asList("privacy_policy"), "隐私查询"));
        list.add(new RagTestCase("商户不想用优惠券怎么办", Arrays.asList("voucher_usage", "general_help"), "商户拒绝场景"));
        list.add(new RagTestCase("刚注册怎么开始使用", Arrays.asList("login_help", "platform_intro"), "新用户引导"));
        list.add(new RagTestCase("好友能看到我的订单吗", Arrays.asList("privacy_policy"), "隐私关联"));
        return list;
    }

    public static List<MemoryTestCase> memoryTestCases() {
        List<MemoryTestCase> list = new ArrayList<>();
        list.add(new MemoryTestCase("长对话压缩",
                Arrays.asList("你好，我想找一家川菜馆", "有什么推荐的吗", "川味轩评分怎么样", "在哪里",
                        "有优惠券吗", "怎么领", "能用几张", "有效期多久", "可以和其他券一起用吗", "那我先领一张，谢谢"),
                10, 3));
        list.add(new MemoryTestCase("跨会话记忆",
                Arrays.asList("上次我领了川味轩的券", "还没用，现在想去用", "帮我导航到川味轩"), 3, 2));
        list.add(new MemoryTestCase("用户画像提取",
                Arrays.asList("我喜欢吃辣的", "川菜湘菜都可以", "不要太远的", "人均100以内", "评分4.5以上"), 5, 2));
        return list;
    }

    // ==================== 测试用例类型（Java 8 兼容） ====================

    public static class IntentTestCase {
        public final String query;
        public final String expectedCategory;
        public IntentTestCase(String query, String expectedCategory) {
            this.query = query; this.expectedCategory = expectedCategory;
        }
    }

    public static class RagTestCase {
        public final String query;
        public final List<String> expectedDocIds;
        public final String description;
        public RagTestCase(String query, List<String> expectedDocIds, String description) {
            this.query = query; this.expectedDocIds = expectedDocIds; this.description = description;
        }
    }

    public static class MemoryTestCase {
        public final String name;
        public final List<String> dialogRounds;
        public final int totalRounds;
        public final int expectedCompressedRounds;
        public MemoryTestCase(String name, List<String> dialogRounds, int totalRounds, int expectedCompressedRounds) {
            this.name = name; this.dialogRounds = dialogRounds;
            this.totalRounds = totalRounds; this.expectedCompressedRounds = expectedCompressedRounds;
        }
    }
}