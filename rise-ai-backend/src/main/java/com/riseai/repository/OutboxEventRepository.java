package com.riseai.repository;

import com.riseai.entity.OutboxEvent;
import com.riseai.enums.OutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            select id from outbox_events
            where status in ('PENDING', 'IN_FLIGHT') and next_attempt_at <= :now and (lease_until is null or lease_until < :now)
            order by occurred_at, id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<String> lockDueIds(Instant now, int limit);

    Optional<OutboxEvent> findByEventId(UUID eventId);

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, UUID aggregateId);

    List<OutboxEvent> findByEventTypeOrderByOccurredAtAsc(String eventType);

    Page<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Query("select min(o.occurredAt) from OutboxEvent o where o.status in :statuses")
    Optional<Instant> oldestOccurredAt(List<OutboxStatus> statuses);

    @Modifying
    @Query("delete from OutboxEvent o where o.status = com.riseai.enums.OutboxStatus.PUBLISHED and o.publishedAt < :before")
    int deletePublishedBefore(Instant before);
}
