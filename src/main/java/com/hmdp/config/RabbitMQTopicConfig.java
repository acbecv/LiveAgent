package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQTopicConfig {
    /**
     * 线程工厂，防止mq消息堆积
     */
    @Bean("customContainerFactory")
    public SimpleRabbitListenerContainerFactory containerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        //设置线程数
        factory.setConcurrentConsumers(10);
        //最大线程数
        factory.setMaxConcurrentConsumers(10);
        configurer.configure(factory, connectionFactory);
        return factory;
    }


    public static final String QUEUE = "direct.seckill.queue";
    public static final String EXCHANGE = "hmdianping.direct";
    public static final String ROUTINGKEY = "direct.seckill";
    @Bean
    public Queue queue(){
        return new Queue(QUEUE);
    }
    @Bean
    public DirectExchange directExchange(){
        return new DirectExchange(EXCHANGE);
    }
    @Bean
    public Binding binding(){
        return BindingBuilder.bind(queue()).to(directExchange()).with(ROUTINGKEY);
    }

    // 延时队列配置
    public static final String DELAY_QUEUE = "delay.order.queue";
    public static final String DELAY_EXCHANGE = "delay.order.exchange";
    public static final String DELAY_ROUTING_KEY = "delay.order";
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.letter";

    // 死信交换机
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    // 死信队列（实际处理取消订单的队列）
    @Bean
    public Queue deadLetterQueue() {
        return new Queue("dead.letter.queue");
    }

    // 绑定死信队列到死信交换机
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_ROUTING_KEY);
    }

    // 延时队列
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-message-ttl", 3600000) // 1小时过期
                .build();
    }

    // 延时交换机
    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE);
    }

    // 绑定延时队列到延时交换机
    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(DELAY_ROUTING_KEY);
    }

    // ============================================
    // Canal监听数据库变更，异步删除缓存配置
    // ============================================
    public static final String CACHE_DELETE_QUEUE = "cache.delete.queue";
    public static final String CACHE_DELETE_EXCHANGE = "cache.delete.exchange";
    public static final String CACHE_DELETE_ROUTING_KEY = "cache.delete";
    
    // 缓存删除死信队列配置
    public static final String CACHE_DELETE_DLQ = "cache.delete.dlq";
    public static final String CACHE_DELETE_DLQ_EXCHANGE = "cache.delete.dlq.exchange";
    public static final String CACHE_DELETE_DLQ_ROUTING_KEY = "cache.delete.dlq";

    // 缓存删除队列（带死信配置）
    @Bean
    public Queue cacheDeleteQueue() {
        return QueueBuilder.durable(CACHE_DELETE_QUEUE)
                .withArgument("x-dead-letter-exchange", CACHE_DELETE_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CACHE_DELETE_DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 3600000) // 1小时过期
                .build();
    }

    // 缓存删除交换机
    @Bean
    public DirectExchange cacheDeleteExchange() {
        return new DirectExchange(CACHE_DELETE_EXCHANGE);
    }

    // 绑定缓存删除队列到交换机
    @Bean
    public Binding cacheDeleteBinding() {
        return BindingBuilder.bind(cacheDeleteQueue()).to(cacheDeleteExchange()).with(CACHE_DELETE_ROUTING_KEY);
    }
    
    // 缓存删除死信队列
    @Bean
    public Queue cacheDeleteDlq() {
        return new Queue(CACHE_DELETE_DLQ);
    }
    
    // 缓存删除死信交换机
    @Bean
    public DirectExchange cacheDeleteDlqExchange() {
        return new DirectExchange(CACHE_DELETE_DLQ_EXCHANGE);
    }
    
    // 绑定缓存删除死信队列到死信交换机
    @Bean
    public Binding cacheDeleteDlqBinding() {
        return BindingBuilder.bind(cacheDeleteDlq())
                .to(cacheDeleteDlqExchange())
                .with(CACHE_DELETE_DLQ_ROUTING_KEY);
    }

//    private static final String QUEUE01="queue_topic01";
//    private static final String QUEUE02="queue_topic02";
//    private static final String EXCHANGE = "topicExchange";
//    private static final String ROUTINGKEY01 = "#.queue.#";
//    private static final String ROUTINGKEY02 = "*.queue.#";
//    @Bean
//    public Queue topicqueue01(){
//        return new Queue(QUEUE01);
//    }
//    @Bean
//    public Queue topicqueue02(){
//        return new Queue(QUEUE02);
//    }
//    @Bean
//    public TopicExchange topicExchange(){
//        return new TopicExchange(EXCHANGE);
//    }
//    @Bean
//    public Binding topicbinding01(){
//        return BindingBuilder.bind(topicqueue01()).to(topicExchange()).with(ROUTINGKEY01);
//    }
//    @Bean
//    public Binding topicbinding02(){
//        return BindingBuilder.bind(topicqueue02()).to(topicExchange()).with(ROUTINGKEY02);
//    }
}
