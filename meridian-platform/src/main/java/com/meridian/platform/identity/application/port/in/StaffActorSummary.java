package com.meridian.platform.identity.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record StaffActorSummary(
        UUID userId,
        String displayName,
        String email
) {
    public StaffActorSummary {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(email, "email must not be null");
    }
}
