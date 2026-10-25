package com.riseai.entity;

import com.riseai.enums.SettlementStatus;
import com.riseai.enums.SettlementType;
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
@Table(name = "settlement_requests")
@Getter
@Setter
@NoArgsConstructor
public class SettlementRequest extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String settlementCode;
    @Column(nullable = false)
    private UUID campaignId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementType settlementType;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus settlementStatus;
    @Column(nullable = false)
    private String reason;
    @Column(nullable = false)
    private UUID proposedBy;
    private UUID decidedBy;
    private Instant decidedAt;
    private String decisionReason;
    private Instant executedAt;
    private Integer donationCount;
    @Version
    private Long version;
}
