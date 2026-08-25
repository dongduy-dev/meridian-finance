package com.meridian.platform.identity.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccessTokenReference(
        UUID tokenId,
        Instant expiresAt
) {

    public AccessTokenReference {
        Objects.requireNonNull(tokenId, "tokenId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
