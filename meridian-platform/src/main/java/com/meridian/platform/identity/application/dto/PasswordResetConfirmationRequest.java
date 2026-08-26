package com.meridian.platform.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmationRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Size(min = 12, max = 72) String newPassword
) {
}
