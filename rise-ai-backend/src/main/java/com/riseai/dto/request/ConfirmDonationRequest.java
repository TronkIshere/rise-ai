package com.riseai.dto.request;

import jakarta.validation.constraints.Size;

public record ConfirmDonationRequest(@Size(max = 100) String simulatedReference) {
}
