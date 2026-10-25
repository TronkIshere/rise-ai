package com.riseai.repository;

import com.riseai.entity.ReconciliationRun;
import com.riseai.enums.ReconciliationRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
    Page<ReconciliationRun> findByRunStatus(ReconciliationRunStatus status, Pageable pageable);
}
