package com.riseai.evidence;

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
import java.util.UUID;

@Entity
@Table(name = "evidence_records")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceRecord extends BaseEntity {
    @Column(nullable = false)
    private UUID campaignId;
    @Column(nullable = false)
    private UUID milestoneId;
    private UUID disbursementRequestId;
    @Column(nullable = false)
    private UUID submittedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceType evidenceType;
    @Column(nullable = false)
    private String title;
    private String description;
    private String fileUrl;
    @JdbcTypeCode(SqlTypes.CHAR)
    private String fileHash;
    private String externalReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceStatus evidenceStatus;
    @Column(nullable = false)
    private Instant submittedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;
    @Version
    private Long version;
}
