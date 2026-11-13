package com.malls.user.service.impl;

import com.malls.user.service.MembershipPolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class MembershipPolicyImpl implements MembershipPolicy {
    private static final NavigableMap<BigDecimal,Integer> LEVELS = new TreeMap<>();
    static {
        LEVELS.put(new BigDecimal("0"),1);
        LEVELS.put(new BigDecimal("1000"),2);
        LEVELS.put(new BigDecimal("5000"),3);
        LEVELS.put(new BigDecimal("20000"),4);
        LEVELS.put(new BigDecimal("100000"),5);

    }
    @Override
    public int leveOf(BigDecimal totalConsumption) {
        BigDecimal total = totalConsumption==null?BigDecimal.ZERO : totalConsumption;
        if(total.signum()<0){
            return 1;
        }
        Map.Entry<BigDecimal, Integer> entry = LEVELS.floorEntry(total);
        return entry!=null?entry.getValue():1;
    }

    @Override
    public int pointOf(BigDecimal payAmount) {
        return payAmount==null?0:payAmount.intValue();
    }
}
