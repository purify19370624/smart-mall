package com.malls.order.service;

import com.malls.common.api.PageResult;
import com.malls.order.domain.Order;
import com.malls.order.dto.CreateOrderRequest;
import com.malls.order.vo.OrderVO;
import com.malls.pay.domain.PayChannel;

/**
 * 订单服务。
 *
 * <p>对外只暴露"用户视角"的动作：下单、我的订单、详情、取消，
 * 外加三个给支付/定时任务用的内部方法（requireOrder / markPaid / closeExpired）。
 */
public interface OraderService {


    // 新建订单
    OrderVO create(Long userId, CreateOrderRequest request);

    // 订单分页
    PageResult<OrderVO> page(Long userId, String status, long pageNum, long pageSize);


    // 订单详情
    OrderVO detail(Long userId, String orderNo);

    // 取消订单
    void cancel(Long userId, String orderNo);


    /**
     * 按单号取订单并校验归属，供支付服务创建支付单。
     *
     * @param orderNo 订单号
     * @param userId  当前用户 ID
     * @return 订单实体
     */
    Order requireOrder(String orderNo, Long userId);

    /**
     * 支付成功，把订单从待支付推进到待发货。
     *
     * <p><b>幂等</b>：重复调用（第三方重复回调）第二次影响 0 行，
     * 此时看当前状态——已是待发货就直接返回，不再报错。
     *
     * @param orderNo 订单号
     * @param channel 支付渠道，回填到订单的 pay_type
     * @throws com.malls.common.exception.BizException 订单已关闭 13004（此时钱已付，需走退款）
     */
    void markPaid(String orderNo, PayChannel channel);

    /**
     * 关闭所有超时未支付的订单，回补库存。
     *
     * @return 本次关闭的订单数
     */
    int closeExpired();
}
