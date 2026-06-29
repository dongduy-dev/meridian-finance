package com.meridian.platform.shared.application.security;

import com.meridian.platform.shared.domain.exception.AuthorizationException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email,
        String userType,
        UUID customerId,
        Set<String> roles,
        Set<String> permissions
) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public Optional<UUID> optionalCustomerId() {
        return Optional.ofNullable(customerId);
    }

    public UUID requireCustomerId() {
        return optionalCustomerId()
                .orElseThrow(() -> new AuthorizationException(
                        "CUSTOMER_CONTEXT_REQUIRED",
                        "Authenticated user is not linked to a customer profile."
                ));
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
