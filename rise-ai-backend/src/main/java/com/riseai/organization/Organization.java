package com.riseai.organization;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization extends BaseEntity {
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String slug;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationType organizationType;
    @Column(nullable = false)
    private String contactEmail;
    private String contactPhone;
    private String address;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;
    private String verificationNotes;
    private Instant verifiedAt;
    @Column(nullable = false)
    private UUID createdBy;
    @Version
    private Long version;
}
