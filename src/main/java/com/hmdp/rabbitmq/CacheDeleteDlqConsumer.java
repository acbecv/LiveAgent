package com.hmdp.rabbitmq;

import com.hmdp.config.RabbitMQTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 缓存删除死信队列消费者
 * 处理最终失败的缓存删除消息
 */
@Slf4j
@Component
public class CacheDeleteDlqConsumer {

    /**
     * 监听缓存删除死信队列
     * @param msg 缓存删除消息
     */
    @RabbitListener(queues = RabbitMQTopicConfig.CACHE_DELETE_DLQ)
    public void handleCacheDeleteDlq(String msg) {
        log.error("死信队列收到缓存删除失败消息: {}", msg);
        
        try {
            // 这里可以实现告警通知逻辑
            // 1. 发送邮件通知
            // 2. 发送短信通知
            // 3. 记录到错误日志系统
            // 4. 人工处理或自动修复
            
            // 示例：发送告警
            sendAlarmNotification(msg);
            
            log.info("死信队列消息处理完成，已发送告警通知");
            
        } catch (Exception e) {
            log.error("处理死信队列消息失败", e);
            // 死信队列的消息不再重试，避免无限循环
        }
    }
    
    /**
     * 发送告警通知
     * @param msg 失败的消息内容
     */
    private void sendAlarmNotification(String msg) {
        // 这里可以集成告警系统
        // 示例：记录到告警日志
        log.error("【告警】缓存删除失败，需要人工处理: {}", msg);
        
        // 实际项目中可以集成：
        // 1. 企业微信告警
        // 2. 钉钉告警
        // 3. 短信告警
        // 4. 邮件告警
    }
}
