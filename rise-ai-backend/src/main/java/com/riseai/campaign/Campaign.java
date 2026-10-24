package com.riseai.campaign;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
public class Campaign extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String campaignCode;
    @Column(nullable = false)
    private UUID creatorId;
    @Column(nullable = false)
    private UUID organizationId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, unique = true)
    private String slug;
    @Column(nullable = false)
    private String shortDescription;
    @Column(nullable = false)
    private String fullDescription;
    private String coverImageUrl;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> galleryImageUrls = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignCategory category;
    @Column(nullable = false)
    private String beneficiaryDescription;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumDonationAmount;
    @Column(nullable = false)
    private Instant startAt;
    @Column(nullable = false)
    private Instant endAt;
    @Column(nullable = false)
    private String location;
    @Column(nullable = false)
    private Integer beneficiaryCount;
    @Column(nullable = false)
    private String problemStatement;
    @Column(nullable = false)
    private String solutionDescription;
    @Column(nullable = false)
    private String impactGoals;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<BudgetItem> budgetBreakdown = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus campaignStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;
    private String reviewNote;
    private Instant publishedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    @Version
    private Long version;
}
