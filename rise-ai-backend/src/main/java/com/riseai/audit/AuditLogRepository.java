package com.riseai.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Query("select a from AuditLog a where (:actorId is null or a.actorUserId = :actorId) and (:entityType is null or a.entityType = :entityType) and (:entityId is null or a.entityId = :entityId) and (:action is null or a.action = :action)")
    Page<AuditLog> search(UUID actorId, String entityType, UUID entityId, String action, Pageable pageable);

    Page<AuditLog> findByActorUserId(UUID actorUserId, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}
