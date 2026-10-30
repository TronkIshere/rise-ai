package com.riseai.entity;

import com.riseai.enums.FindingSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "monitoring_finding_occurrences")
@Getter
@Setter
@NoArgsConstructor
public class FindingOccurrence extends CreatedEntity {
    @Column(nullable = false)
    private UUID findingId;
    @Column(nullable = false)
    private UUID sourceEventId;
    private UUID actorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSeverity severity;
    @Column(nullable = false)
    private Instant observedAt;
    @Column(length = 400)
    private String aiNote;
}
