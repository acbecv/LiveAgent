package com.hmdp.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.hmdp.config.RabbitMQTopicConfig;
import com.hmdp.dto.CacheDeleteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息发送者
 */
@Slf4j
@Service
public class MQSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String ROUTINGKEY = "direct.seckill";
    /**
     * 发送秒杀信息
     * @param msg
     */
    public void sendSeckillMessage(String msg){
        log.info("发送消息"+msg);
        rabbitTemplate.convertAndSend(RabbitMQTopicConfig.EXCHANGE,ROUTINGKEY,msg);
    }

    /**
     * 发送延时消息（用于订单超时取消）
     * @param msg 消息内容
     * @param delayTime 延时时间（毫秒）
     */
    public void sendDelayMessage(String msg, long delayTime) {
        log.info("发送延时消息: {}", msg);
        // 创建消息，设置延时属性
        Message message = MessageBuilder.withBody(msg.getBytes())
                .setHeader("x-delay", delayTime)
                .build();
        // 发送到延时交换机
        rabbitTemplate.convertAndSend(RabbitMQTopicConfig.DELAY_EXCHANGE,
                RabbitMQTopicConfig.DELAY_ROUTING_KEY, message);
    }

    /**
     * 发送缓存删除消息（Canal监听数据库变更后调用）
     * @param message 缓存删除消息
     */
    public void sendCacheDeleteMessage(CacheDeleteMessage message) {
        String msg = JSON.toJSONString(message);
        log.info("发送缓存删除消息: {}", msg);
        rabbitTemplate.convertAndSend(
                RabbitMQTopicConfig.CACHE_DELETE_EXCHANGE,
                RabbitMQTopicConfig.CACHE_DELETE_ROUTING_KEY,
                msg
        );
    }
}
