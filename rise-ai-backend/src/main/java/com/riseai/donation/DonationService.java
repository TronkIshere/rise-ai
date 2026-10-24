package com.riseai.donation;

import com.riseai.audit.AuditService;
import com.riseai.campaign.Campaign;
import com.riseai.campaign.CampaignService;
import com.riseai.campaign.CampaignStatus;
import com.riseai.common.Codes;
import com.riseai.common.Money;
import com.riseai.common.States;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import com.riseai.config.RiseProperties;
import com.riseai.donation.DonationDtos.CreateDonationRequest;
import com.riseai.donation.DonationDtos.DonationResponse;
import com.riseai.escrow.EscrowEngine;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import com.riseai.user.User;
import com.riseai.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DonationService {
    private static final String ENTITY = "DONATION";

    private final DonationRepository donationRepository;
    private final UserRepository userRepository;
    private final CampaignService campaignService;
    private final EscrowEngine escrowEngine;
    private final AuditService auditService;
    private final RiseProperties properties;

    @Transactional
    public DonationResponse create(AuthUser actor, UUID campaignId, CreateDonationRequest request, String idempotencyKey) {
        AuthUser.require(actor).requireRole(Role.DONOR);
        BigDecimal amount = Money.requirePositive(request.amount());
        if (idempotencyKey != null) {
            if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
                throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Idempotency-Key must be 1-100 characters");
            }
            Optional<Donation> existing = donationRepository.findByDonorIdAndIdempotencyKey(actor.id(), idempotencyKey);
            if (existing.isPresent()) {
                Donation donation = existing.get();
                if (!donation.getCampaignId().equals(campaignId) || donation.getAmount().compareTo(amount) != 0) {
                    throw new ApplicationException(ErrorCode.DUPLICATE_OPERATION, "Idempotency-Key was already used for a different donation");
                }
                return DonationResponse.full(donation, userRepository.findById(actor.id()).orElse(null));
            }
        }
        Campaign campaign = campaignService.loadVisible(campaignId, actor);
        if (actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.CONFLICT_OF_INTEREST, "Campaign creators cannot donate to their own campaign");
        }
        Instant now = Instant.now();
        if (campaign.getCampaignStatus() != CampaignStatus.ACTIVE || now.isBefore(campaign.getStartAt()) || now.isAfter(campaign.getEndAt())) {
            throw new ApplicationException(ErrorCode.CAMPAIGN_NOT_ACCEPTING_DONATIONS);
        }
        String currency = Money.requireCurrency(request.currency(), campaign.getCurrency());
        if (amount.compareTo(campaign.getMinimumDonationAmount()) < 0) {
            throw new ApplicationException(ErrorCode.DONATION_BELOW_MINIMUM,
                    "Minimum donation is " + campaign.getMinimumDonationAmount().toPlainString() + " " + campaign.getCurrency());
        }
        Donation donation = new Donation();
        donation.setDonationCode(Codes.generate("DON"));
        donation.setCampaignId(campaignId);
        donation.setDonorId(actor.id());
        donation.setAmount(amount);
        donation.setCurrency(currency);
        donation.setDonationStatus(DonationStatus.PENDING);
        donation.setPaymentMethod(request.paymentMethod() == null ? PaymentMethod.SIMULATED_BANK_TRANSFER : request.paymentMethod());
        donation.setAnonymous(Boolean.TRUE.equals(request.anonymous()));
        donation.setDonorMessage(request.donorMessage());
        donation.setIdempotencyKey(idempotencyKey);
        donationRepository.saveAndFlush(donation);
        auditService.transition(actor.id(), "DONATION_CREATED", ENTITY, donation.getId(), null, DonationStatus.PENDING,
                "Amount " + amount.toPlainString() + " " + currency);
        return DonationResponse.full(donation, userRepository.findById(actor.id()).orElse(null));
    }

    @Transactional
    public DonationResponse confirm(AuthUser actor, UUID id, String simulatedReference) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        if (!properties.simulation().donationConfirmationEnabled()) {
            throw new ApplicationException(ErrorCode.SIMULATION_DISABLED);
        }
        Donation donation = lock(id);
        if (DonationStatus.COUNTED.contains(donation.getDonationStatus())) {
            throw new ApplicationException(ErrorCode.DUPLICATE_OPERATION, "Donation has already been confirmed");
        }
        States.require(donation.getDonationStatus(), DonationStatus.PENDING);
        if (actor.is(donation.getDonorId())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED, "You cannot confirm your own donation");
        }
        Campaign campaign = campaignService.load(donation.getCampaignId());
        if (campaign.getCampaignStatus() != CampaignStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.CAMPAIGN_NOT_ACCEPTING_DONATIONS, "Campaign is not ACTIVE; fail the donation instead");
        }
        donation.setConfirmedAt(Instant.now());
        donation.setSimulatedReference(simulatedReference == null || simulatedReference.isBlank() ? Codes.generate("SIM") : simulatedReference.trim());
        move(donation, DonationStatus.CONFIRMED, actor.id(), "DONATION_CONFIRMED", "Simulated payment confirmation");
        escrowEngine.recordContribution(campaign.getId(), donation.getCurrency(), donation.getId(), donation.getAmount(), actor.id());
        return DonationResponse.full(donation, userRepository.findById(donation.getDonorId()).orElse(null));
    }

    @Transactional
    public DonationResponse fail(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Donation donation = lock(id);
        States.require(donation.getDonationStatus(), DonationStatus.PENDING);
        move(donation, DonationStatus.FAILED, actor.id(), "DONATION_FAILED", reason);
        return DonationResponse.full(donation, userRepository.findById(donation.getDonorId()).orElse(null));
    }

    @Transactional
    public DonationResponse cancel(AuthUser actor, UUID id) {
        Donation donation = lock(id);
        if (!AuthUser.require(actor).is(donation.getDonorId())) {
            throw ApplicationException.notFound("Donation");
        }
        States.require(donation.getDonationStatus(), DonationStatus.PENDING);
        move(donation, DonationStatus.CANCELLED, actor.id(), "DONATION_CANCELLED", "Cancelled by donor");
        return DonationResponse.full(donation, userRepository.findById(donation.getDonorId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public DonationResponse get(AuthUser actor, UUID id) {
        Donation donation = donationRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Donation"));
        User donor = userRepository.findById(donation.getDonorId()).orElse(null);
        if (AuthUser.require(actor).isAdmin() || actor.is(donation.getDonorId())) {
            return DonationResponse.full(donation, donor);
        }
        Campaign campaign = campaignService.load(donation.getCampaignId());
        if (actor.is(campaign.getCreatorId())) {
            return DonationResponse.masked(donation, donor);
        }
        throw ApplicationException.notFound("Donation");
    }

    @Transactional(readOnly = true)
    public Page<DonationResponse> mine(AuthUser actor, Pageable pageable) {
        return map(donationRepository.findByDonorId(AuthUser.require(actor).id(), pageable), DonationResponse::full);
    }

    @Transactional(readOnly = true)
    public Page<DonationResponse> publicList(AuthUser actor, UUID campaignId, Pageable pageable) {
        campaignService.loadVisible(campaignId, actor);
        return map(donationRepository.findByCampaignIdAndDonationStatusIn(campaignId, DonationStatus.COUNTED, pageable), DonationResponse::masked);
    }

    @Transactional(readOnly = true)
    public Page<DonationResponse> manageList(AuthUser actor, UUID campaignId, DonationStatus status, Pageable pageable) {
        campaignService.loadOwnerOrAdmin(actor, campaignId);
        Page<Donation> page = status == null
                ? donationRepository.findByCampaignId(campaignId, pageable)
                : donationRepository.findByCampaignIdAndDonationStatusIn(campaignId, EnumSet.of(status), pageable);
        return map(page, actor.isAdmin() ? DonationResponse::full : DonationResponse::masked);
    }

    @Transactional(readOnly = true)
    public Page<DonationResponse> adminList(DonationStatus status, Pageable pageable) {
        Page<Donation> page = status == null ? donationRepository.findAll(pageable) : donationRepository.findByDonationStatus(status, pageable);
        return map(page, DonationResponse::full);
    }

    private Page<DonationResponse> map(Page<Donation> page, BiFunction<Donation, User, DonationResponse> mapper) {
        Map<UUID, User> donors = userRepository.findAllById(page.map(Donation::getDonorId).toSet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return page.map(d -> mapper.apply(d, donors.get(d.getDonorId())));
    }

    private void move(Donation donation, DonationStatus target, UUID actorId, String action, String reason) {
        DonationStatus previous = donation.getDonationStatus();
        donation.setDonationStatus(target);
        auditService.transition(actorId, action, ENTITY, donation.getId(), previous, target, reason);
    }

    private Donation lock(UUID id) {
        return donationRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Donation"));
    }
}
