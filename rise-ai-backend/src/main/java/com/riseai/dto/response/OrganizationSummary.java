package com.riseai.dto.response;

import com.riseai.enums.VerificationStatus;
import com.riseai.repository.OrganizationRepository;

import java.util.UUID;

public record OrganizationSummary(UUID id, String name, String slug, VerificationStatus verificationStatus) {
    static OrganizationSummary from(OrganizationRepository.SummaryView o) {
        return o == null ? null : new OrganizationSummary(o.getId(), o.getName(), o.getSlug(), o.getVerificationStatus());
    }
}
