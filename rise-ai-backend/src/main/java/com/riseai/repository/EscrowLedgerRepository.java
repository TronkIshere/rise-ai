package com.riseai.repository;

import com.riseai.entity.EscrowLedgerEntry;
import com.riseai.enums.LedgerTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EscrowLedgerRepository extends JpaRepository<EscrowLedgerEntry, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<EscrowLedgerEntry> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<EscrowLedgerEntry> findByCampaignIdAndTransactionType(UUID campaignId, LedgerTransactionType type, Pageable pageable);

    @Query("select e.transactionType, sum(e.amount), count(e) from EscrowLedgerEntry e where e.escrowAccountId = :accountId group by e.transactionType")
    List<Object[]> sumByType(UUID accountId);
}
