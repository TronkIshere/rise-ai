package com.riseai.donation;

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
@Table(name = "donations")
@Getter
@Setter
@NoArgsConstructor
public class Donation extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String donationCode;
    @Column(nullable = false)
    private UUID campaignId;
    @Column(nullable = false)
    private UUID donorId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationStatus donationStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;
    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;
    private String donorMessage;
    private String simulatedReference;
    private String idempotencyKey;
    private Instant confirmedAt;
    @Version
    private Long version;

    public BigDecimal refundableAmount() {
        return amount.subtract(refundedAmount);
    }
}
