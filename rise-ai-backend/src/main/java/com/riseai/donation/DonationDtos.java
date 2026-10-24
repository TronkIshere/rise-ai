package com.riseai.donation;

import com.riseai.user.User;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class DonationDtos {
    public static final String ANONYMOUS_NAME = "Anonymous donor";

    private DonationDtos() {
    }

    public record CreateDonationRequest(
            @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
            PaymentMethod paymentMethod,
            Boolean anonymous,
            @Size(max = 1000) String donorMessage) {
    }

    public record ConfirmDonationRequest(@Size(max = 100) String simulatedReference) {
    }

    public record DonationResponse(UUID id, String donationCode, UUID campaignId, UUID donorId, String donorDisplayName,
                                   BigDecimal amount, BigDecimal refundedAmount, String currency, DonationStatus donationStatus,
                                   PaymentMethod paymentMethod, boolean anonymous, String donorMessage, String simulatedReference,
                                   Instant confirmedAt, Instant createdAt) {
        public static DonationResponse full(Donation d, User donor) {
            return new DonationResponse(d.getId(), d.getDonationCode(), d.getCampaignId(), d.getDonorId(), donor == null ? null : donor.getFullName(),
                    d.getAmount(), d.getRefundedAmount(), d.getCurrency(), d.getDonationStatus(), d.getPaymentMethod(), d.isAnonymous(),
                    d.getDonorMessage(), d.getSimulatedReference(), d.getConfirmedAt(), d.getCreatedAt());
        }

        public static DonationResponse masked(Donation d, User donor) {
            String name = d.isAnonymous() || donor == null ? ANONYMOUS_NAME : donor.getFullName();
            return new DonationResponse(d.getId(), d.getDonationCode(), d.getCampaignId(), null, name, d.getAmount(), d.getRefundedAmount(),
                    d.getCurrency(), d.getDonationStatus(), d.getPaymentMethod(), d.isAnonymous(), d.getDonorMessage(), null,
                    d.getConfirmedAt(), d.getCreatedAt());
        }
    }
}
