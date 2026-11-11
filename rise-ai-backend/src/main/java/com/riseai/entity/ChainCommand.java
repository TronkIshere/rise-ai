package com.riseai.entity;

import com.riseai.enums.ChainCommandStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chain_commands")
@Getter
@Setter
@NoArgsConstructor
public class ChainCommand extends BaseEntity {
    @Column(nullable = false, updatable = false)
    private UUID campaignId;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(nullable = false, updatable = false, length = 12000)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChainCommandStatus status;
    @Column(nullable = false)
    private int attempt;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private String txHash;
    private String lastError;
    @Column(updatable = false)
    private UUID referenceId;
    @Column(updatable = false)
    private UUID actorId;
    @Version
    private Long version;

    public String operationKey() {
        return "cmd-" + getId() + "-" + attempt;
    }
}
