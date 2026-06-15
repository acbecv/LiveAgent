package com.echomind.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 主项目（hm-dianping）HTTP 客户端
 * 通过 REST API 调用主项目获取商户/用户/优惠券数据
 */
@Slf4j
@Component
public class DianpingClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${echomind.dianping-base-url}")
    private String baseUrl;

    public DianpingClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 按名称关键字搜索商户
     * GET /shop/of/name?name=xxx&current=1
     * 注意：keyword 应由 LLM 从用户提问中提取，传入具体商户名或类别关键词
     */
    public List<Map<String, Object>> queryShopByName(String keyword, int page) {
        try {
            String url = baseUrl + "/shop/of/name?name=" + keyword + "&current=" + page;
            log.debug("调用主项目API: GET {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return parseDataList(json);
        } catch (Exception e) {
            log.error("商户查询失败: keyword={}, error={}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * 按ID查询商户详情
     * GET /shop/{id}
     */
    public Map<String, Object> queryShopById(Long shopId) {
        try {
            String url = baseUrl + "/shop/" + shopId;
            log.debug("调用主项目API: GET {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return parseDataObject(json);
        } catch (Exception e) {
            log.error("商户查询失败: shopId={}, error={}", shopId, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 按商铺类型分页查询
     * GET /shop/of/type?typeId=xxx&current=1
     */
    public List<Map<String, Object>> queryShopByType(Integer typeId, int page) {
        try {
            String url = baseUrl + "/shop/of/type?typeId=" + typeId + "&current=" + page;
            log.debug("调用主项目API: GET {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return parseDataList(json);
        } catch (Exception e) {
            log.error("按类型查询商户失败: typeId={}, error={}", typeId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 按ID查询用户信息
     * GET /user/{id}
     */
    public Map<String, Object> queryUserById(Long userId) {
        try {
            String url = baseUrl + "/user/" + userId;
            log.debug("调用主项目API: GET {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return parseDataObject(json);
        } catch (Exception e) {
            log.error("用户查询失败: userId={}, error={}", userId, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 查询指定店铺的优惠券列表
     * GET /voucher/list/{shopId}
     */
    public List<Map<String, Object>> queryVoucherOfShop(Long shopId) {
        try {
            String url = baseUrl + "/voucher/list/" + shopId;
            log.debug("调用主项目API: GET {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return parseDataList(json);
        } catch (Exception e) {
            log.error("优惠券查询失败: shopId={}, error={}", shopId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 查询用户优惠券订单
     * GET /voucher-order/user/{userId}
     */
    public Map<String, Object> queryOrdersByUser(Long userId) {
        try {
            String url = baseUrl + "/voucher-order/user/" + userId;
            log.debug("调用主项目API: GET {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return parseDataListAsMap(json);
        } catch (Exception e) {
            log.error("订单查询失败: userId={}, error={}", userId, e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 按ID查询优惠券详情
     * GET /voucher/{id}
     */
    public Map<String, Object> queryVoucherById(Long voucherId) {
        try {
            String url = baseUrl + "/voucher/" + voucherId;
            log.debug("调用主项目API: GET {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return parseDataObject(json);
        } catch (Exception e) {
            log.error("优惠券详情查询失败: voucherId={}, error={}", voucherId, e.getMessage());
            return Map.of();
        }
    }

    // ==================== 响应解析 ====================

    /**
     * 解析主项目 Result 返回的 data 字段（数组）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDataList(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.path("success").asBoolean()) {
                log.warn("主项目返回失败: {}", root.path("errorMsg").asText());
                return List.of();
            }
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : data) {
                result.add(objectMapper.convertValue(item, Map.class));
            }
            return result;
        } catch (Exception e) {
            log.error("解析主项目响应失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析主项目 Result 返回的 data 字段（单个对象）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDataObject(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.path("success").asBoolean()) {
                log.warn("主项目返回失败: {}", root.path("errorMsg").asText());
                return Map.of();
            }
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                return Map.of();
            }
            return objectMapper.convertValue(data, Map.class);
        } catch (Exception e) {
            log.error("解析主项目响应失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析为 {success, message/data} Map（用于订单操作等非查询场景）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDataListAsMap(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            boolean success = root.path("success").asBoolean();
            JsonNode data = root.get("data");
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", success);
            if (!success) {
                result.put("message", root.path("errorMsg").asText("操作失败"));
            } else if (data != null && !data.isNull()) {
                result.put("data", objectMapper.convertValue(data, List.class));
            }
            return result;
        } catch (Exception e) {
            log.error("解析主项目响应失败: {}", e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}