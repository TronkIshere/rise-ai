package com.riseai.dto.request;

import com.riseai.enums.DisputeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OpenDisputeRequest(
        UUID milestoneId,
        UUID disbursementRequestId,
        @NotNull DisputeType disputeType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> supportingReferences) {
}
