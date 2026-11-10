package com.riseai.repository;

import com.riseai.entity.DisbursementRequest;
import com.riseai.enums.DisbursementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisbursementRepository extends JpaRepository<DisbursementRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DisbursementRequest r where r.id = :id")
    Optional<DisbursementRequest> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DisbursementRequest r where r.milestoneId = :milestoneId and r.requestStatus in :statuses order by r.submittedAt")
    List<DisbursementRequest> lockByMilestoneAndStatuses(UUID milestoneId, Collection<DisbursementStatus> statuses);

    Optional<DisbursementRequest> findByRequestedByAndIdempotencyKey(UUID requestedBy, String idempotencyKey);

    boolean existsByMilestoneIdAndRequestStatusIn(UUID milestoneId, Collection<DisbursementStatus> statuses);

    boolean existsByCampaignIdAndRequestStatusIn(UUID campaignId, Collection<DisbursementStatus> statuses);

    long countByCampaignIdAndRequestStatusIn(UUID campaignId, Collection<DisbursementStatus> statuses);

    long countByRequestStatusIn(Collection<DisbursementStatus> statuses);

    Page<DisbursementRequest> findByCampaignId(UUID campaignId, Pageable pageable);

    List<DisbursementRequest> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    Page<DisbursementRequest> findByRequestStatus(DisbursementStatus status, Pageable pageable);
}
