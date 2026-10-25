package com.riseai.service.impl;

import com.riseai.configuration.RiseProperties;
import com.riseai.dto.response.EscrowTotals;
import com.riseai.dto.response.SystemStatistics;
import com.riseai.enums.DisbursementStatus;
import com.riseai.enums.DisputeStatus;
import com.riseai.enums.DonationStatus;
import com.riseai.enums.RefundStatus;
import com.riseai.enums.UserStatus;
import com.riseai.repository.CampaignRepository;
import com.riseai.repository.DisbursementRepository;
import com.riseai.repository.DisputeRepository;
import com.riseai.repository.DonationRepository;
import com.riseai.repository.EscrowAccountRepository;
import com.riseai.repository.RefundRepository;
import com.riseai.repository.UserRepository;
import com.riseai.service.AdminStatisticsService;
import com.riseai.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatisticsServiceImpl implements AdminStatisticsService {
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final DonationRepository donationRepository;
    private final DisbursementRepository disbursementRepository;
    private final DisputeRepository disputeRepository;
    private final RefundRepository refundRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final RiseProperties properties;

    @Transactional(readOnly = true)
    @Override
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
