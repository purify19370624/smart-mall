package com.malls.user.domain;

public enum AccountStatus {
    ENABLED("正常"),
    DISABLED("禁用");
    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }
    public String getLabel() {
        return label;
    }

}
