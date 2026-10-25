package com.riseai.dto.response;

import com.riseai.entity.EscrowAccount;
import com.riseai.util.Money;

import java.math.BigDecimal;

public record FundingSummary(BigDecimal totalConfirmed, BigDecimal totalRefunded, BigDecimal netRaised, BigDecimal totalReserved,
                             BigDecimal totalDisbursed, BigDecimal availableBalance, BigDecimal progressPercent) {
    public static FundingSummary from(EscrowAccount a, BigDecimal target) {
        if (a == null) {
            return new FundingSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    Money.percent(BigDecimal.ZERO, target));
        }
        BigDecimal net = a.getTotalConfirmedContributions().subtract(a.getTotalRefunded());
        return new FundingSummary(a.getTotalConfirmedContributions(), a.getTotalRefunded(), net, a.getTotalReserved(),
                a.getTotalDisbursed(), a.getAvailableBalance(), Money.percent(net, target));
    }
}
