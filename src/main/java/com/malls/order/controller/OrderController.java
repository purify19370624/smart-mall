package com.malls.order.controller;

import com.malls.common.security.RequireLogin;
import com.malls.order.service.OraderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 *
 * <p>整个类都加 {@link RequireLogin}：订单是用户私有数据，
 * 每个方法都必须有登录态，userId 一律从令牌里取，绝不从请求参数拿。
 */
@RestController
@RequestMapping("/api/orders")
@RequireLogin
@RequiredArgsConstructor
public class OrderController {

    private final OraderService orderService;

    // 新建订单

    // 订单分页

    // 订单详情

    // 取消订单

}
