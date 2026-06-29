package com.meridian.platform.identity.domain.model;

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
        Set<String> permissions
) {

    public User {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
