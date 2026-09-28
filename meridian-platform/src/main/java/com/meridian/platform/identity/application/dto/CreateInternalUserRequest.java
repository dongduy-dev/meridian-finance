package com.meridian.platform.identity.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.ArrayList;

public record CreateInternalUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 150) String displayName,
        @NotEmpty List<@NotBlank String> roleCodes
) {
    public CreateInternalUserRequest {
        email = email == null ? null : email.trim();
        displayName = displayName == null ? null : displayName.trim();
        roleCodes = roleCodes == null ? null : new ArrayList<>(roleCodes);
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported Internal User creation field.");
    }
}
