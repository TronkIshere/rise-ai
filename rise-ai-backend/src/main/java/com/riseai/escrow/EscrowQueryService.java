package com.riseai.escrow;

import com.riseai.campaign.Campaign;
import com.riseai.campaign.CampaignService;
import com.riseai.common.Money;
import com.riseai.common.exception.ApplicationException;
import com.riseai.escrow.EscrowDtos.Balances;
import com.riseai.escrow.EscrowDtos.EscrowSummary;
import com.riseai.escrow.EscrowDtos.LedgerEntryResponse;
import com.riseai.escrow.EscrowDtos.ReconciliationReport;
import com.riseai.milestone.MilestoneRepository;
import com.riseai.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EscrowQueryService {
    private final EscrowAccountRepository accountRepository;
    private final EscrowLedgerRepository ledgerRepository;
    private final MilestoneRepository milestoneRepository;
    private final CampaignService campaignService;

    public EscrowSummary summary(AuthUser actor, UUID campaignId) {
        Campaign campaign = campaignService.loadVisible(campaignId, actor);
        BigDecimal planned = Money.orZero(milestoneRepository.sumPlannedAmount(campaignId));
        return accountRepository.findByCampaignIdAndCurrency(campaignId, campaign.getCurrency())
                .map(a -> new EscrowSummary(campaignId, a.getId(), a.getCurrency(), a.getEscrowStatus(), a.getTotalConfirmedContributions(),
                        a.getTotalReserved(), a.getTotalDisbursed(), a.getTotalRefunded(), a.getAvailableBalance(), a.heldBalance(),
                        planned, true, EscrowDtos.SIMULATION_DISCLAIMER))
                .orElseGet(() -> new EscrowSummary(campaignId, null, campaign.getCurrency(), null, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, planned, true, EscrowDtos.SIMULATION_DISCLAIMER));
    }

    public Page<LedgerEntryResponse> ledger(AuthUser actor, UUID campaignId, LedgerTransactionType type, Pageable pageable) {
        campaignService.loadVisible(campaignId, actor);
        Page<EscrowLedgerEntry> page = type == null
                ? ledgerRepository.findByCampaignId(campaignId, pageable)
                : ledgerRepository.findByCampaignIdAndTransactionType(campaignId, type, pageable);
        return page.map(LedgerEntryResponse::from);
    }

    public ReconciliationReport reconcile(UUID campaignId) {
        Campaign campaign = campaignService.load(campaignId);
        EscrowAccount account = accountRepository.findByCampaignIdAndCurrency(campaignId, campaign.getCurrency())
                .orElseThrow(() -> ApplicationException.notFound("Escrow account"));
        return reconcile(account);
    }

    public List<ReconciliationReport> reconcileAll() {
        return accountRepository.findAll().stream().map(this::reconcile).toList();
    }

    private ReconciliationReport reconcile(EscrowAccount account) {
        Map<LedgerTransactionType, BigDecimal> sums = new EnumMap<>(LedgerTransactionType.class);
        long count = 0;
        for (Object[] row : ledgerRepository.sumByType(account.getId())) {
            sums.put((LedgerTransactionType) row[0], (BigDecimal) row[1]);
            count += ((Number) row[2]).longValue();
        }
        BigDecimal confirmed = sums.getOrDefault(LedgerTransactionType.CONTRIBUTION_CONFIRMED, BigDecimal.ZERO);
        BigDecimal released = sums.getOrDefault(LedgerTransactionType.FUNDS_RELEASED, BigDecimal.ZERO);
        BigDecimal refunded = sums.getOrDefault(LedgerTransactionType.REFUND_RECORDED, BigDecimal.ZERO);
        BigDecimal reserved = sums.getOrDefault(LedgerTransactionType.FUNDS_RESERVED, BigDecimal.ZERO)
                .subtract(sums.getOrDefault(LedgerTransactionType.RESERVATION_CANCELLED, BigDecimal.ZERO))
                .subtract(released);
        BigDecimal available = confirmed.subtract(reserved).subtract(released).subtract(refunded);
        Balances computed = new Balances(confirmed, reserved, released, refunded, available);
        Balances stored = new Balances(account.getTotalConfirmedContributions(), account.getTotalReserved(), account.getTotalDisbursed(),
                account.getTotalRefunded(), account.getAvailableBalance());
        boolean consistent = equal(confirmed, stored.confirmed()) && equal(reserved, stored.reserved()) && equal(released, stored.disbursed())
                && equal(refunded, stored.refunded()) && equal(available, stored.available());
        return new ReconciliationReport(account.getCampaignId(), account.getId(), account.getCurrency(), stored, computed, count, consistent);
    }

    private static boolean equal(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }
}
