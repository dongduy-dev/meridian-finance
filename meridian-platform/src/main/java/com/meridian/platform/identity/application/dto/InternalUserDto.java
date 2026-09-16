package com.meridian.platform.identity.application.dto;

import java.util.List;
import java.util.UUID;

public record InternalUserDto(
        UUID userId,
        String email,
        String displayName,
        String status,
        List<String> assignedRoleCodes
) {
    public InternalUserDto {
        assignedRoleCodes = List.copyOf(assignedRoleCodes);
    }
}
