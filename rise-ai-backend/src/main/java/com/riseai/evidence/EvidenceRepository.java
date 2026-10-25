package com.riseai.evidence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<EvidenceRecord, UUID> {
    boolean existsByMilestoneIdAndEvidenceStatusIn(UUID milestoneId, Collection<EvidenceStatus> statuses);

    long countByMilestoneId(UUID milestoneId);

    long countByMilestoneIdAndEvidenceStatus(UUID milestoneId, EvidenceStatus status);

    Page<EvidenceRecord> findByMilestoneId(UUID milestoneId, Pageable pageable);

    Page<EvidenceRecord> findByDisbursementRequestId(UUID disbursementRequestId, Pageable pageable);
}
