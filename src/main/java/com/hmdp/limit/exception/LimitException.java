package com.hmdp.limit.exception;

import lombok.Getter;

/**
 * <p>
 * 异常处理
 * </p>
 * @author yang
 * @date 2023/8/9
 */
@Getter
public final class LimitException extends RuntimeException {

    /**
     * 返回信息
     */
    private final String message;

    public LimitException(String message) {
        this.message = message;
    }
}
