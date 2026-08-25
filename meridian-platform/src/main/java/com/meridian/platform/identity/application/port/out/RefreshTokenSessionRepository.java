package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.RefreshTokenSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository {

    void create(RefreshTokenSession session);

    Optional<RefreshTokenSession> findByDigestForUpdate(String tokenDigest);

    void markConsumed(UUID sessionId, Instant consumedAt);

    void revokeFamily(UUID familyId, Instant revokedAt);
}
