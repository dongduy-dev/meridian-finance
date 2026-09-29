package com.meridian.platform.identity.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnableCustomerDigitalAccessRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String identityReference
) {
    public EnableCustomerDigitalAccessRequest {
        email = email == null ? null : email.trim();
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported digital-access activation field.");
    }
}
