package com.echomind.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 共享 Redis 访问器
 * 访问主项目共享的 Redis 数据
 */
@Slf4j
@Component
public class SharedRedisAccessor {

    private final StringRedisTemplate stringRedisTemplate;

    public SharedRedisAccessor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 读取主项目的缓存商户信息
     */
    public String getCachedShop(Long shopId) {
        String key = "cache:shop:" + shopId;
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 检查秒杀库存（从主项目共享 Redis 读取）
     */
    public Integer getSeckillStock(Long voucherId) {
        String key = "seckill:stock:" + voucherId;
        String val = stringRedisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : 0;
    }

    public StringRedisTemplate getStringRedisTemplate() {
        return stringRedisTemplate;
    }
}
