package com.riseai.repository;

import com.riseai.entity.Milestone;
import com.riseai.enums.MilestoneStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findByCampaignIdOrderBySequenceNumberAsc(UUID campaignId);

    Optional<Milestone> findFirstByCampaignIdAndMilestoneStatusOrderBySequenceNumberAsc(UUID campaignId, MilestoneStatus status);

    boolean existsByCampaignIdAndMilestoneStatusIn(UUID campaignId, Collection<MilestoneStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Milestone m where m.id = :id")
    Optional<Milestone> findByIdForUpdate(UUID id);

    @Query("select sum(m.plannedAmount) from Milestone m where m.campaignId = :campaignId")
    BigDecimal sumPlannedAmount(UUID campaignId);

    @Query("select sum(m.plannedAmount) from Milestone m where m.campaignId = :campaignId and m.id <> :excludedId")
    BigDecimal sumPlannedAmountExcluding(UUID campaignId, UUID excludedId);

    @Query("select max(m.sequenceNumber) from Milestone m where m.campaignId = :campaignId")
    Integer maxSequence(UUID campaignId);
}
