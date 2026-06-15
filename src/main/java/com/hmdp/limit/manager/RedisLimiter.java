package com.hmdp.limit.manager;


import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 使用Redis实现限流
 * </p>
 * @author yang
 * @date 2023/8/9
 */
public class RedisLimiter implements LimiterManager {

    private final StringRedisTemplate stringRedisTemplate;
    private DefaultRedisScript<Long> redisScript;

    private static final ThreadLocal<SimpleDateFormat> DF = new ThreadLocal<>();
    private static final String LIMIT_START_TIME = "limit:start:time:";
    private static final String LIMIT_LOCK_TIME = "limit:lock:time:";
    private static final String LIMIT_PREFIX = "limit:redis:";

    public RedisLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("rateLimiter.lua")));
    }

    @Override
    public boolean tryAccess(Limiter limiter) throws Exception {
        try {
            if (limiter != null) {
                //获取redis的key
                String key = limiter.getKey();
                if (ObjectUtils.isEmpty(key)) {
                    return false;
                }
                Integer perSecond = limiter.getPermitsPerSecond();
                Integer timeout = limiter.getTimeout();
                TimeUnit timeUnit = limiter.getTimeUnit();
                SimpleDateFormat format = DF.get();
                if (format == null) {
                    format = new SimpleDateFormat();
                }
                //获取锁定时间
                String isLock = stringRedisTemplate.opsForValue().get(LIMIT_LOCK_TIME + key);
                if (isLock != null) {
                    Date lockTime = format.parse(isLock);
                    long l1 = System.currentTimeMillis() - lockTime.getTime();
                    long l2 = timeUnit.toMillis(timeout);
                    //当带没有到过期时间时，直接拦截
                    if (l1 < l2) {
                        return false;
                    }
                }
                String startTimeKey = LIMIT_START_TIME + key;
                String start = stringRedisTemplate.opsForValue().get(startTimeKey);
                //当开始时间不存在时，说明近期没有访问过，直接返回
                if (start == null) {
                    String dateTime1 = format.format(new Date());
                    stringRedisTemplate.opsForValue().set(startTimeKey, dateTime1, timeout, timeUnit);
                    return true;
                }
                //当存在上次记录时间时
                List<String> keys = new ArrayList<>();
                keys.add(LIMIT_PREFIX + key);

                //统一时间单位，将过期时间转化为秒
                long seconds = timeUnit.toSeconds(timeout);
                Long count = stringRedisTemplate
                        .execute(this.redisScript,
                                keys,
                                String.valueOf(perSecond),
                                String.valueOf(seconds)
                        );

                if (count != null && count == 0) {
                    //获取当前时间为新的锁定时间
                    String currLockTime = format.format(new Date());
                    stringRedisTemplate.opsForValue().set(LIMIT_LOCK_TIME + key, currLockTime, timeout, timeUnit);

                    return false;
                }
            }
            return true;
        }finally {
            DF.remove();
        }

    }
}
