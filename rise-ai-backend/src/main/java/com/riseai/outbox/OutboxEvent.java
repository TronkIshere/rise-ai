package com.riseai.outbox;

import com.riseai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent extends BaseEntity {
    @Column(nullable = false, unique = true, updatable = false)
    private UUID eventId;
    @Column(nullable = false, updatable = false)
    private String eventType;
    @Column(nullable = false, updatable = false)
    private int schemaVersion;
    @Column(nullable = false, updatable = false)
    private String aggregateType;
    @Column(nullable = false, updatable = false)
    private UUID aggregateId;
    @Column(updatable = false)
    private UUID actorId;
    @Column(updatable = false)
    private String correlationId;
    @Column(nullable = false, updatable = false)
    private String envelope;
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private String lastError;
    private Instant publishedAt;
    @Version
    private Long version;
}
