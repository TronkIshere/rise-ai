package com.riseai.campaign;

import com.riseai.common.Money;
import com.riseai.escrow.EscrowAccount;
import com.riseai.organization.OrganizationRepository;
import com.riseai.organization.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CampaignDtos {
    private CampaignDtos() {
    }

    public record CampaignRequest(
            @NotNull UUID organizationId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 500) String shortDescription,
            @NotBlank @Size(max = 20000) String fullDescription,
            @Size(max = 500) @Pattern(regexp = "^(https://\\S+)?$") String coverImageUrl,
            @Size(max = 10) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https://\\S+$") String> galleryImageUrls,
            @NotNull CampaignCategory category,
            @NotBlank @Size(max = 2000) String beneficiaryDescription,
            @NotNull @DecimalMin("1000.00") @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
            @Size(min = 3, max = 3) String currency,
            @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal minimumDonationAmount,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotBlank @Size(max = 255) String location,
            @NotNull @Positive Integer beneficiaryCount,
            @NotBlank @Size(max = 5000) String problemStatement,
            @NotBlank @Size(max = 5000) String solutionDescription,
            @NotBlank @Size(max = 5000) String impactGoals,
            @Size(max = 50) List<@Valid BudgetItem> budgetBreakdown) {
    }

    public record OrganizationSummary(UUID id, String name, String slug, VerificationStatus verificationStatus) {
        static OrganizationSummary from(OrganizationRepository.SummaryView o) {
            return o == null ? null : new OrganizationSummary(o.getId(), o.getName(), o.getSlug(), o.getVerificationStatus());
        }
    }

    public record FundingSummary(BigDecimal totalConfirmed, BigDecimal totalRefunded, BigDecimal netRaised, BigDecimal totalReserved,
                                 BigDecimal totalDisbursed, BigDecimal availableBalance, BigDecimal progressPercent) {
        static FundingSummary from(EscrowAccount a, BigDecimal target) {
            if (a == null) {
                return new FundingSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        Money.percent(BigDecimal.ZERO, target));
            }
            BigDecimal net = a.getTotalConfirmedContributions().subtract(a.getTotalRefunded());
            return new FundingSummary(a.getTotalConfirmedContributions(), a.getTotalRefunded(), net, a.getTotalReserved(),
                    a.getTotalDisbursed(), a.getAvailableBalance(), Money.percent(net, target));
        }
    }

    public record CampaignResponse(UUID id, String campaignCode, UUID creatorId, OrganizationSummary organization, String title, String slug,
                                   String shortDescription, String fullDescription, String coverImageUrl, List<String> galleryImageUrls,
                                   CampaignCategory category, String beneficiaryDescription, BigDecimal targetAmount, String currency,
                                   BigDecimal minimumDonationAmount, Instant startAt, Instant endAt, String location, Integer beneficiaryCount,
                                   String problemStatement, String solutionDescription, String impactGoals, List<BudgetItem> budgetBreakdown,
                                   CampaignStatus campaignStatus, ReviewStatus reviewStatus, String reviewNote, Instant publishedAt,
                                   Instant completedAt, Instant cancelledAt, Instant createdAt, Instant updatedAt, FundingSummary funding,
                                   Instant fundraisingClosedAt, boolean acceptingDonations) {
        public static CampaignResponse from(Campaign c, OrganizationRepository.SummaryView org, EscrowAccount account) {
            return new CampaignResponse(c.getId(), c.getCampaignCode(), c.getCreatorId(), OrganizationSummary.from(org), c.getTitle(), c.getSlug(),
                    c.getShortDescription(), c.getFullDescription(), c.getCoverImageUrl(), c.getGalleryImageUrls(), c.getCategory(),
                    c.getBeneficiaryDescription(), c.getTargetAmount(), c.getCurrency(), c.getMinimumDonationAmount(), c.getStartAt(),
                    c.getEndAt(), c.getLocation(), c.getBeneficiaryCount(), c.getProblemStatement(), c.getSolutionDescription(),
                    c.getImpactGoals(), c.getBudgetBreakdown(), c.getCampaignStatus(), c.getReviewStatus(), c.getReviewNote(),
                    c.getPublishedAt(), c.getCompletedAt(), c.getCancelledAt(), c.getCreatedAt(), c.getUpdatedAt(),
                    FundingSummary.from(account, c.getTargetAmount()), c.getFundraisingClosedAt(), c.isAcceptingDonations(Instant.now()));
        }
    }

    public record CampaignStatistics(UUID campaignId, String currency, BigDecimal targetAmount, FundingSummary funding,
                                     long confirmedDonationCount, long uniqueDonorCount, long pendingDonationCount,
                                     long milestoneCount, long completedMilestoneCount, long openDisbursementCount, long openDisputeCount) {
    }
}
