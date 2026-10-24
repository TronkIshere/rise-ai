package com.riseai.milestone;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_milestones")
@Getter
@Setter
@NoArgsConstructor
public class Milestone extends BaseEntity {
    @Column(nullable = false)
    private UUID campaignId;
    @Column(nullable = false)
    private String title;
    private String description;
    @Column(nullable = false)
    private Integer sequenceNumber;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal plannedAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal releasedAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private Instant startAt;
    @Column(nullable = false)
    private Instant dueAt;
    @Column(nullable = false)
    private String releaseConditions;
    @Column(nullable = false)
    private String expectedDeliverables;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneStatus milestoneStatus;
    @Enumerated(EnumType.STRING)
    private MilestoneStatus preDisputeStatus;
    private Instant submittedAt;
    private Instant reviewedAt;
    private Instant completedAt;
    @Version
    private Long version;

    public BigDecimal remainingAmount() {
        return plannedAmount.subtract(reservedAmount).subtract(releasedAmount);
    }
}
