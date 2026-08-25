package com.meridian.platform.identity.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        UserType userType,
        UserStatus status,
        String displayName,
        UUID customerId,
        Set<String> roles,
        Set<String> permissions,
        int failedLoginAttempts,
        Instant lockedUntil
) {

    public User {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("failedLoginAttempts must not be negative");
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isTemporarilyLockedAt(Instant instant) {
        return lockedUntil != null && instant.isBefore(lockedUntil);
    }

    public boolean hasExpiredLockAt(Instant instant) {
        return lockedUntil != null && !instant.isBefore(lockedUntil);
    }
}
