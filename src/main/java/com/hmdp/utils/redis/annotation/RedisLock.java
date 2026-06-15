package com.hmdp.utils.redis.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 * 基于Redis实现分布式锁
 *
 * 使用示例：
 * @RedisLock(key = "lock:order:", waitTime = 10, leaseTime = 30)
 * public Result createOrder(Long orderId) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisLock {

    /**
     * 锁key前缀
     * 完整key = prefix + key + 参数值（可选）
     */
    String key() default "lock:";

    /**
     * 等待获取锁的最大时间（秒）
     * 默认10秒，-1表示不等待立即返回
     */
    long waitTime() default 10;

    /**
     * 锁自动释放时间（秒）
     * 默认30秒，防止死锁
     */
    long leaseTime() default 30;

    /**
     * 时间单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败提示信息
     */
    String message() default "系统繁忙，请稍后重试";

    /**
     * 是否基于参数生成锁key
     * true：使用方法参数生成key
     * false：只使用key前缀
     */
    boolean useArgs() default true;

    /**
     * 指定使用哪个参数生成key（参数索引，从0开始）
     * -1表示使用所有参数
     */
    int argIndex() default 0;
}
