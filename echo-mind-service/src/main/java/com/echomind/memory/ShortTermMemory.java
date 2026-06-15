package com.echomind.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Level 1: 短期记忆 (Short-Term Memory)
 *
 * 存储: Redis List (key = echomind:stm:{sessionId})
 * 内容: 当前会话最近 N 轮原始消息（角色+内容），N 默认 20
 * 用途: 亚毫秒级上下文读取，Prompt 直接注入
 * TTL: 24 小时自动过期
 */
@Slf4j
@Component
public class ShortTermMemory {

    private static final String KEY_PREFIX = "echomind:stm:";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redis;
    private final MemoryConfig config;

    public ShortTermMemory(StringRedisTemplate redis, MemoryConfig config) {
        this.redis = redis;
        this.config = config;
    }

    /**
     * 添加一条消息到短期记忆
     */
    public void addMessage(String sessionId, String role, String content) {
        String key = KEY_PREFIX + sessionId;
        String entry = role + "::" + content;

        redis.opsForList().rightPush(key, entry);
        redis.expire(key, TTL_HOURS, TimeUnit.HOURS);

        // 裁剪超过上限的旧消息，保留最近 N 条
        int max = config.getShortTermMax();
        Long size = redis.opsForList().size(key);
        if (size != null && size > max) {
            redis.opsForList().trim(key, size - max, -1);
        }

        log.debug("STM 写入: session={}, role={}, size={}", sessionId, role,
                redis.opsForList().size(key));
    }

    /**
     * 获取当前会话的短期记忆消息列表
     */
    public List<String> getRecentMessages(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        return redis.opsForList().range(key, -config.getShortTermMax(), -1);
    }

    /**
     * 获取消息总数
     */
    public Long getMessageCount(String sessionId) {
        return redis.opsForList().size(KEY_PREFIX + sessionId);
    }

    /**
     * 转换为 LLM Prompt 可用的上下文文本
     */
    public String getContextString(String sessionId) {
        List<String> messages = getRecentMessages(sessionId);
        if (messages == null || messages.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n--- 近期对话历史 ---\n");
        for (String msg : messages) {
            String[] parts = msg.split("::", 2);
            if (parts.length == 2) {
                String role = "user".equals(parts[0]) ? "用户" : "助手";
                sb.append(role).append(": ").append(parts[1]).append("\n");
            }
        }
        sb.append("---\n");
        return sb.toString();
    }

    /**
     * 清除短期记忆
     */
    public void clear(String sessionId) {
        redis.delete(KEY_PREFIX + sessionId);
    }

    /**
     * 更新会话最后活跃时间（用于空闲检测）
     */
    public void touch(String sessionId) {
        String key = KEY_PREFIX + sessionId + ":touch";
        redis.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 获取最后活跃时间戳（毫秒）
     */
    public long getLastTouch(String sessionId) {
        String val = redis.opsForValue().get(KEY_PREFIX + sessionId + ":touch");
        return val != null ? Long.parseLong(val) : 0L;
    }
}
