package com.riseai.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProRataAllocation {
    private ProRataAllocation() {
    }

    public static <K> Map<K, BigDecimal> allocate(BigDecimal amount, Map<K, BigDecimal> weights) {
        BigInteger total = cents(Money.sum(weights.values().stream()));
        BigInteger pool = cents(amount);
        if (pool.signum() < 0 || pool.compareTo(total) > 0) {
            throw new IllegalArgumentException("Amount must be between zero and the total weight");
        }
        List<K> keys = new ArrayList<>(weights.keySet());
        BigInteger[] shares = new BigInteger[keys.size()];
        BigInteger[] remainders = new BigInteger[keys.size()];
        BigInteger allocated = BigInteger.ZERO;
        for (int i = 0; i < keys.size(); i++) {
            BigInteger[] division = total.signum() == 0 ? new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO}
                    : pool.multiply(cents(weights.get(keys.get(i)))).divideAndRemainder(total);
            shares[i] = division[0];
            remainders[i] = division[1];
            allocated = allocated.add(division[0]);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> remainders[i]).reversed());
        int leftover = pool.subtract(allocated).intValueExact();
        for (int i = 0; i < leftover; i++) {
            shares[order.get(i)] = shares[order.get(i)].add(BigInteger.ONE);
        }
        Map<K, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            result.put(keys.get(i), new BigDecimal(shares[i], Money.SCALE));
        }
        return result;
    }

    private static BigInteger cents(BigDecimal value) {
        return value.movePointRight(Money.SCALE).toBigIntegerExact();
    }
}
