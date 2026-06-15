package com.echomind.agent;

/**
 * 线程级用户上下文，用于在 Function Calling 工具方法中获取当前 userId。
 * 因为 Spring AI @Tool 方法的参数由 LLM 决定，userId 无法通过参数传递，
 * 故通过 ThreadLocal 在请求入口注入，工具方法内读取。
 */
public class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}