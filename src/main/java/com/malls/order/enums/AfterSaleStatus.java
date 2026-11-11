package com.malls.order.enums;

public enum AfterSaleStatus {

    APPLIED("已申请"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    REFUNDED("已退款"),
    CANCELLED("已撤销");

    private final String label;

    AfterSaleStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
