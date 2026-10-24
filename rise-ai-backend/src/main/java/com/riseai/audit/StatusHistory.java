package com.riseai.audit;

import com.riseai.common.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

@Entity
@Immutable
@Table(name = "status_history")
@Getter
@Setter
@NoArgsConstructor
public class StatusHistory extends CreatedEntity {
    @Column(nullable = false)
    private String entityType;
    @Column(nullable = false)
    private UUID entityId;
    private String fromStatus;
    @Column(nullable = false)
    private String toStatus;
    private UUID changedBy;
    private String reason;
}
