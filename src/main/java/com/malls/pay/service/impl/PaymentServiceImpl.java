package com.malls.pay.service.impl;

// MyBatis-Plus 条件构造器
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
// 错误码
import com.malls.common.api.ResultCode;
// 业务异常
import com.malls.common.exception.BizException;
// 订单实体：读金额、判状态
import com.malls.order.domain.Order;
// 订单状态枚举
import com.malls.order.domain.OrderStatus;
// 订单服务：支付只调它的接口，不碰 order 的 mapper
import com.malls.order.service.OraderService;
// 支付请求 DTO
import com.malls.pay.dto.CreatePaymentRequest;
// 支付实体与持久层
import com.malls.pay.domain.Payment;
import com.malls.pay.domain.PayChannel;
import com.malls.pay.domain.PaymentStatus;
import com.malls.pay.mapper.PaymentMapper;
// 支付服务接口：本类实现它
import com.malls.pay.service.PaymentService;
// 支付 VO
import com.malls.pay.vo.PaymentVO;
// 构造器注入与日志
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// Service 组件
import org.springframework.stereotype.Service;
// 事务
import org.springframework.transaction.annotation.Transactional;

// 时间
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// 随机
import java.security.SecureRandom;

/**
 * 支付服务实现。
 *
 * <p>整个支付域只靠两条数据库约束保证正确性：
 * <ol>
 *   <li>{@code uk_payment_order} —— 一个订单只能有一条支付单，物理上杜绝重复建单</li>
 *   <li>{@code update ... where status = 'UNPAID'} —— 回调幂等，第二次影响 0 行</li>
 * </ol>
 *
 * <p>没有分布式锁、没有 Redis：单库单表时，把状态判断写进 UPDATE 的 WHERE
 * 就已经是原子的了。加锁是因为"先查再判"没有原子性，不是因为更新本身不安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    // 支付单号里的时间部分
    private static final DateTimeFormatter PAY_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 后 4 位随机，降低同秒并发撞号
    private static final SecureRandom RANDOM = new SecureRandom();

    // 支付单表
    private final PaymentMapper paymentMapper;

    // 订单服务接口
    private final OraderService orderService;

    /**
     * 创建支付单。
     *
     * <p>顺序：先确认订单还能付（存在、归属、未超时、未支付），
     * 再看有没有现成的未支付单可以复用，最后才新建。
     *
     * <p>金额<b>直接取订单的 pay_amount</b>，不做任何重算：
     * 一旦支付侧自己算一遍，就会和订单侧出现口径漂移，对账永远对不平。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO create(Long userId, CreatePaymentRequest request) {
        // 1. 取订单：内含归属校验，不是你的单直接 13004
        Order order = orderService.requireOrder(request.orderNo(), userId);

        // 2. 订单能不能付：payable() = 待支付 且 未过截止时间
        if (!order.payable()) {
            // 已发货/已完成，说明钱已经付过了
            if (order.getStatus() == OrderStatus.WAIT_SHIP || order.getStatus() == OrderStatus.COMPLETED) {
                throw new BizException(ResultCode.PAYMENT_ALREADY_PAID);
            }
            // 还在待支付：payable() 已经要求未超时，所以这里只可能是过了截止时间
            if (order.getStatus() == OrderStatus.WAIT_PAY) {
                throw new BizException(ResultCode.ORDER_PAY_TIMEOUT);
            }
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "当前订单状态不能发起支付");
        }

        // 3. 复用已有支付单：用户关掉收银台又重开时不该新建一条
        Payment exist = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderNo, order.getOrderNo()));

        if (exist != null) {
            if (exist.paid()) {
                throw new BizException(ResultCode.PAYMENT_ALREADY_PAID);
            }
            if (exist.getStatus() == PaymentStatus.CLOSED) {
                throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "支付单已关闭，请重新下单");
            }
            // 未支付 → 原样返回，金额与渠道都沿用第一次的选择
            return PaymentVO.of(exist);
        }

        // 4. 新建支付单
        Payment payment = new Payment();
        payment.setPaymentNo(nextPaymentNo());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setChannel(request.channel() == null ? PayChannel.MOCK : request.channel());
        payment.setAmount(order.getPayAmount());                 // 金额唯一来源：订单实付
        payment.setStatus(PaymentStatus.UNPAID);
        paymentMapper.insert(payment);

        log.info("创建支付单 paymentNo={} orderNo={} amount={} channel={}",
                payment.getPaymentNo(), payment.getOrderNo(), payment.getAmount(), payment.getChannel());

        return PaymentVO.of(payment);
    }

    /**
     * 支付回调。
     *
     * <p>幂等的关键在这一句条件更新：
     * {@code update pay_payment set status='PAID' where payment_no=? and status='UNPAID'}。
     * 第一次影响 1 行，重复回调影响 0 行——0 行不代表失败，代表"已经处理过了"。
     *
     * <p>为什么先改支付单、再改订单，且放在同一个事务里：
     * 支付单是记账凭证，订单是业务状态。如果订单推进失败（比如已被超时关单），
     * 整个事务回滚，支付单保持 UNPAID，渠道会继续重试，最终转人工退款——
     * 宁可"钱到了但单没推"，也不能"单推了但没记账"。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO callback(String paymentNo) {
        // 1. 支付单必须存在
        Payment payment = getByNo(paymentNo);

        // 2. 条件更新：只有 UNPAID 能变成 PAID
        int rows = paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getPaymentNo, paymentNo)
                .eq(Payment::getStatus, PaymentStatus.UNPAID)
                .set(Payment::getStatus, PaymentStatus.PAID)
                .set(Payment::getTradeNo, "MOCK" + System.currentTimeMillis())   // 模拟第三方交易号
                .set(Payment::getCallbackTime, LocalDateTime.now()));

        if (rows == 0) {
            // 没更新到：要么重复回调（已支付），要么支付单已关闭
            Payment latest = getByNo(paymentNo);
            if (latest.paid()) {
                log.debug("重复支付回调，幂等返回 paymentNo={}", paymentNo);
                return PaymentVO.of(latest);
            }
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "支付单状态不允许回调");
        }

        // 3. 推进订单状态：失败会抛异常，连带把上面的支付单更新一起回滚
        orderService.markPaid(payment.getOrderNo(), payment.getChannel());

        log.info("支付回调处理成功 paymentNo={} orderNo={}", paymentNo, payment.getOrderNo());

        // 4. 重新查一次，返回带 callbackTime / tradeNo 的最新状态
        return PaymentVO.of(getByNo(paymentNo));
    }

    @Override
    public PaymentVO detail(Long userId, String paymentNo) {
        Payment payment = getByNo(paymentNo);

        // 归属校验：支付单里有金额与交易号，不能给别人看
        if (!payment.ownedBy(userId)) {
            throw new BizException(ResultCode.PAYMENT_NOT_FOUND);
        }
        return PaymentVO.of(payment);
    }

    /**
     * 按支付单号查，查不到即 15001。
     */
    private Payment getByNo(String paymentNo) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getPaymentNo, paymentNo));

        if (payment == null) {
            throw new BizException(ResultCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    /**
     * 生成支付单号：P + 秒级时间 + 4 位随机。
     */
    private String nextPaymentNo() {
        return "P" + LocalDateTime.now().format(PAY_NO_TIME) + "%04d".formatted(RANDOM.nextInt(10000));
    }
}
