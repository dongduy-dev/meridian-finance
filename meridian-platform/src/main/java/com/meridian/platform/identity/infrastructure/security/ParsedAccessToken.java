package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.shared.application.security.AuthenticatedUser;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ParsedAccessToken(
        AuthenticatedUser authenticatedUser,
        UUID tokenId,
        Instant expiresAt
) {

    public ParsedAccessToken {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser must not be null");
        Objects.requireNonNull(tokenId, "tokenId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
