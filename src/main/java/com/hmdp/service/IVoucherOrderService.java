package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     * 带分布式锁的保存方法
     * 防止重复下单
     * @param voucherOrder 订单对象
     * @return 是否保存成功
     */
    boolean saveWithDistributedLock(VoucherOrder voucherOrder);

    /**
     * 查询用户的所有订单
     * @param userId 用户ID
     * @return 订单列表
     */
    Result queryOrdersByUser(Long userId);

    /**
     * 取消订单（仅未支付/已支付可取消）
     * @param orderId 订单ID
     * @return 操作结果
     */
    Result cancelOrder(Long orderId);

    /**
     * 申请退款（仅已支付/已核销可退款）
     * @param orderId 订单ID
     * @return 操作结果
     */
    Result refundOrder(Long orderId);

    /**
     * 退单（删除订单记录）
     * 仅已取消(4)或已退款(6)的订单可删除
     * @param orderId 订单ID
     * @return 操作结果
     */
    Result deleteOrder(Long orderId);
}
