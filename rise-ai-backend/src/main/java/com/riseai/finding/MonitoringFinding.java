package com.riseai.finding;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "monitoring_findings")
@Getter
@Setter
@NoArgsConstructor
public class MonitoringFinding extends BaseEntity {
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, length = 64, updatable = false)
    private String findingKey;
    @Column(nullable = false)
    private String contractVersion;
    @Column(nullable = false)
    private String detectorType;
    @Column(nullable = false)
    private String ruleId;
    @Column(nullable = false)
    private String ruleVersion;
    @Column(nullable = false)
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSeverity severity;
    @Column(nullable = false)
    private String subjectType;
    @Column(nullable = false)
    private String subjectId;
    private UUID campaignId;
    private UUID milestoneId;
    private UUID disbursementRequestId;
    @Column(nullable = false)
    private String explanation;
    @Column(nullable = false)
    private String recommendedAction;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Map<String, Object>> facts = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingReviewStatus reviewStatus;
    @Column(nullable = false)
    private int occurrenceCount;
    @Column(nullable = false)
    private int newOccurrencesSinceReview;
    @Column(nullable = false)
    private Instant firstSeenAt;
    @Column(nullable = false)
    private Instant lastSeenAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;
    @Version
    private Long version;
}
