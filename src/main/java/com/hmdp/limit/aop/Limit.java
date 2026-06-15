package com.hmdp.limit.aop;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 限流注解
 * </p>
 * @author yang
 * @date 2023/8/9
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Limit {
    String key() default "";

    /**
     * 每秒钟最多少个请求，数据必须大于0
     **/
    int permitsPerSecond() default 10;

    /**
     * 请求等待时间 默认：秒
     **/
    int timeout() default 5;

    /**
     * 请求等待时间单位
     **/
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    String msg() default "系统繁忙,请稍后再试......";
}
