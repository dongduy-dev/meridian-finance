package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.EmailVerificationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository {

    void create(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByDigestForUpdate(String tokenDigest);

    void revokeActiveForUser(UUID userId, Instant revokedAt);

    void markConsumed(UUID tokenId, Instant consumedAt);
}
