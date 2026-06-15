package com.hmdp.cache.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * Canal监听Shop表变更处理器 - 直接操作缓存版本
 * 
 * 【重要说明】
 * 本处理器已被改造为通过MQ异步删除缓存，新实现见 {@link ShopCanalHandler}
 * 
 * 改造原因：
 * 1. 直接操作缓存可能导致Canal处理延迟，影响数据同步实时性
 * 2. 通过MQ异步解耦，提高系统可用性
 * 3. 支持延迟双删策略，解决数据库与缓存不一致问题
 * 
 * 【使用方式】
 * 1. 默认使用：保持当前状态，不启用本Handler（已注释@CanalTable）
 * 2. 如需切换回直接操作缓存模式：
 *    - 取消下面的@CanalTable注释
 *    - 注释掉ShopCanalHandler中的@CanalTable
 * 
 * 【两种方案对比】
 * 方案一（当前默认）：MQ异步删除
 *   - 优点：解耦、支持延迟双删、Canal处理快
 *   - 缺点：有短暂延迟（毫秒级）
 * 
 * 方案二（本类）：直接操作缓存
 *   - 优点：实时性高
 *   - 缺点：Canal处理慢，缓存操作失败会影响数据同步
 */

// @CanalTable(value = "tb_shop")  // 已注释，使用ShopCanalHandler替代
@Component
public class ShopHandler implements EntryHandler<Shop>{


    @Autowired
    private ShopRedisHandler redisHandler;

    @Resource
    private Cache<String, Object> shopCache;

    @Override
    public void insert(Shop shop) {
        // 写数据到JVM进程缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        shopCache.put(key , shop);
        // 写数据到redis
        redisHandler.saveShop(shop);
    }

    @Override
    public void update(Shop before, Shop after) {
        // 写数据到JVM进程缓存
        System.out.println("++++++++++=");
        String key = CACHE_SHOP_KEY + after.getId();
        shopCache.put(key, after);
        // 写数据到redis
        redisHandler.saveShop(after);
    }

    @Override
    public void delete(Shop shop) {
        // 删除数据到JVM进程缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        shopCache.invalidate(key);
        // 删除数据到redis
        redisHandler.deleteShopById(shop.getId());
    }
}
