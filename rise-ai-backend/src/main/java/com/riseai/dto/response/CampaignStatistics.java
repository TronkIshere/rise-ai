package com.riseai.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CampaignStatistics(UUID campaignId, String currency, BigDecimal targetAmount, FundingSummary funding,
                                 long confirmedDonationCount, long uniqueDonorCount, long pendingDonationCount,
                                 long milestoneCount, long completedMilestoneCount, long openDisbursementCount, long openDisputeCount) {
}
