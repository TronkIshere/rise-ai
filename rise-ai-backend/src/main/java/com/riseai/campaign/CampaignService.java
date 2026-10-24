package com.riseai.campaign;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.audit.AuditService;
import com.riseai.campaign.CampaignDtos.CampaignRequest;
import com.riseai.campaign.CampaignDtos.CampaignResponse;
import com.riseai.campaign.CampaignDtos.CampaignStatistics;
import com.riseai.campaign.CampaignDtos.FundingSummary;
import com.riseai.common.Codes;
import com.riseai.common.Money;
import com.riseai.common.Slugs;
import com.riseai.common.States;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import com.riseai.config.RiseProperties;
import com.riseai.disbursement.DisbursementRepository;
import com.riseai.disbursement.DisbursementStatus;
import com.riseai.dispute.DisputeRepository;
import com.riseai.dispute.DisputeStatus;
import com.riseai.donation.DonationRepository;
import com.riseai.donation.DonationStatus;
import com.riseai.escrow.EscrowAccount;
import com.riseai.escrow.EscrowAccountRepository;
import com.riseai.escrow.EscrowEngine;
import com.riseai.escrow.EscrowStatus;
import com.riseai.milestone.Milestone;
import com.riseai.milestone.MilestoneRepository;
import com.riseai.milestone.MilestoneStatus;
import com.riseai.organization.Organization;
import com.riseai.organization.OrganizationRepository;
import com.riseai.organization.VerificationStatus;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignService {
    public static final Set<CampaignStatus> PUBLIC_STATUSES = EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED, CampaignStatus.COMPLETED);
    private static final Set<CampaignStatus> EDITABLE = EnumSet.of(CampaignStatus.DRAFT, CampaignStatus.REJECTED);
    private static final Set<CampaignStatus> OWNER_CANCELLABLE = EnumSet.of(CampaignStatus.DRAFT, CampaignStatus.PENDING_REVIEW, CampaignStatus.APPROVED, CampaignStatus.REJECTED);
    private static final Set<MilestoneStatus> CANCELLABLE_MILESTONES = EnumSet.of(MilestoneStatus.PLANNED, MilestoneStatus.READY, MilestoneStatus.IN_PROGRESS, MilestoneStatus.EVIDENCE_SUBMITTED);
    private static final String ENTITY = "CAMPAIGN";

    private final CampaignRepository campaignRepository;
    private final OrganizationRepository organizationRepository;
    private final MilestoneRepository milestoneRepository;
    private final DisbursementRepository disbursementRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final DonationRepository donationRepository;
    private final DisputeRepository disputeRepository;
    private final EscrowEngine escrowEngine;
    private final AuditService auditService;
    private final RiseProperties properties;

    @Transactional
    public CampaignResponse create(AuthUser actor, CampaignRequest request) {
        AuthUser.require(actor).requireRole(Role.CAMPAIGN_CREATOR);
        Campaign campaign = new Campaign();
        campaign.setCreatorId(actor.id());
        campaign.setCampaignCode(Codes.generate("CMP"));
        apply(campaign, request, actor);
        campaign.setSlug(uniqueSlug(request.title()));
        campaign.setCampaignStatus(CampaignStatus.DRAFT);
        campaign.setReviewStatus(ReviewStatus.NOT_SUBMITTED);
        campaignRepository.saveAndFlush(campaign);
        auditService.transition(actor.id(), "CAMPAIGN_CREATED", ENTITY, campaign.getId(), null, CampaignStatus.DRAFT, null);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse update(AuthUser actor, UUID id, CampaignRequest request) {
        Campaign campaign = loadOwned(actor, id);
        States.require(campaign.getCampaignStatus(), EDITABLE);
        apply(campaign, request, actor);
        auditService.record(actor.id(), "CAMPAIGN_UPDATED", ENTITY, id, null, null, null);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse submit(AuthUser actor, UUID id) {
        Campaign campaign = loadOwned(actor, id);
        States.require(campaign.getCampaignStatus(), EDITABLE);
        List<Milestone> milestones = milestoneRepository.findByCampaignIdOrderBySequenceNumberAsc(id);
        if (milestones.isEmpty()) {
            throw new ApplicationException(ErrorCode.BUDGET_MISMATCH, "At least one milestone is required before submission");
        }
        if (Money.sum(milestones.stream().map(Milestone::getPlannedAmount)).compareTo(campaign.getTargetAmount()) != 0) {
            throw new ApplicationException(ErrorCode.BUDGET_MISMATCH, "Milestone planned amounts must sum to targetAmount");
        }
        if (!campaign.getEndAt().isAfter(Instant.now())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Campaign end date has already passed");
        }
        if (organization(campaign).getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ApplicationException(ErrorCode.ORGANIZATION_NOT_VERIFIED, "Organization was rejected by platform review");
        }
        campaign.setReviewStatus(ReviewStatus.PENDING);
        move(campaign, CampaignStatus.PENDING_REVIEW, actor, "CAMPAIGN_SUBMITTED_FOR_REVIEW", null);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse approve(AuthUser actor, UUID id, String reason) {
        Campaign campaign = loadForReview(actor, id);
        if (organization(campaign).getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApplicationException(ErrorCode.ORGANIZATION_NOT_VERIFIED);
        }
        if (!campaign.getEndAt().isAfter(Instant.now())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Campaign end date has already passed");
        }
        campaign.setReviewStatus(ReviewStatus.APPROVED);
        campaign.setReviewNote(reason);
        move(campaign, CampaignStatus.APPROVED, actor, "CAMPAIGN_APPROVED", reason);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse reject(AuthUser actor, UUID id, String reason) {
        Campaign campaign = loadForReview(actor, id);
        campaign.setReviewStatus(ReviewStatus.REJECTED);
        campaign.setReviewNote(reason);
        move(campaign, CampaignStatus.REJECTED, actor, "CAMPAIGN_REJECTED", reason);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse publish(AuthUser actor, UUID id) {
        Campaign campaign = loadOwnerOrAdmin(actor, id);
        States.require(campaign.getCampaignStatus(), CampaignStatus.APPROVED);
        if (!campaign.getEndAt().isAfter(Instant.now())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Campaign end date has already passed");
        }
        campaign.setPublishedAt(Instant.now());
        escrowEngine.openAccount(campaign.getId(), campaign.getCurrency());
        move(campaign, CampaignStatus.ACTIVE, actor, "CAMPAIGN_PUBLISHED", null);
        milestoneRepository.findFirstByCampaignIdAndMilestoneStatusOrderBySequenceNumberAsc(id, MilestoneStatus.PLANNED).ifPresent(m -> {
            m.setMilestoneStatus(MilestoneStatus.READY);
            auditService.transition(actor.id(), "MILESTONE_READY", "MILESTONE", m.getId(), MilestoneStatus.PLANNED, MilestoneStatus.READY, "Campaign published");
        });
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse pause(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Campaign campaign = load(id);
        States.require(campaign.getCampaignStatus(), CampaignStatus.ACTIVE);
        escrowEngine.changeStatus(id, campaign.getCurrency(), EscrowStatus.FROZEN, actor.id(), reason);
        move(campaign, CampaignStatus.PAUSED, actor, "CAMPAIGN_PAUSED", reason);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse resume(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Campaign campaign = load(id);
        States.require(campaign.getCampaignStatus(), CampaignStatus.PAUSED);
        escrowEngine.changeStatus(id, campaign.getCurrency(), EscrowStatus.ACTIVE, actor.id(), reason);
        move(campaign, CampaignStatus.ACTIVE, actor, "CAMPAIGN_RESUMED", reason);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse complete(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Campaign campaign = load(id);
        States.require(campaign.getCampaignStatus(), CampaignStatus.ACTIVE, CampaignStatus.PAUSED);
        requireNoOpenFinancialWork(campaign);
        campaign.setCompletedAt(Instant.now());
        escrowEngine.changeStatus(id, campaign.getCurrency(), EscrowStatus.SETTLEMENT, actor.id(), "Campaign completed");
        move(campaign, CampaignStatus.COMPLETED, actor, "CAMPAIGN_COMPLETED", reason);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse cancel(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor);
        Campaign campaign = load(id);
        if (actor.isAdmin()) {
            States.require(campaign.getCampaignStatus(), EnumSet.complementOf(EnumSet.of(CampaignStatus.COMPLETED, CampaignStatus.CANCELLED)));
        } else {
            requireOwner(actor, campaign);
            States.require(campaign.getCampaignStatus(), OWNER_CANCELLABLE);
        }
        requireNoOpenFinancialWork(campaign);
        for (Milestone milestone : milestoneRepository.findByCampaignIdOrderBySequenceNumberAsc(id)) {
            if (CANCELLABLE_MILESTONES.contains(milestone.getMilestoneStatus())) {
                MilestoneStatus previous = milestone.getMilestoneStatus();
                milestone.setMilestoneStatus(MilestoneStatus.CANCELLED);
                auditService.transition(actor.id(), "MILESTONE_CANCELLED", "MILESTONE", milestone.getId(), previous, MilestoneStatus.CANCELLED, "Campaign cancelled");
            }
        }
        campaign.setCancelledAt(Instant.now());
        escrowEngine.changeStatus(id, campaign.getCurrency(), EscrowStatus.SETTLEMENT, actor.id(), "Campaign cancelled");
        move(campaign, CampaignStatus.CANCELLED, actor, "CAMPAIGN_CANCELLED", reason);
        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(AuthUser actor, UUID id) {
        return toResponse(loadVisible(id, actor));
    }

    @Transactional(readOnly = true)
    public CampaignResponse getBySlug(AuthUser actor, String slug) {
        Campaign campaign = campaignRepository.findBySlug(slug).orElseThrow(() -> ApplicationException.notFound("Campaign"));
        if (!isVisible(campaign, actor)) {
            throw ApplicationException.notFound("Campaign");
        }
        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public Page<CampaignResponse> search(AuthUser actor, CampaignStatus status, CampaignCategory category, UUID organizationId, String q, Pageable pageable) {
        boolean admin = actor != null && actor.isAdmin();
        Collection<CampaignStatus> statuses;
        if (status != null) {
            if (!admin && !PUBLIC_STATUSES.contains(status)) {
                throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only public campaign statuses can be listed");
            }
            statuses = EnumSet.of(status);
        } else {
            statuses = admin ? EnumSet.allOf(CampaignStatus.class) : PUBLIC_STATUSES;
        }
        String keyword = q == null || q.isBlank() ? null : q.trim();
        return toResponses(campaignRepository.search(statuses, category, organizationId, keyword, pageable));
    }

    @Transactional(readOnly = true)
    public Page<CampaignResponse> mine(AuthUser actor, Pageable pageable) {
        AuthUser.require(actor).requireRole(Role.CAMPAIGN_CREATOR);
        return toResponses(campaignRepository.findByCreatorId(actor.id(), pageable));
    }

    @Transactional(readOnly = true)
    public CampaignStatistics statistics(AuthUser actor, UUID id) {
        Campaign campaign = loadVisible(id, actor);
        EscrowAccount account = escrowAccountRepository.findByCampaignIdAndCurrency(id, campaign.getCurrency()).orElse(null);
        List<Milestone> milestones = milestoneRepository.findByCampaignIdOrderBySequenceNumberAsc(id);
        long completed = milestones.stream().filter(m -> m.getMilestoneStatus() == MilestoneStatus.COMPLETED).count();
        return new CampaignStatistics(id, campaign.getCurrency(), campaign.getTargetAmount(), FundingSummary.from(account, campaign.getTargetAmount()),
                donationRepository.countByCampaignIdAndDonationStatusIn(id, DonationStatus.COUNTED),
                donationRepository.countDistinctDonors(id, DonationStatus.COUNTED),
                donationRepository.countByCampaignIdAndDonationStatusIn(id, EnumSet.of(DonationStatus.PENDING)),
                milestones.size(), completed,
                disbursementRepository.countByCampaignIdAndRequestStatusIn(id, DisbursementStatus.OPEN),
                disputeRepository.countByCampaignIdAndDisputeStatusIn(id, DisputeStatus.OPEN_STATES));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(AuthUser actor, UUID id) {
        loadVisible(id, actor);
        return auditService.history(ENTITY, id);
    }

    public Campaign load(UUID id) {
        return campaignRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Campaign"));
    }

    public Campaign lock(UUID id) {
        return campaignRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Campaign"));
    }

    public Campaign loadVisible(UUID id, AuthUser actor) {
        Campaign campaign = load(id);
        if (!isVisible(campaign, actor)) {
            throw ApplicationException.notFound("Campaign");
        }
        return campaign;
    }

    public Campaign loadOwned(AuthUser actor, UUID id) {
        AuthUser.require(actor).requireRole(Role.CAMPAIGN_CREATOR);
        Campaign campaign = load(id);
        requireOwner(actor, campaign);
        return campaign;
    }

    public Campaign loadOwnerOrAdmin(AuthUser actor, UUID id) {
        AuthUser.require(actor);
        Campaign campaign = load(id);
        if (!actor.isAdmin()) {
            requireOwner(actor, campaign);
        }
        return campaign;
    }

    public boolean isVisible(Campaign campaign, AuthUser actor) {
        return PUBLIC_STATUSES.contains(campaign.getCampaignStatus()) || campaign.getPublishedAt() != null || isOwnerOrAdmin(campaign, actor);
    }

    public boolean isOwnerOrAdmin(Campaign campaign, AuthUser actor) {
        return actor != null && (actor.isAdmin() || actor.is(campaign.getCreatorId()));
    }

    private void requireOwner(AuthUser actor, Campaign campaign) {
        if (!actor.is(campaign.getCreatorId())) {
            throw isVisible(campaign, actor) ? new ApplicationException(ErrorCode.ACCESS_DENIED, "You do not manage this campaign") : ApplicationException.notFound("Campaign");
        }
    }

    private Campaign loadForReview(AuthUser actor, UUID id) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Campaign campaign = load(id);
        if (actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        States.require(campaign.getCampaignStatus(), CampaignStatus.PENDING_REVIEW);
        return campaign;
    }

    private void requireNoOpenFinancialWork(Campaign campaign) {
        if (disbursementRepository.existsByCampaignIdAndRequestStatusIn(campaign.getId(), DisbursementStatus.OPEN)) {
            throw new ApplicationException(ErrorCode.OPEN_REQUEST_EXISTS, "Campaign has open disbursement requests");
        }
        escrowAccountRepository.findByCampaignIdAndCurrency(campaign.getId(), campaign.getCurrency())
                .filter(a -> a.getTotalReserved().signum() > 0)
                .ifPresent(a -> {
                    throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Campaign still has reserved escrow funds");
                });
    }

    private void move(Campaign campaign, CampaignStatus target, AuthUser actor, String action, String reason) {
        CampaignStatus previous = campaign.getCampaignStatus();
        campaign.setCampaignStatus(target);
        auditService.transition(actor.id(), action, ENTITY, campaign.getId(), previous, target, reason);
    }

    private Organization organization(Campaign campaign) {
        return organizationRepository.findById(campaign.getOrganizationId()).orElseThrow(() -> ApplicationException.notFound("Organization"));
    }

    private void apply(Campaign campaign, CampaignRequest request, AuthUser actor) {
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> ApplicationException.notFound("Organization"));
        if (!actor.is(organization.getCreatedBy())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Organization is not managed by you");
        }
        if (organization.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ApplicationException(ErrorCode.ORGANIZATION_NOT_VERIFIED, "Organization was rejected by platform review");
        }
        String currency = Money.requireCurrency(request.currency(), properties.escrow().currency());
        BigDecimal target = Money.requirePositive(request.targetAmount());
        BigDecimal minimum = Money.requirePositive(request.minimumDonationAmount());
        if (!request.endAt().isAfter(request.startAt())) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "endAt must be after startAt");
        }
        if (!request.endAt().isAfter(Instant.now())) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "endAt must be in the future");
        }
        if (minimum.compareTo(target) > 0) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "minimumDonationAmount must not exceed targetAmount");
        }
        List<BudgetItem> breakdown = request.budgetBreakdown() == null ? List.of() : List.copyOf(request.budgetBreakdown());
        if (!breakdown.isEmpty() && Money.sum(breakdown.stream().map(BudgetItem::amount)).compareTo(target) != 0) {
            throw new ApplicationException(ErrorCode.BUDGET_MISMATCH, "Budget breakdown total must equal targetAmount");
        }
        if (campaign.getId() != null && Money.orZero(milestoneRepository.sumPlannedAmount(campaign.getId())).compareTo(target) > 0) {
            throw new ApplicationException(ErrorCode.BUDGET_MISMATCH, "Existing milestone budgets exceed the new targetAmount");
        }
        campaign.setOrganizationId(organization.getId());
        campaign.setTitle(request.title().trim());
        campaign.setShortDescription(request.shortDescription());
        campaign.setFullDescription(request.fullDescription());
        campaign.setCoverImageUrl(request.coverImageUrl());
        campaign.setGalleryImageUrls(request.galleryImageUrls() == null ? new ArrayList<>() : new ArrayList<>(request.galleryImageUrls()));
        campaign.setCategory(request.category());
        campaign.setBeneficiaryDescription(request.beneficiaryDescription());
        campaign.setTargetAmount(target);
        campaign.setCurrency(currency);
        campaign.setMinimumDonationAmount(minimum);
        campaign.setStartAt(request.startAt());
        campaign.setEndAt(request.endAt());
        campaign.setLocation(request.location());
        campaign.setBeneficiaryCount(request.beneficiaryCount());
        campaign.setProblemStatement(request.problemStatement());
        campaign.setSolutionDescription(request.solutionDescription());
        campaign.setImpactGoals(request.impactGoals());
        campaign.setBudgetBreakdown(new ArrayList<>(breakdown));
    }

    private String uniqueSlug(String title) {
        String base = Slugs.slugify(title);
        String slug = base;
        int suffix = 2;
        while (campaignRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private CampaignResponse toResponse(Campaign campaign) {
        OrganizationRepository.SummaryView organization = organizationRepository.findSummaries(List.of(campaign.getOrganizationId())).stream().findFirst().orElse(null);
        EscrowAccount account = escrowAccountRepository.findByCampaignIdAndCurrency(campaign.getId(), campaign.getCurrency()).orElse(null);
        return CampaignResponse.from(campaign, organization, account);
    }

    private Page<CampaignResponse> toResponses(Page<Campaign> page) {
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        Map<UUID, EscrowAccount> accounts = escrowAccountRepository.findByCampaignIdIn(page.map(Campaign::getId).toSet()).stream()
                .collect(Collectors.toMap(EscrowAccount::getCampaignId, Function.identity(), (a, b) -> a));
        Map<UUID, OrganizationRepository.SummaryView> organizations = organizationRepository.findSummaries(page.map(Campaign::getOrganizationId).toSet()).stream()
                .collect(Collectors.toMap(OrganizationRepository.SummaryView::getId, Function.identity()));
        return page.map(c -> CampaignResponse.from(c, organizations.get(c.getOrganizationId()), accounts.get(c.getId())));
    }
}
