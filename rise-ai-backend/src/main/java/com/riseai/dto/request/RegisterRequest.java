package com.riseai.dto.request;

import com.riseai.enums.RegistrableRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain letters and digits") String password,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 30) @Pattern(regexp = "^[0-9+ ()-]*$") String phone,
        @NotNull RegistrableRole accountType) {
}
