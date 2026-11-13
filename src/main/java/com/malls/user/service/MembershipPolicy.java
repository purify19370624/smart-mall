package com.malls.user.service;

import java.math.BigDecimal;

public interface MembershipPolicy {
    int leveOf(BigDecimal totalConsumption);
    int pointOf(BigDecimal payAmount);
}
