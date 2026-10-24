package com.riseai.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<SettlementRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SettlementRequest s where s.id = :id")
    Optional<SettlementRequest> findByIdForUpdate(UUID id);

    boolean existsByCampaignIdAndSettlementStatus(UUID campaignId, SettlementStatus status);

    Page<SettlementRequest> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<SettlementRequest> findBySettlementStatus(SettlementStatus status, Pageable pageable);
}
