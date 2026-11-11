package com.malls.marketing.domain;

public enum SeckillStatus {
    PENDING("未开始"),
    ACTIVE("进行中"),
    ENDED("已结束");

    private final String label;

    SeckillStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}