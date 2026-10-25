package com.riseai.entity;

import com.riseai.enums.DisputeStatus;
import com.riseai.enums.DisputeType;
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
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
public class Dispute extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String disputeCode;
    @Column(nullable = false)
    private UUID campaignId;
    private UUID milestoneId;
    private UUID disbursementRequestId;
    @Column(nullable = false)
    private UUID openedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeType disputeType;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> supportingReferences = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus disputeStatus;
    private UUID assignedTo;
    private String resolution;
    private UUID resolvedBy;
    private Instant resolvedAt;
    @Version
    private Long version;
}
