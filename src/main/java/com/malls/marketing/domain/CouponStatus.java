package com.malls.marketing.domain;

public enum CouponStatus {

    UNUSED("未使用"),
    USED("已使用"),
    EXPIRED("已过期");

    private final String label;

    CouponStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}