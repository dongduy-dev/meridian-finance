package com.meridian.platform.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationConfirmationRequest(
        @NotBlank @Size(max = 256) String token
) {
}
