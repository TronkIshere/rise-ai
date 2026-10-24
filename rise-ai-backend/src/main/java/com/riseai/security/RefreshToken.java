package com.riseai.security;

import com.riseai.common.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends CreatedEntity {
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, unique = true)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;
}
