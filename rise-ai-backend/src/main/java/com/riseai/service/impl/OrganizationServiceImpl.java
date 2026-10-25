package com.riseai.service.impl;

import com.riseai.dto.request.OrganizationRequest;
import com.riseai.dto.response.OrganizationResponse;
import com.riseai.entity.Organization;
import com.riseai.enums.Role;
import com.riseai.enums.VerificationStatus;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.repository.CampaignRepository;
import com.riseai.repository.OrganizationRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AuditService;
import com.riseai.service.OrganizationService;
import com.riseai.util.Slugs;
import com.riseai.util.States;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {
    private static final String ENTITY = "ORGANIZATION";

    private final OrganizationRepository organizationRepository;
    private final CampaignRepository campaignRepository;
    private final AuditService auditService;

    @Transactional
    @Override
    public OrganizationResponse create(AuthUser actor, OrganizationRequest request) {
        AuthUser.require(actor).requireAnyRole(Role.CAMPAIGN_CREATOR, Role.ADMIN);
        Organization organization = new Organization();
        apply(organization, request);
        organization.setSlug(uniqueSlug(request.name()));
        organization.setVerificationStatus(VerificationStatus.UNVERIFIED);
        organization.setCreatedBy(actor.id());
        organizationRepository.saveAndFlush(organization);
        auditService.transition(actor.id(), "ORGANIZATION_CREATED", ENTITY, organization.getId(), null, VerificationStatus.UNVERIFIED, null);
        return OrganizationResponse.from(organization);
    }

    @Transactional
    @Override
    public OrganizationResponse update(AuthUser actor, UUID id, OrganizationRequest request) {
        Organization organization = loadManaged(actor, id);
        boolean material = !organization.getName().equals(request.name().trim()) || organization.getOrganizationType() != request.organizationType();
        apply(organization, request);
        if (material && !actor.isAdmin() && organization.getVerificationStatus() == VerificationStatus.VERIFIED) {
            organization.setVerificationStatus(VerificationStatus.UNVERIFIED);
            organization.setVerifiedAt(null);
            organization.setVerificationNotes("Re-review required after a material change");
            auditService.transition(actor.id(), "ORGANIZATION_VERIFICATION_RESET", ENTITY, id, VerificationStatus.VERIFIED, VerificationStatus.UNVERIFIED, "Material change by owner");
        }
        auditService.record(actor.id(), "ORGANIZATION_UPDATED", ENTITY, id, null, null, null);
        return OrganizationResponse.from(organization);
    }

    @Transactional
    @Override
    public void delete(AuthUser actor, UUID id) {
        Organization organization = loadManaged(actor, id);
        if (campaignRepository.existsByOrganizationId(id)) {
            throw new ApplicationException(ErrorCode.RESOURCE_IN_USE, "Organization is referenced by campaigns");
        }
        organizationRepository.delete(organization);
        auditService.record(actor.id(), "ORGANIZATION_DELETED", ENTITY, id, organization.getVerificationStatus().name(), null, null);
    }

    @Transactional(readOnly = true)
    @Override
    public OrganizationResponse get(UUID id) {
        return OrganizationResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrganizationResponse> search(String q, VerificationStatus status, Pageable pageable) {
        return organizationRepository.search(q == null || q.isBlank() ? null : q.trim(), status, pageable).map(OrganizationResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrganizationResponse> mine(AuthUser actor, Pageable pageable) {
        return organizationRepository.findByCreatedBy(AuthUser.require(actor).id(), pageable).map(OrganizationResponse::from);
    }

    @Transactional
    @Override
    public OrganizationResponse submitForVerification(AuthUser actor, UUID id) {
        Organization organization = load(id);
        if (!AuthUser.require(actor).is(organization.getCreatedBy())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only the organization owner can request verification");
        }
        States.require(organization.getVerificationStatus(), VerificationStatus.UNVERIFIED, VerificationStatus.REJECTED);
        move(organization, VerificationStatus.PENDING_REVIEW, actor, "ORGANIZATION_VERIFICATION_REQUESTED", null);
        return OrganizationResponse.from(organization);
    }

    @Transactional
    @Override
    public OrganizationResponse verify(AuthUser actor, UUID id, String notes) {
        Organization organization = loadForReview(actor, id);
        organization.setVerifiedAt(Instant.now());
        organization.setVerificationNotes(notes);
        move(organization, VerificationStatus.VERIFIED, actor, "ORGANIZATION_VERIFIED", notes);
        return OrganizationResponse.from(organization);
    }

    @Transactional
    @Override
    public OrganizationResponse rejectVerification(AuthUser actor, UUID id, String notes) {
        Organization organization = loadForReview(actor, id);
        organization.setVerifiedAt(null);
        organization.setVerificationNotes(notes);
        move(organization, VerificationStatus.REJECTED, actor, "ORGANIZATION_VERIFICATION_REJECTED", notes);
        return OrganizationResponse.from(organization);
    }

    private Organization loadForReview(AuthUser actor, UUID id) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Organization organization = load(id);
        if (actor.is(organization.getCreatedBy())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        States.require(organization.getVerificationStatus(), VerificationStatus.PENDING_REVIEW);
        return organization;
    }

    private void move(Organization organization, VerificationStatus target, AuthUser actor, String action, String reason) {
        VerificationStatus previous = organization.getVerificationStatus();
        organization.setVerificationStatus(target);
        auditService.transition(actor.id(), action, ENTITY, organization.getId(), previous, target, reason);
    }

    private Organization loadManaged(AuthUser actor, UUID id) {
        Organization organization = load(id);
        if (!AuthUser.require(actor).isAdmin() && !actor.is(organization.getCreatedBy())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return organization;
    }

    private Organization load(UUID id) {
        return organizationRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Organization"));
    }

    private void apply(Organization organization, OrganizationRequest request) {
        organization.setName(request.name().trim());
        organization.setDescription(request.description());
        organization.setOrganizationType(request.organizationType());
        organization.setContactEmail(request.contactEmail().trim());
        organization.setContactPhone(request.contactPhone());
        organization.setAddress(request.address());
    }

    private String uniqueSlug(String name) {
        String base = Slugs.slugify(name);
        String slug = base;
        int suffix = 2;
        while (organizationRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
