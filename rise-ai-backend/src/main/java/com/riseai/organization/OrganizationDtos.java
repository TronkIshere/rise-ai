package com.riseai.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class OrganizationDtos {
    public static final String VERIFICATION_DISCLAIMER = "Verification reflects an internal platform review only and is not a legal verification of the organization.";

    private OrganizationDtos() {
    }

    public record OrganizationRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @NotNull OrganizationType organizationType,
            @NotBlank @Email @Size(max = 255) String contactEmail,
            @Size(max = 30) String contactPhone,
            @Size(max = 500) String address) {
    }

    public record VerificationDecisionRequest(@NotBlank @Size(max = 1000) String notes) {
    }

    public record OrganizationResponse(UUID id, String name, String slug, String description, OrganizationType organizationType,
                                       String contactEmail, String contactPhone, String address, VerificationStatus verificationStatus,
                                       String verificationNotes, Instant verifiedAt, UUID createdBy, Instant createdAt, String verificationDisclaimer) {
        public static OrganizationResponse from(Organization o) {
            return new OrganizationResponse(o.getId(), o.getName(), o.getSlug(), o.getDescription(), o.getOrganizationType(), o.getContactEmail(),
                    o.getContactPhone(), o.getAddress(), o.getVerificationStatus(), o.getVerificationNotes(), o.getVerifiedAt(), o.getCreatedBy(),
                    o.getCreatedAt(), VERIFICATION_DISCLAIMER);
        }
    }
}
