package com.echomind.intent;

import com.echomind.dto.IntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Path 3: Pattern 关键词兜底路径
 * 使用正则/关键词规则匹配，确定性高但覆盖有限
 */
@Slf4j
@Component
public class PatternIntentRecognizer implements IntentRecognizer {

    private static final List<PatternRule> RULES = List.of(
            new PatternRule("shop_query", "商户查询",
                    List.of(".*(商户|店铺|商家|门店).*(信息|地址|电话|评分|价格|营业|位置).*",
                            ".*(查|看|找|搜).*(商户|店铺|商家|店).*",
                            ".*(有没有|哪里).*(店|商户|商家).*")),

            new PatternRule("shop_recommend", "商户推荐",
                    List.of(".*(推荐|介绍|有什么好).*(店|餐厅|馆|商家|商户).*",
                            ".*(附近|周边|附近有).*(店|餐厅|馆|馆子).*",
                            ".*(好吃|好玩|热门|必去).*")),

            new PatternRule("shop_compare", "商户对比",
                    List.of(".*(对比|比较|哪个好|谁更好|vs|VS).*(店|商户|商家).*",
                            ".*(和|与|跟).*(哪个).*(好|合适).*")),

            new PatternRule("voucher_query", "优惠券查询",
                    List.of(".*(优惠券|优惠|券|代金券|折扣).*(查|看|有|多少|哪些).*",
                            ".*(有什么|有).*(优惠|券|优惠券).*",
                            ".*(领|领取|抢).*(券|优惠券).*")),

            new PatternRule("voucher_use", "优惠券使用",
                    List.of(".*(怎么|如何).*(用|使用|用券).*(优惠券|券).*",
                            ".*(满减|规则|条件).*",
                            ".*(优惠券|券).*(规则|条件|限制).*")),

            new PatternRule("voucher_order_query", "用户券单查询",
                    List.of(".*(我|我的|本人).*(优惠券|券).*(订单|有|已|已领|持有).*",
                            ".*(查|看).*(我|我的).*(优惠券|券).*",
                            ".*(有哪些|拥有).*(优惠券|券).*",
                            ".*(我的|已领|领取了).*(券|优惠券).*")),

            new PatternRule("seckill_query", "秒杀查询",
                    List.of(".*(秒杀|秒|抢购).*(活动|信息|时间|开始).*",
                            ".*(什么时间|几点).*(秒杀|抢购).*",
                            ".*(库存|还有).*(吗|么).*")),

            new PatternRule("user_profile", "用户信息",
                    List.of(".*(我|我的|本人).*(信息|资料|账号|积分|等级|余额).*",
                            ".*(查|看).*(信息|资料|账号).*")),

            new PatternRule("user_follow", "关注操作",
                    List.of(".*(关注|取关|取消关注|拉黑|屏蔽).*",
                            ".*(怎么|如何).*(关注|取关).*")),

            new PatternRule("user_signin", "签到",
                    List.of(".*(签到|打卡|连续签到).*",
                            ".*(怎么|如何).*(签到|打卡).*")),

            new PatternRule("general_help", "帮助指引",
                    List.of(".*(帮助|怎么用|如何使用|操作指南|教程|说明).*",
                            ".*(功能|客服).*(有|能|什么|哪些).*")),

            new PatternRule("general_chat", "闲聊",
                    List.of(".*(你好|hello|hi|嗨|在吗|在么).*",
                            ".*(你是谁|你叫什么|你是什么).*",
                            ".*(天气|你好呀|今天|心情).*")),

            new PatternRule("complaint", "投诉建议",
                    List.of(".*(投诉|举报|建议|意见|差评|不满意).*",
                            ".*(太差|垃圾|不行|很烂|糟糕).*",
                            ".*(要求|必须).*(退|赔|处理|解决).*"))
    );

    @Override
    public IntentResult recognize(String userId, String message, String sessionId) {
        if (message == null || message.isBlank()) {
            return new IntentResult("unknown", "未知意图", 0.5, "pattern");
        }

        String msg = message.trim().toLowerCase();

        // 按优先级匹配规则
        for (PatternRule rule : RULES) {
            for (String regex : rule.patterns) {
                if (Pattern.matches(regex, msg)) {
                    log.debug("Pattern匹配成功: {} -> {}", regex, rule.code);
                    return new IntentResult(rule.code, rule.name, 0.9, "pattern");
                }
            }
        }

        return new IntentResult("unknown", "未知意图", 0.3, "pattern");
    }

    private record PatternRule(String code, String name, List<String> patterns) {}
}
