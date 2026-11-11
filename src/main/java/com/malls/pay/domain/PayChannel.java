package com.malls.pay.domain;

public enum PayChannel {
    MOCK("模拟支付"),
    ALIPAY("支付宝"),
    WECHAT("微信支付");
    private final String label;
    PayChannel(String label){
        this.label  = label;

    }
    public String getLabel(){
        return label;
    }
}
