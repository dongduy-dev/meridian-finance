package com.meridian.platform.identity.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface AccessTokenRevocationRepository {

    void revoke(UUID tokenId, Instant revokedAt, Instant expiresAt);

    boolean isRevoked(UUID tokenId);
}
