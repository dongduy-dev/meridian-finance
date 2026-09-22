package com.meridian.platform.identity.application.dto;

import com.meridian.platform.identity.domain.model.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        UserType expectedUserType
) {
    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
