package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.rabbitmq.MQSender;
import com.hmdp.redisson.annotations.Lock;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFutureCallback;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private MQSender mqSender;

    private RateLimiter rateLimiter=RateLimiter.create(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //令牌桶算法 限流
//        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)){
//            return Result.fail("目前网络正忙，请重试");
//        }
        //1.执行lua脚本
        Long userId = UserHolder.getUser().getId();

        Long r = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2.判断结果为0
        int result = r.intValue();
        if (result != 0) {
            //2.1不为0代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "该用户重复下单");
        }
        //2.2为0代表有购买资格,将下单信息保存到阻塞队列

        //2.3创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.4订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.5用户id
        voucherOrder.setUserId(userId);
        //2.6代金卷id
        voucherOrder.setVoucherId(voucherId);
//4. 存入消息队列等待异步消费
        // 4.1 创建CorrelationData
        CorrelationData cd = new CorrelationData();
        // 4.2 给Future添加ConfirmCallback
        cd.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>(){
            @Override
            public void onSuccess(CorrelationData.Confirm confirm) {
                // 4.3 消息发送成功时的处理逻辑
                if(confirm.isAck()){
                    log.debug("消息发送成功，收到ack!");
                }else{
                    log.error("消息发送失败，收到nack!"+ confirm.getReason());
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                // 4.2.2 消息发送失败时的处理逻辑
                log.error("消息发送失败，发生异常!"+throwable.getMessage());
            }
        });
        //2.7将信息放入MQ中
        System.out.println("--------------");
        mqSender.sendSeckillMessage(JSON.toJSONString(voucherOrder));


        //2.7 返回订单id
        return Result.ok(orderId);
//        单机模式下，使用synchronized实现锁
//        synchronized (userId.toString().intern())
//        {
//            //    createVoucherOrder的事物不会生效,因为你调用的方法，其实是this.的方式调用的，事务想要生效，
//            //    还得利用代理来生效，所以这个地方，我们需要获得原始的事务对象， 来操作事务
//            return voucherOrderService.createVoucherOrder(voucherId);
//        }
    }

    @Lock(name = "lock:order:#{#voucherOrder.userId}", waitTime = 1, leaseTime = 30, timeUnit = TimeUnit.SECONDS)
    @Override
    public boolean saveWithDistributedLock(VoucherOrder voucherOrder) {

        return this.save(voucherOrder);
    }

    @Override
    public Result queryOrdersByUser(Long userId) {
        List<VoucherOrder> orders = query().eq("user_id", userId)
                .orderByDesc("create_time")
                .list();
        return Result.ok(orders);
    }

    @Override
    public Result cancelOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        // 仅未支付(1)和已支付(2)可取消
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            return Result.fail("当前订单状态不可取消");
        }
        order.setStatus(4); // 已取消
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
        return Result.ok("订单已取消");
    }

    @Override
    public Result refundOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        // 仅已支付(2)和已核销(3)可申请退款
        if (order.getStatus() != 2 && order.getStatus() != 3) {
            return Result.fail("当前订单状态不可退款");
        }
        order.setStatus(6); // 已退款
        order.setRefundTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
        return Result.ok("退款成功");
    }

    @Override
    public Result deleteOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        // 仅已取消(4)或已退款(6)的订单可删除
        if (order.getStatus() != 4 && order.getStatus() != 6) {
            return Result.fail("当前订单状态不可退单，请先取消或退款");
        }
        // 删除订单记录
        removeById(orderId);
        return Result.ok("退单成功，订单已删除");
    }


//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 一人一单逻辑
//        Long userId = UserHolder.getUser().getId();
//
//
//        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//        if (count > 0){
//            return Result.fail("你已经抢过优惠券了哦");
//        }
//
//        //5. 扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock",0)   //加了CAS 乐观锁，Compare and swap
//                .update();
//
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//
////        库存足且在时间范围内的，则创建新的订单
//        //6. 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //6.1 设置订单id，生成订单的全局id
//        long orderId = redisIdWorker.nextId("order");
//        //6.2 设置用户id
//        Long id = UserHolder.getUser().getId();
//        //6.3 设置代金券id
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(id);
//        //7. 将订单数据保存到表中
//        save(voucherOrder);
//        //8. 返回订单id
//        return Result.ok(orderId);
//    }
}
