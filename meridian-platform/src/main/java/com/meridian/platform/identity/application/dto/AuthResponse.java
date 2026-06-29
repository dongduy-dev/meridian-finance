package com.meridian.platform.identity.application.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        UUID userId,
        String email,
        String userType,
        UUID customerId,
        Set<String> roles,
        Set<String> permissions
) {
}
