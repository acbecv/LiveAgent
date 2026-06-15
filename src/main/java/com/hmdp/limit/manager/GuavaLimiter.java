package com.hmdp.limit.manager;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * <p>
 * 基于guava的令牌桶实现限流
 * </p>
 * @author yang
 * @date 2023/8/9
 */
@Slf4j
public class GuavaLimiter implements LimiterManager{

    /**
     * 不同的接口，不同的流量控制
     * map的key为 Limiter.key
     */
    private final Map<String, RateLimiter> GuavaLimitMap = Maps.newConcurrentMap();

    @Override
    public boolean tryAccess(Limiter limiter) {

        if (limiter != null) {
            //key作用：不同的接口，不同的流量控制
            String key = limiter.getKey();
            RateLimiter rateLimiter;
            //验证缓存是否有命中key
            if (!GuavaLimitMap.containsKey(key)) {
                // 创建令牌桶
                rateLimiter = RateLimiter.create(limiter.getPermitsPerSecond());
                GuavaLimitMap.put(key, rateLimiter);
                log.info("新建了令牌桶={}，容量={}",key,limiter.getPermitsPerSecond());
            }
            rateLimiter = GuavaLimitMap.get(key);
            // 拿令牌
            boolean acquire = rateLimiter.tryAcquire(limiter.getTimeout(), limiter.getTimeUnit());

            // 拿不到命令，直接返回异常提示
            if (!acquire) {
                log.debug("令牌桶={}，获取令牌失败",key);
                return false;
            }
        }
        return true;
    }
}
