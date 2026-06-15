package com.hmdp.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.hmdp.config.RabbitMQTopicConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 消息消费者
 */
@Slf4j
@Service
public class MQReceiver {

    @Resource
    IVoucherOrderService voucherOrderService;

    @Resource
    ISeckillVoucherService seckillVoucherService;
    /**
     * 接收秒杀信息并下单
     * @param msg
     */

//    @RabbitListener(queues = RabbitMQTopicConfig.QUEUE)
//    public void receiveSeckillMessage(String msg) throws InterruptedException {
//        log.info("spring 消费者接收到消息：【" + msg + "】");
//        if (true) {
//            throw new MessageConversionException("故意的");
//        }
//        log.info("消息处理完成");
//    }


    @Transactional
    @RabbitListener(queues = RabbitMQTopicConfig.QUEUE)
    public void receiveSeckillMessage(String msg){
        log.info("接收到消息: "+msg);
        VoucherOrder voucherOrder = JSON.parseObject(msg, VoucherOrder.class);

        Long voucherId = voucherOrder.getVoucherId();
        //5.一人一单
        Long userId = voucherOrder.getUserId();
        //5.1查询订单
        int count = voucherOrderService.query().eq("user_id",userId).eq("voucher_id", voucherId).count();
        //5.2判断是否存在
        if(count>0){
            //用户已经购买过了
            log.error("该用户已购买过");
            return ;
        }
        log.info("扣减库存");
        //6.扣减库存
        boolean success = seckillVoucherService
                .update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)//cas乐观锁
                .update();
        if(!success){
            log.error("库存不足");
            return;
        }
        // 原始保存方法（已注释，使用带分布式锁的保存方法）
        // voucherOrderService.save(voucherOrder);
        
        // 直接保存订单（使用带分布式锁的保存方法）
        boolean saved = voucherOrderService.saveWithDistributedLock(voucherOrder);
        if (!saved) {
            log.error("订单保存失败: {}", voucherOrder.getId());
            return;
        }
    }

    /**
     * 处理延时消息，取消超时未支付的订单
     * @param msg 消息内容
     */
    @Transactional
    @RabbitListener(queues = "dead.letter.queue")
    public void receiveDelayMessage(String msg) {
        log.info("接收到延时消息: {}", msg);
        VoucherOrder voucherOrder = JSON.parseObject(msg, VoucherOrder.class);
        
        //查询订单是否存在且状态为未支付
        VoucherOrder order = voucherOrderService.getById(voucherOrder.getId());
        if (order == null) {
            log.warn("订单不存在: {}", voucherOrder.getId());
            return;
        }
        
        if (order.getStatus() != 1) {
            log.info("订单状态已变更，无需取消: {}，状态: {}", order.getId(), order.getStatus());
            return;
        }
        
        //取消订单，更新状态为已取消
        order.setStatus(4);
        boolean updateSuccess = voucherOrderService.updateById(order);
        if (updateSuccess) {
            log.info("订单已取消: {}", order.getId());
            
            //恢复库存
            Long voucherId = order.getVoucherId();
            boolean stockRecovered = seckillVoucherService
                    .update()
                    .setSql("stock = stock + 1")
                    .eq("voucher_id", voucherId)
                    .update();
            if (stockRecovered) {
                log.info("库存已恢复: {}", voucherId);
            } else {
                log.error("库存恢复失败: {}", voucherId);
            }
        } else {
            log.error("取消订单失败: {}", order.getId());
        }
    }

}
