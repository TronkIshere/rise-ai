package com.riseai.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record SystemStatistics(long totalUsers, long activeUsers, long suspendedUsers, Map<String, Long> campaignsByStatus,
                               long pendingDonations, long openDisbursementRequests, long openDisputes, long openRefundRequests,
                               String currency, BigDecimal totalConfirmedContributions, BigDecimal totalReserved,
                               BigDecimal totalDisbursed, BigDecimal totalRefunded, BigDecimal totalAvailable, boolean simulated) {
}
