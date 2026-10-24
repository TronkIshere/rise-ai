package com.riseai.refund;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_requests")
@Getter
@Setter
@NoArgsConstructor
public class RefundRequest extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String refundCode;
    @Column(nullable = false)
    private UUID donationId;
    @Column(nullable = false)
    private UUID campaignId;
    @Column(nullable = false)
    private UUID requestedBy;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false)
    private boolean fundsReserved;
    @Column(nullable = false)
    private String reason;
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus refundStatus;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String decisionReason;
    private UUID recordedBy;
    private Instant recordedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalPaymentStatus externalPaymentStatus = ExternalPaymentStatus.NOT_EXECUTED_SIMULATION;
    @Version
    private Long version;
}
