package com.meridian.platform.identity.application.port.out;

import java.time.Instant;

public record IssuedAccessToken(
        String tokenValue,
        Instant expiresAt
) {
}
