package com.riseai.repository;

import com.riseai.entity.AlertNotification;
import com.riseai.enums.AlertStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertNotificationRepository extends JpaRepository<AlertNotification, UUID> {
    @Query(value = """
            select id from alert_notifications
            where status in ('PENDING', 'RETRYING') and next_attempt_at <= :now
            order by created_at, id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<String> lockDueIds(Instant now, int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlertNotification a where a.id = :id")
    Optional<AlertNotification> lockById(UUID id);

    long countByStatus(AlertStatus status);

    Page<AlertNotification> findByStatus(AlertStatus status, Pageable pageable);

    @Query("select min(a.createdAt) from AlertNotification a where a.status in (com.riseai.enums.AlertStatus.PENDING, com.riseai.enums.AlertStatus.RETRYING)")
    Optional<Instant> oldestUndeliveredCreatedAt();

    @Modifying
    @Query("delete from AlertNotification a where a.status = com.riseai.enums.AlertStatus.SENT and a.deliveredAt < :before")
    int deleteSentBefore(Instant before);
}
