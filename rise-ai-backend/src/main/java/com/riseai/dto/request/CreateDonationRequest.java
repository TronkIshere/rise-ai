package com.riseai.dto.request;

import com.riseai.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateDonationRequest(
        @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        PaymentMethod paymentMethod,
        Boolean anonymous,
        @Size(max = 1000) String donorMessage) {
}
