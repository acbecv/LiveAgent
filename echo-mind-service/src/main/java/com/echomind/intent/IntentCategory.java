package com.echomind.intent;

import lombok.Getter;

/**
 * 意图类别枚举 - 覆盖商户、优惠券、用户、通用客服四大领域
 */
@Getter
public enum IntentCategory {

    // 商户相关
    SHOP_QUERY("shop_query", "商户查询"),
    SHOP_RECOMMEND("shop_recommend", "商户推荐"),
    SHOP_COMPARE("shop_compare", "商户对比"),

    // 优惠券相关
    VOUCHER_QUERY("voucher_query", "优惠券查询"),
    VOUCHER_USE("voucher_use", "优惠券使用"),
    VOUCHER_ORDER_QUERY("voucher_order_query", "用户券单查询"),
    SECKILL_QUERY("seckill_query", "秒杀查询"),

    // 用户相关
    USER_PROFILE("user_profile", "用户信息"),
    USER_FOLLOW("user_follow", "关注操作"),
    USER_SIGN_IN("user_signin", "签到"),

    // 通用客服
    GENERAL_HELP("general_help", "帮助指引"),
    GENERAL_CHAT("general_chat", "闲聊"),
    COMPLAINT("complaint", "投诉建议"),

    // 系统级
    UNKNOWN("unknown", "未知意图"),
    FALLBACK_HUMAN("fallback_human", "转人工");

    private final String code;
    private final String name;

    IntentCategory(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static IntentCategory fromCode(String code) {
        for (IntentCategory c : values()) {
            if (c.code.equals(code)) return c;
        }
        return UNKNOWN;
    }
}
