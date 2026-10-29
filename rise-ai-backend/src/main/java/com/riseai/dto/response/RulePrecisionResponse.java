package com.riseai.dto.response;

import java.math.BigDecimal;

public record RulePrecisionResponse(String ruleId, long total, long open, long acknowledged, long concernConfirmed, long dismissed,
                                    BigDecimal precision) {
}
