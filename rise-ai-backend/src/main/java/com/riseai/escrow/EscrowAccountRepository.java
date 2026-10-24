package com.riseai.escrow;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {
    Optional<EscrowAccount> findByCampaignIdAndCurrency(UUID campaignId, String currency);

    List<EscrowAccount> findByCampaignIdIn(Collection<UUID> campaignIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EscrowAccount a where a.campaignId = :campaignId and a.currency = :currency")
    Optional<EscrowAccount> lockByCampaignIdAndCurrency(UUID campaignId, String currency);

    @Query("select new com.riseai.escrow.EscrowTotals(sum(a.totalConfirmedContributions), sum(a.totalReserved), sum(a.totalDisbursed), sum(a.totalRefunded), sum(a.availableBalance)) from EscrowAccount a where a.currency = :currency")
    EscrowTotals totals(String currency);
}
