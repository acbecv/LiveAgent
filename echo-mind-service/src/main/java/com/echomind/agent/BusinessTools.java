package com.echomind.agent;

import com.echomind.integration.DianpingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 业务工具集 — Spring AI Function Calling 方式
 *
 * 通过 @Tool 注解将方法暴露给 LLM，LLM 自主决定调用哪个方法及参数。
 * 框架自动处理：LLM 输出函数调用 → 执行 Java 方法 → 反馈结果 → 生成最终回复。
 */
@Slf4j
@Component
public class BusinessTools {

    private final DianpingClient dianpingClient;

    public BusinessTools(DianpingClient dianpingClient) {
        this.dianpingClient = dianpingClient;
    }

    @Tool(description = "按名称关键字搜索商户。当用户询问某个商户、餐厅、店铺的信息时调用，例如'川菜馆'、'火锅'、'蜀香园'、'KTV'等。参数keyword为商户名称或类别关键词")
    public String queryShopByName(@ToolParam(description = "商户名称或类别关键词，如'川菜'、'火锅'、'蜀香园'") String keyword) {
        log.info("Tool调用: queryShopByName, keyword={}", keyword);
        List<Map<String, Object>> shops = dianpingClient.queryShopByName(keyword, 1);
        if (shops.isEmpty()) return "未找到与'" + keyword + "'相关的商户";
        StringBuilder sb = new StringBuilder("共找到 " + shops.size() + " 个商户:\n");
        for (int i = 0; i < Math.min(shops.size(), 10); i++) {
            Map<String, Object> s = shops.get(i);
            sb.append(i + 1).append(". ")
              .append(s.getOrDefault("name", "未知"))
              .append(" | ID:").append(s.getOrDefault("id", "?"))
              .append(" | 评分:").append(s.getOrDefault("score", "?"))
              .append(" | 人均消费:¥").append(s.getOrDefault("avgPrice", "?"))
                    .append(" | 地址:").append(s.getOrDefault("address", "未知"))
                    .append(" | 营业时间:").append(s.getOrDefault("openHours", "未知"))
                    .append(" | 描述:").append(s.getOrDefault("shopDesc", "未知"))
              .append("\n");
        }
        return sb.toString();
    }

    @Tool(description = "按商户ID查询商户详情（名称、地址、评分、均价、营业时间）")
    public String queryShopById(@ToolParam(description = "商户ID") Long shopId) {
        log.info("Tool调用: queryShopById, shopId={}", shopId);
        Map<String, Object> shop = dianpingClient.queryShopById(shopId);
        if (shop.isEmpty()) return "未找到ID为" + shopId + "的商户";
        return String.format("商户: %s | 评分: %s | 均价: ¥%s | 地址: %s | 营业时间: %s",
                shop.getOrDefault("name", "未知"),
                shop.getOrDefault("score", "?"),
                shop.getOrDefault("avgPrice", "?"),
                shop.getOrDefault("address", "未知"),
                shop.getOrDefault("hours", "未知"));
    }

    @Tool(description = "按商铺类型ID分页查询商户列表")
    public String queryShopByType(@ToolParam(description = "商铺类型ID") Integer typeId) {
        log.info("Tool调用: queryShopByType, typeId={}", typeId);
        List<Map<String, Object>> shops = dianpingClient.queryShopByType(typeId, 1);
        if (shops.isEmpty()) return "未找到类型ID为" + typeId + "的商户";
        StringBuilder sb = new StringBuilder("共找到 " + shops.size() + " 个商户:\n");
        for (int i = 0; i < Math.min(shops.size(), 10); i++) {
            Map<String, Object> s = shops.get(i);
            sb.append(i + 1).append(". ")
              .append(s.getOrDefault("name", "未知"))
              .append(" | ID:").append(s.getOrDefault("id", "?"))
              .append(" | 评分:").append(s.getOrDefault("score", "?"))
              .append(" | 均价:¥").append(s.getOrDefault("avgPrice", "?"))
              .append("\n");
        }
        return sb.toString();
    }

    @Tool(description = "查询指定店铺当前可用的优惠券/团购券列表")
    public String queryVoucherByShop(@ToolParam(description = "商铺ID") Long shopId) {
        log.info("Tool调用: queryVoucherByShop, shopId={}", shopId);
        List<Map<String, Object>> vouchers = dianpingClient.queryVoucherOfShop(shopId);
        if (vouchers.isEmpty()) return "该店铺暂无可用优惠券";
        StringBuilder sb = new StringBuilder("该店铺优惠券:\n");
        for (Map<String, Object> v : vouchers) {
            sb.append("- ").append(v.getOrDefault("title", "未命名"))
              .append(" | 售价:¥").append(v.getOrDefault("payValue", "?"))
              .append(" | 库存:").append(v.getOrDefault("stock", 0))
              .append("\n");
        }
        return sb.toString();
    }

    @Tool(description = "查询当前用户已领取/拥有的优惠券订单。当用户问'我的优惠券'、'我有什么券'、'我的卡券'时调用")
    public String queryUserVouchers() {
        Long userId = UserContext.getUserId();
        log.info("Tool调用: queryUserVouchers, userId={}", userId);
        if (userId == null) return "无法获取当前用户信息";

        Map<String, Object> result = dianpingClient.queryOrdersByUser(userId);
        if (result == null || Boolean.FALSE.equals(result.get("success"))) {
            return result != null
                    ? (String) result.getOrDefault("message", "查询失败")
                    : "查询失败";
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("data");
        if (list == null || list.isEmpty()) return "您当前没有可用的优惠券";

        StringBuilder sb = new StringBuilder("您共有 " + list.size() + " 张优惠券:\n");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> o = list.get(i);
            Object voucherIdObj = o.get("voucherId");
            Long voucherId = voucherIdObj instanceof Number
                    ? ((Number) voucherIdObj).longValue()
                    : Long.parseLong(voucherIdObj.toString());

            // 查询优惠券详情
            Map<String, Object> voucherDetail = dianpingClient.queryVoucherById(voucherId);

            sb.append(i + 1).append(". ");
            if (!voucherDetail.isEmpty()) {
                sb.append("【").append(voucherDetail.getOrDefault("title", "未命名")).append("】")
                  .append(" | 售价:¥").append(voucherDetail.getOrDefault("payValue", "?"))
                  .append(" | 实际价值:¥").append(voucherDetail.getOrDefault("actualValue", "?"))
                  .append(" | 类型:").append(voucherDetail.getOrDefault("type", "?"))
                  .append(" | 库存:").append(voucherDetail.getOrDefault("stock", "?"))
                  .append(" | 状态:").append(o.getOrDefault("status", "?"))
                  .append(" | 订单ID:").append(o.getOrDefault("id", "?"));
            } else {
                sb.append("订单ID:").append(o.getOrDefault("id", "?"))
                  .append(" | 券ID:").append(voucherId)
                  .append(" | 状态:").append(o.getOrDefault("status", "?"))
                  .append(" (详情查询失败)");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}