package com.riseai.repository;

import com.riseai.entity.MonitoringFinding;
import com.riseai.enums.FindingReviewStatus;
import com.riseai.enums.FindingSeverity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoringFindingRepository extends JpaRepository<MonitoringFinding, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from MonitoringFinding f where f.findingKey = :findingKey")
    Optional<MonitoringFinding> findByFindingKeyForUpdate(String findingKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from MonitoringFinding f where f.id = :id")
    Optional<MonitoringFinding> findByIdForUpdate(UUID id);

    @Query("select f from MonitoringFinding f where (:status is null or f.reviewStatus = :status) and (:severity is null or f.severity = :severity) and (:category is null or f.category = :category) and (:campaignId is null or f.campaignId = :campaignId)")
    Page<MonitoringFinding> search(FindingReviewStatus status, FindingSeverity severity, String category, UUID campaignId, Pageable pageable);

    @Query("select f.ruleId, f.reviewStatus, count(f) from MonitoringFinding f group by f.ruleId, f.reviewStatus")
    List<Object[]> countByRuleAndReviewStatus();
}
