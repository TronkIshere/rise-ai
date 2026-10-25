package com.riseai.dto.response;

import com.riseai.entity.Organization;
import com.riseai.enums.OrganizationType;
import com.riseai.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name, String slug, String description, OrganizationType organizationType,
                                   String contactEmail, String contactPhone, String address, VerificationStatus verificationStatus,
                                   String verificationNotes, Instant verifiedAt, UUID createdBy, Instant createdAt, String verificationDisclaimer) {
    public static final String VERIFICATION_DISCLAIMER = "Verification reflects an internal platform review only and is not a legal verification of the organization.";

    public static OrganizationResponse from(Organization o) {
        return new OrganizationResponse(o.getId(), o.getName(), o.getSlug(), o.getDescription(), o.getOrganizationType(), o.getContactEmail(),
                o.getContactPhone(), o.getAddress(), o.getVerificationStatus(), o.getVerificationNotes(), o.getVerifiedAt(), o.getCreatedBy(),
                o.getCreatedAt(), VERIFICATION_DISCLAIMER);
    }
}
