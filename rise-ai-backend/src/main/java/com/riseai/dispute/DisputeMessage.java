package com.riseai.dispute;

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
@Table(name = "dispute_messages")
@Getter
@Setter
@NoArgsConstructor
public class DisputeMessage extends CreatedEntity {
    @Column(nullable = false)
    private UUID disputeId;
    @Column(nullable = false)
    private UUID authorId;
    @Column(nullable = false)
    private String message;
}
