package com.hmdp.limit.manager;

/**
 * <p>
 * 限流管理器
 * </p>
 * @author yang
 * @date 2023/8/9
 */
public interface LimiterManager {

    /**
     * 能否访问
     * @param limiter limit
     * @return {@link LimiterManager}
     */
    boolean tryAccess(Limiter limiter) throws Exception;
}
