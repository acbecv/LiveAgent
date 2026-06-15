package com.hmdp.cache.handler;

import com.alibaba.fastjson.JSON;
import com.hmdp.dto.CacheDeleteMessage;
import com.hmdp.entity.Shop;
import com.hmdp.rabbitmq.MQSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

import java.util.Collections;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * Canal监听Shop表变更处理器
 * 通过MQ异步删除缓存，实现最终一致性
 * 
 * 改造说明：
 * 1. 原ShopHandler直接操作缓存，现在改为发送MQ消息
 * 2. MQ消费者异步删除缓存，降低Canal处理延迟
 */
@Slf4j
@CanalTable(value = "tb_shop")
@Component
public class ShopCanalHandler implements EntryHandler<Shop> {

    @Autowired
    private MQSender mqSender;

    @Override
    public void insert(Shop shop) {
        log.info("Canal监听到Shop表INSERT操作, id: {}", shop.getId());
        // 新增数据时，不需要删除缓存（缓存中本来就没有）
        // 可以选择预热缓存，这里暂不处理
    }

    @Override
    public void update(Shop before, Shop after) {
        log.info("Canal监听到Shop表UPDATE操作, id: {}", after.getId());
        // 更新操作：发送缓存删除消息
        sendCacheDeleteMessage(after.getId(), "UPDATE", after);
    }

    @Override
    public void delete(Shop shop) {
        log.info("Canal监听到Shop表DELETE操作, id: {}", shop.getId());
        // 删除操作：发送缓存删除消息
        sendCacheDeleteMessage(shop.getId(), "DELETE", null);
    }

    /**
     * 发送缓存删除消息到MQ
     * @param shopId 店铺ID
     * @param operationType 操作类型
     * @param shop 店铺数据（用于重建缓存）
     */
    private void sendCacheDeleteMessage(Long shopId, String operationType, Shop shop) {
        String cacheKey = CACHE_SHOP_KEY + shopId;
        List<String> cacheKeys = Collections.singletonList(cacheKey);
        
        CacheDeleteMessage message = new CacheDeleteMessage();
        message.setTableName("tb_shop");
        message.setOperationType(operationType);
        message.setCacheKeys(cacheKeys);
        message.setDataId(shopId);
        
        if (shop != null) {
            message.setDataJson(JSON.toJSONString(shop));
        }
        
        mqSender.sendCacheDeleteMessage(message);
        log.info("已发送缓存删除消息到MQ, shopId: {}, key: {}", shopId, cacheKey);
    }
}
