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

/** One Merkle root of an append-only record set, as sent to the RiseAnchor contract. */
@Entity
@Table(name = "chain_anchors")
@Getter
@Setter
@NoArgsConstructor
public class ChainAnchor extends BaseEntity {
    @Column(nullable = false, updatable = false)
    private String kind;
    @Column(nullable = false, updatable = false)
    private long recordCount;
    @Column(nullable = false, updatable = false, length = 66)
    private String root;
    @Column(nullable = false, updatable = false, length = 66)
    private String previousRoot;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChainCommandStatus status;
    private String txHash;
    private String lastError;
    @Version
    private Long version;

    public String operationKey() {
        return "anchor-" + getId();
    }
}
