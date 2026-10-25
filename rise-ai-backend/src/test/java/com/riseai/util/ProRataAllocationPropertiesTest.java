package com.riseai.util;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProRataAllocationPropertiesTest {
    record Case(List<Long> weightCents, long amountPermille) {
    }

    @Property(tries = 500)
    void sharesSumToAmountStayWithinWeightsAndAreProportionalToTheCent(@ForAll("cases") Case c) {
        Map<Integer, BigDecimal> weights = new LinkedHashMap<>();
        for (int i = 0; i < c.weightCents().size(); i++) {
            weights.put(i, BigDecimal.valueOf(c.weightCents().get(i), 2));
        }
        BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = total.multiply(BigDecimal.valueOf(c.amountPermille())).divide(BigDecimal.valueOf(1000), 2, RoundingMode.DOWN);

        Map<Integer, BigDecimal> shares = ProRataAllocation.allocate(amount, weights);

        assertThat(shares.keySet()).containsExactlyElementsOf(weights.keySet());
        assertThat(shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(amount);
        for (Integer key : weights.keySet()) {
            BigDecimal share = shares.get(key);
            BigDecimal exact = amount.multiply(weights.get(key)).divide(total, 10, RoundingMode.HALF_UP);
            assertThat(share.signum()).isGreaterThanOrEqualTo(0);
            assertThat(share).isLessThanOrEqualTo(weights.get(key));
            assertThat(share.scale()).isEqualTo(2);
            assertThat(share.subtract(exact).abs()).isLessThan(new BigDecimal("0.01"));
        }
    }

    @Property(tries = 100)
    void fullAmountRefundsEveryWeightExactly(@ForAll("cases") Case c) {
        Map<Integer, BigDecimal> weights = new LinkedHashMap<>();
        for (int i = 0; i < c.weightCents().size(); i++) {
            weights.put(i, BigDecimal.valueOf(c.weightCents().get(i), 2));
        }
        BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Integer, BigDecimal> shares = ProRataAllocation.allocate(total, weights);

        weights.forEach((key, weight) -> assertThat(shares.get(key)).isEqualByComparingTo(weight));
    }

    @Provide
    Arbitrary<Case> cases() {
        return Combinators.combine(
                Arbitraries.longs().between(1, 10_000_000_000L).list().ofMinSize(1).ofMaxSize(40),
                Arbitraries.longs().between(0, 1000)).as(Case::new);
    }
}
