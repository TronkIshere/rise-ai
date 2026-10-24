package com.riseai.dispute;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForUpdate(UUID id);

    boolean existsByMilestoneIdAndDisputeStatusIn(UUID milestoneId, Collection<DisputeStatus> statuses);

    long countByCampaignIdAndDisputeStatusIn(UUID campaignId, Collection<DisputeStatus> statuses);

    long countByDisputeStatusIn(Collection<DisputeStatus> statuses);

    @Query("select count(d) from Dispute d where d.openedBy = :openedBy and d.campaignId = :campaignId and ((:milestoneId is null and d.milestoneId is null) or d.milestoneId = :milestoneId) and ((:requestId is null and d.disbursementRequestId is null) or d.disbursementRequestId = :requestId) and d.disputeStatus in :statuses")
    long countOpenDuplicates(UUID openedBy, UUID campaignId, UUID milestoneId, UUID requestId, Collection<DisputeStatus> statuses);

    Page<Dispute> findByOpenedBy(UUID openedBy, Pageable pageable);

    Page<Dispute> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<Dispute> findByDisputeStatus(DisputeStatus status, Pageable pageable);
}
