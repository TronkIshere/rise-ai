package com.riseai.repository;

import com.riseai.entity.EscrowAdjustment;
import com.riseai.enums.AdjustmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AdjustmentRepository extends JpaRepository<EscrowAdjustment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EscrowAdjustment a where a.id = :id")
    Optional<EscrowAdjustment> findByIdForUpdate(UUID id);

    Page<EscrowAdjustment> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<EscrowAdjustment> findByAdjustmentStatus(AdjustmentStatus status, Pageable pageable);
}
