package com.malls.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malls.cart.service.CartService;
import com.malls.catalog.domain.Product;
import com.malls.catalog.service.ProductService;
import com.malls.catalog.service.SkuStockService;
import com.malls.common.api.PageResult;
import com.malls.common.api.ResultCode;
import com.malls.common.config.MallProperties;
import com.malls.common.exception.BizException;
import com.malls.order.domain.CartItem;
import com.malls.order.domain.Order;
import com.malls.order.domain.OrderItem;
import com.malls.order.dto.CreateOrderRequest;
import com.malls.order.mapper.OrderItemMapper;
import com.malls.order.mapper.OrderLogMapper;
import com.malls.order.mapper.OrderMapper;
import com.malls.order.service.OraderService;
import com.malls.order.vo.OrderVO;
import com.malls.pay.domain.PayChannel;
import com.malls.user.domain.UserAddress;
import com.malls.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OraderServiceImpl implements OraderService {
    // 订单号里的时间部分：精确到秒
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;

    // 订单明细
    private final OrderItemMapper orderItemMapper;

    // 状态流水
    private final OrderLogMapper orderLogMapper;

    // 购物车：读勾选项、下单后清理
    private final CartService cartService;

    // 库存：扣减与回补
    private final SkuStockService skuStockService;

    // 商品：快照要标题与主图
    private final ProductService productService;

    // 地址：生成收货快照
    private final AddressService addressService;

    // 业务配置：运费、包邮门槛、支付超时
    private final MallProperties properties;

    // JSON 工具：收货快照序列化
    private final ObjectMapper objectMapper;

    /**
     * 下单：读购物车勾选项 → 扣库存 → 写订单/明细/流水 → 清掉已结算的购物车条目。
     *
     * <p>整段在一个事务里：任何一步失败（库存不足、地址不存在）全部回滚，
     * 不会出现"扣了库存却没生成订单"的中间态。
     *
     * @param userId  当前用户 ID
     * @param request 下单请求（地址 + 幂等键）
     * @return 新建订单
     * @throws BizException 购物车为空 13001；地址不存在 11006；库存不足 12003
     */
    @Override
    public OrderVO create(Long userId, CreateOrderRequest request) {
        // 1. 幂等：同一个 requestNo 已经下过单，直接返回那张单，不重复扣库存
        Order exist = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(
                Order::getRequestNo, request.requestNo()
        ));
        if (exist != null) {
            // 别人的 requestNo 不能拿来查单，统一按"订单不存在"处理
            if (!exist.ownedBy(userId)) {
                throw new BizException(ResultCode.ORDER_NOT_FOUND);
            }
            log.debug("重复下单请求命中幂等键 requestNo={} orderNo={}", request.requestNo(), exist.getOrderNo());
            return detail(userId, exist.getOrderNo());
        }

        // 2. 读勾选的购物车条目：没勾任何东西就没什么可下的
        List<CartItem> cartItems = cartService.listChecked(userId);
        if (cartItems.isEmpty()) {
            throw new BizException(ResultCode.CART_EMPTY);
        }
        // 3. 地址双条件校验：拿不到说明不存在或不是你的，11006
        UserAddress owned = addressService.getOwned(userId, request.addressId());

        HashMap<Object, Object> objectObjectHashMap = new HashMap<>();

        // 明细先攒在内存里，等订单主表拿到 ID 再统一落库
        ArrayList<OrderItem> orderItems = new ArrayList<>();

        // 商品 总额 逐行累加
        BigDecimal zero = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {

            // 扣库存   条件更新




        }


        return null;
    }

    /**
     * 我的订单分页。
     *
     * <p>列表不返回明细：订单列表只展示"共 N 件 + 总金额"，逐单查明细是 N+1。
     *
     * @param userId   当前用户 ID
     * @param status   状态过滤，null 或空串表示全部
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageResult<OrderVO> page(Long userId, String status, long pageNum, long pageSize) {
        return null;
    }

    /**
     * 订单详情（含明细）。
     *
     * @param userId  当前用户 ID
     * @param orderNo 订单号
     * @return 订单详情
     * @throws BizException 订单不存在或不属于当前用户 13002
     */
    @Override
    public OrderVO detail(Long userId, String orderNo) {
        return null;
    }

    /**
     * 取消订单：仅待支付可取消，取消后回补库存。
     *
     * @param userId  当前用户 ID
     * @param orderNo 订单号
     * @throws BizException 订单不存在 13002；状态不允许 13003
     */
    @Override
    public void cancel(Long userId, String orderNo) {

    }

    /**
     * 按单号取订单并校验归属，供支付服务创建支付单。
     *
     * @param orderNo 订单号
     * @param userId  当前用户 ID
     * @return 订单实体
     * @throws BizException 订单不存在或不属于该用户 13002
     */
    @Override
    public Order requireOrder(String orderNo, Long userId) {
        return null;
    }

    /**
     * 支付成功，把订单从待支付推进到待发货。
     *
     * <p><b>幂等</b>：重复调用（第三方重复回调）第二次影响 0 行，
     * 此时看当前状态——已是待发货就直接返回，不再报错。
     *
     * @param orderNo 订单号
     * @param channel 支付渠道，回填到订单的 pay_type
     * @throws BizException 订单已关闭 13004（此时钱已付，需走退款）
     */
    @Override
    public void markPaid(String orderNo, PayChannel channel) {

    }

    /**
     * 关闭所有超时未支付的订单，回补库存。
     *
     * <p>由定时任务每分钟调一次；Day07 换成 RabbitMQ 延时队列（TTL + 死信），
     * 就不再需要轮询全表。
     *
     * @return 本次关闭的订单数
     */
    @Override
    public int closeExpired() {


        return 0;
    }

}
