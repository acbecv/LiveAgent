package com.hmdp.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.config.RabbitMQTopicConfig;
import com.hmdp.dto.CacheDeleteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存删除消息消费者
 * 异步处理Canal发送的缓存删除消息
 */
@Slf4j
@Component
public class CacheDeleteConsumer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private Cache<String, Object> shopCache;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 监听缓存删除队列，异步删除缓存
     * @param msg 缓存删除消息
     */
    @RabbitListener(queues = RabbitMQTopicConfig.CACHE_DELETE_QUEUE)
    public void handleCacheDelete(String msg) {
        log.info("接收到缓存删除消息: {}", msg);
        
        // 生成消息唯一标识，用于幂等性检查
        String messageId = generateMessageId(msg);
        String processedKey = "processed:cache:delete:" + messageId;
        
        // 幂等性检查：避免重复处理
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(processedKey))) {
            log.info("消息已处理，跳过: {}", messageId);
            return;
        }
        
        int maxRetries = 3; // 最大重试次数
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                CacheDeleteMessage message = JSON.parseObject(msg, CacheDeleteMessage.class);
                List<String> cacheKeys = message.getCacheKeys();
                
                if (cacheKeys == null || cacheKeys.isEmpty()) {
                    log.warn("缓存删除消息中key列表为空");
                    return;
                }
                
                boolean allSuccess = true;
                
                // 删除Redis缓存
                for (String key : cacheKeys) {
                    try {
                        Boolean deleted = stringRedisTemplate.delete(key);
                        if (Boolean.TRUE.equals(deleted)) {
                            log.info("Redis缓存已删除: {}", key);
                        } else {
                            log.warn("Redis缓存删除失败或key不存在: {}", key);
                        }
                    } catch (Exception e) {
                        log.error("删除Redis缓存失败: {}", key, e);
                        allSuccess = false;
                    }
                }
                
                // 删除Caffeine本地缓存
                for (String key : cacheKeys) {
                    try {
                        shopCache.invalidate(key);
                        log.info("Caffeine缓存已删除: {}", key);
                    } catch (Exception e) {
                        log.error("删除Caffeine缓存失败: {}", key, e);
                        allSuccess = false;
                    }
                }
                
                if (allSuccess) {
                    // 标记消息已处理，设置过期时间24小时
                    stringRedisTemplate.opsForValue().set(processedKey, "1", 24, TimeUnit.HOURS);
                    log.info("缓存删除处理完成, 表名: {}, 操作类型: {}", 
                            message.getTableName(), message.getOperationType());
                    return; // 处理成功，退出循环
                } else {
                    // 部分失败，继续重试
                    retryCount++;
                    log.warn("部分缓存删除失败，进行第{}次重试", retryCount);
                }
                
            } catch (Exception e) {
                retryCount++;
                log.error("第{}次处理失败: {}", retryCount, e.getMessage(), e);
            }
            
            if (retryCount < maxRetries) {
                // 指数退避重试
                try {
                    Thread.sleep(1000L * retryCount);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // 达到最大重试次数，发送到死信队列
        log.error("达到最大重试次数，消息处理失败，发送到死信队列: {}", messageId);
        sendToDeadLetterQueue(msg);
    }
    
    /**
     * 生成消息唯一标识
     */
    private String generateMessageId(String msg) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(msg.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("生成消息ID失败", e);
            return String.valueOf(System.currentTimeMillis());
        }
    }
    
    /**
     * 发送到死信队列
     */
    private void sendToDeadLetterQueue(String msg) {
        try {
            // 发送到死信交换机
            rabbitTemplate.convertAndSend(
                    RabbitMQTopicConfig.CACHE_DELETE_DLQ_EXCHANGE,
                    RabbitMQTopicConfig.CACHE_DELETE_DLQ_ROUTING_KEY,
                    msg
            );
            log.info("消息已发送到死信队列");
        } catch (Exception e) {
            log.error("发送到死信队列失败", e);
        }
    }
}
