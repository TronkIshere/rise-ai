package com.riseai.entity;

import com.riseai.enums.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "alert_notifications")
@Getter
@Setter
@NoArgsConstructor
public class AlertNotification extends BaseEntity {
    @Column(nullable = false, updatable = false)
    private String kind;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false, updatable = false)
    private Map<String, String> details = new LinkedHashMap<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private String lastError;
    private Instant deliveredAt;
    private Instant failedAt;
}
