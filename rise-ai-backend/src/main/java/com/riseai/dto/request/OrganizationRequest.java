package com.riseai.dto.request;

import com.riseai.enums.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @NotNull OrganizationType organizationType,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @Size(max = 30) String contactPhone,
        @Size(max = 500) String address) {
}
