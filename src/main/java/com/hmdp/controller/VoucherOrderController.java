package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询用户所有订单
     */
    @GetMapping("/user/{userId}")
    public Result queryOrdersByUser(@PathVariable("userId") Long userId) {
        return voucherOrderService.queryOrdersByUser(userId);
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel/{orderId}")
    public Result cancelOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.cancelOrder(orderId);
    }

    /**
     * 申请退款
     */
    @PostMapping("/refund/{orderId}")
    public Result refundOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.refundOrder(orderId);
    }

    /**
     * 退单（删除订单记录）
     */
    @PostMapping("/delete/{orderId}")
    public Result deleteOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.deleteOrder(orderId);
    }
}
