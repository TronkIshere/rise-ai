package com.riseai.escrow;

import com.riseai.common.CreatedEntity;
import com.riseai.escrow.EscrowDtos.ReconciliationReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "reconciliation_runs")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationRun extends CreatedEntity {
    @Column(nullable = false, unique = true)
    private String runCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationTrigger triggerType;
    private UUID triggeredBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationRunStatus runStatus;
    @Column(nullable = false)
    private Instant startedAt;
    @Column(nullable = false)
    private Instant finishedAt;
    @Column(nullable = false)
    private Integer accountsChecked;
    @Column(nullable = false)
    private Integer inconsistentAccounts;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<ReconciliationReport> findings = new ArrayList<>();
    private String errorMessage;
}
