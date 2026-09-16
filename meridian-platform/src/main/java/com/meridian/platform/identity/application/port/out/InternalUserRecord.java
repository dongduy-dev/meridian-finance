package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.UserStatus;

import java.util.Set;
import java.util.UUID;

public record InternalUserRecord(
        UUID userId,
        String email,
        String displayName,
        UserStatus status,
        Set<String> assignedRoleCodes
) {
    public InternalUserRecord {
        assignedRoleCodes = Set.copyOf(assignedRoleCodes);
    }
}
