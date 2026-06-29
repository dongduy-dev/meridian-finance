package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.shared.application.security.AuthenticatedUser;

public record MeridianPrincipal(AuthenticatedUser authenticatedUser) {

    public String getName() {
        return authenticatedUser.email();
    }
}
