package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void create(PasswordResetToken token);

    Optional<PasswordResetToken> findByDigestForUpdate(String tokenDigest);

    void revokeActiveForUser(UUID userId, Instant revokedAt);

    void markConsumed(UUID tokenId, Instant consumedAt);
}
