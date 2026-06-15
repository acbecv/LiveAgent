package com.hmdp.limit.config;


import com.hmdp.limit.manager.GuavaLimiter;
import com.hmdp.limit.manager.LimiterManager;
import com.hmdp.limit.manager.RedisLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * <p>
 * 限流类型选择
 * </p>
 * @author yang
 * @date 2023/8/9
 */
@Configuration
public class LimiterConfigure {

    @Bean
    @ConditionalOnProperty(name = "com.yang.limit.type", havingValue = "local")
    public LimiterManager guavaLimiter() {
        return new GuavaLimiter();
    }


    @Bean
    @ConditionalOnProperty(name = "com.yang.limit.type", havingValue = "redis")
    public LimiterManager redisLimiter(StringRedisTemplate stringRedisTemplate) {
        return new RedisLimiter(stringRedisTemplate);
    }

}
