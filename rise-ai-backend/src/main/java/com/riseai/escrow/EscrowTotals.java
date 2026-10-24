package com.riseai.escrow;

import java.math.BigDecimal;

public record EscrowTotals(BigDecimal confirmed, BigDecimal reserved, BigDecimal disbursed, BigDecimal refunded, BigDecimal available) {
}
