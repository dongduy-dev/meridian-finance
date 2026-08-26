package com.meridian.platform.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Email @Size(max = 255) String email
) {

    public PasswordResetRequest {
        email = email == null ? null : email.trim();
    }
}
