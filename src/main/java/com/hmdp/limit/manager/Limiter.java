package com.hmdp.limit.manager;


import com.hmdp.limit.aop.Limit;

import java.util.concurrent.TimeUnit;


/**
 * <p>
 * 限流实体类
 * </p>
 * @author yang
 * @date 2023/8/9
 */
public class Limiter {

    private String key;

    /**
     * 每秒钟最多少个请求
     **/
    private Integer permitsPerSecond;

    /**
     * 请求等待时间
     **/
    private Integer timeout;

    /**
     * 请求等待时间单位
     **/
    private TimeUnit timeUnit;

    private final String msg;


    public Limiter(String key, Integer permitsPerSecond, Integer timeout, TimeUnit timeUnit,String msg) {
        this.key = key;
        this.permitsPerSecond = permitsPerSecond;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.msg = msg;
    }

    public static Limiter builder(Limit limit) {
        return new Limiter(limit.key(), limit.permitsPerSecond(), limit.timeout(), limit.timeUnit(), limit.msg());
    }

    public  Limiter key(String key){
        this.key = key;
        return this;
    }

    public Limiter permitsPerSecond(int permitsPerSecond){
        this.permitsPerSecond = permitsPerSecond;
        return this;
    }
    public Limiter timeout(int timeout){
        this.timeout = timeout;
        return this;
    }
    public Limiter timeUnit(TimeUnit timeUnit){
        this.timeUnit = timeUnit;
        return this;
    }

    public String getKey() {
        return key;
    }

    public Integer getPermitsPerSecond() {
        return permitsPerSecond;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public String getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        return "Limiter{" +
                "key='" + key + '\'' +
                ", permitsPerSecond=" + permitsPerSecond +
                ", timeout=" + timeout +
                ", timeUnit=" + timeUnit +
                ", msg='" + msg + '\'' +
                '}';
    }
}
