package com.riseai.adjustment;

import com.riseai.common.BaseEntity;
import com.riseai.escrow.LedgerDirection;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escrow_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class EscrowAdjustment extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String adjustmentCode;
    @Column(nullable = false)
    private UUID campaignId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerDirection direction;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentStatus adjustmentStatus;
    @Column(nullable = false)
    private String reason;
    @Column(nullable = false)
    private UUID requestedBy;
    private UUID decidedBy;
    private Instant decidedAt;
    private String decisionReason;
    @Version
    private Long version;
}
