package com.riseai.escrow;

import com.riseai.common.CreatedEntity;
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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "escrow_ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class EscrowLedgerEntry extends CreatedEntity {
    @Column(nullable = false, unique = true)
    private String transactionCode;
    @Column(nullable = false)
    private UUID campaignId;
    @Column(nullable = false)
    private UUID escrowAccountId;
    private UUID donationId;
    private UUID milestoneId;
    private UUID disbursementId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerTransactionType transactionType;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerDirection direction;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryStatus status;
    @Column(nullable = false, unique = true)
    private String idempotencyKey;
    @Column(nullable = false)
    private String referenceType;
    @Column(nullable = false)
    private UUID referenceId;
    private String description;
    private UUID createdBy;
}
