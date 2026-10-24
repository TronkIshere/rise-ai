package com.riseai.admin;

import com.riseai.campaign.CampaignRepository;
import com.riseai.common.Money;
import com.riseai.config.RiseProperties;
import com.riseai.disbursement.DisbursementRepository;
import com.riseai.disbursement.DisbursementStatus;
import com.riseai.dispute.DisputeRepository;
import com.riseai.dispute.DisputeStatus;
import com.riseai.donation.DonationRepository;
import com.riseai.donation.DonationStatus;
import com.riseai.escrow.EscrowAccountRepository;
import com.riseai.escrow.EscrowTotals;
import com.riseai.refund.RefundRepository;
import com.riseai.refund.RefundStatus;
import com.riseai.user.UserRepository;
import com.riseai.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatisticsService {
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final DonationRepository donationRepository;
    private final DisbursementRepository disbursementRepository;
    private final DisputeRepository disputeRepository;
    private final RefundRepository refundRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final RiseProperties properties;

    public record SystemStatistics(long totalUsers, long activeUsers, long suspendedUsers, Map<String, Long> campaignsByStatus,
                                   long pendingDonations, long openDisbursementRequests, long openDisputes, long openRefundRequests,
                                   String currency, BigDecimal totalConfirmedContributions, BigDecimal totalReserved,
                                   BigDecimal totalDisbursed, BigDecimal totalRefunded, BigDecimal totalAvailable, boolean simulated) {
    }

    @Transactional(readOnly = true)
    public SystemStatistics statistics() {
        Map<String, Long> campaigns = new LinkedHashMap<>();
        for (Object[] row : campaignRepository.countGroupedByStatus()) {
            campaigns.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        String currency = properties.escrow().currency();
        EscrowTotals totals = escrowAccountRepository.totals(currency);
        return new SystemStatistics(userRepository.count(), userRepository.countByStatus(UserStatus.ACTIVE), userRepository.countByStatus(UserStatus.SUSPENDED),
                campaigns, donationRepository.countByDonationStatus(DonationStatus.PENDING),
                disbursementRepository.countByRequestStatusIn(DisbursementStatus.OPEN),
                disputeRepository.countByDisputeStatusIn(DisputeStatus.OPEN_STATES),
                refundRepository.countByRefundStatusIn(RefundStatus.OPEN), currency,
                Money.orZero(totals == null ? null : totals.confirmed()), Money.orZero(totals == null ? null : totals.reserved()),
                Money.orZero(totals == null ? null : totals.disbursed()), Money.orZero(totals == null ? null : totals.refunded()),
                Money.orZero(totals == null ? null : totals.available()), true);
    }
}
