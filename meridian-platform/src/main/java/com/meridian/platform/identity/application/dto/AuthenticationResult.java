package com.meridian.platform.identity.application.dto;

import java.time.Instant;
import java.util.Objects;

public record AuthenticationResult(
        AuthResponse response,
        String refreshToken,
        Instant refreshTokenIssuedAt,
        Instant refreshTokenExpiresAt
) {

    public AuthenticationResult {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(refreshTokenIssuedAt, "refreshTokenIssuedAt must not be null");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt must not be null");
    }
}
