package com.riseai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 30) @Pattern(regexp = "^[0-9+ ()-]*$") String phone,
        @Size(max = 500) @Pattern(regexp = "^(https://\\S+)?$") String avatarUrl) {
}
