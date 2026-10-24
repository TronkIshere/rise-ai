package com.riseai.disbursement;

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
@Table(name = "disbursement_requests")
@Getter
@Setter
@NoArgsConstructor
public class DisbursementRequest extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String requestCode;
    @Column(nullable = false)
    private UUID campaignId;
    @Column(nullable = false)
    private UUID milestoneId;
    @Column(nullable = false)
    private UUID requestedBy;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false)
    private String requestReason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisbursementStatus requestStatus;
    @Enumerated(EnumType.STRING)
    private DisbursementStatus preDisputeStatus;
    @Column(nullable = false)
    private Instant submittedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String decisionReason;
    private UUID releasedBy;
    private Instant releasedAt;
    @Version
    private Long version;
}
