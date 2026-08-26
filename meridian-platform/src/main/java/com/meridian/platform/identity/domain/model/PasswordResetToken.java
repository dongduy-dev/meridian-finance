package com.meridian.platform.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PasswordResetToken(
        UUID id,
        UUID userId,
        String tokenDigest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt
) {

    public PasswordResetToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenDigest, "tokenDigest must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant instant) {
        return !Objects.requireNonNull(instant, "instant must not be null").isBefore(expiresAt);
    }
}
