package com.riseai.dto.response;

import com.riseai.entity.Donation;
import com.riseai.enums.DonationStatus;
import com.riseai.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DonationResponse(UUID id, String donationCode, UUID campaignId, UUID donorId, String donorDisplayName,
                               BigDecimal amount, BigDecimal refundedAmount, String currency, DonationStatus donationStatus,
                               PaymentMethod paymentMethod, boolean anonymous, String donorMessage, String simulatedReference,
                               Instant confirmedAt, Instant createdAt) {
    public static final String ANONYMOUS_NAME = "Anonymous donor";

    public static DonationResponse full(Donation d, String donorName) {
        return new DonationResponse(d.getId(), d.getDonationCode(), d.getCampaignId(), d.getDonorId(), donorName,
                d.getAmount(), d.getRefundedAmount(), d.getCurrency(), d.getDonationStatus(), d.getPaymentMethod(), d.isAnonymous(),
                d.getDonorMessage(), d.getSimulatedReference(), d.getConfirmedAt(), d.getCreatedAt());
    }

    public static DonationResponse masked(Donation d, String donorName) {
        String name = d.isAnonymous() || donorName == null ? ANONYMOUS_NAME : donorName;
        return new DonationResponse(d.getId(), d.getDonationCode(), d.getCampaignId(), null, name, d.getAmount(), d.getRefundedAmount(),
                d.getCurrency(), d.getDonationStatus(), d.getPaymentMethod(), d.isAnonymous(), d.getDonorMessage(), null,
                d.getConfirmedAt(), d.getCreatedAt());
    }
}
