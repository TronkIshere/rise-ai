package com.riseai.dto.response;

import com.riseai.entity.BudgetItem;
import com.riseai.entity.Campaign;
import com.riseai.entity.EscrowAccount;
import com.riseai.enums.CampaignCategory;
import com.riseai.enums.CampaignStatus;
import com.riseai.enums.ReviewStatus;
import com.riseai.repository.OrganizationRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
