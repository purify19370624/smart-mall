package com.malls.pay.domain;

/**
 * 支付单状态。
 */
public enum PaymentStatus {
    UNPAID("未支付"),
    PENDING("待支付"),
    PAID("支付成功"),
    FAILED("支付失败"),
    REFUNDED("已退款"),
    CLOSED("已关闭");   // 支付单被关闭后不能再发起，需要重新下单

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
