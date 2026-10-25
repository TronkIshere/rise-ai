package com.riseai.dto.request;

import com.riseai.enums.EvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EvidenceRequest(
        @NotNull EvidenceType evidenceType,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @Size(max = 500) @Pattern(regexp = "^((https|sim)://\\S+)?$", message = "must be an https:// or sim:// reference") String fileUrl,
        @Pattern(regexp = "^([a-fA-F0-9]{64})?$", message = "must be a SHA-256 hex digest") String fileHash,
        @Size(max = 500) String externalReference) {
}
