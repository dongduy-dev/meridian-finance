package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw authenticationRequired();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof MeridianPrincipal meridianPrincipal) {
            return meridianPrincipal.authenticatedUser();
        }

        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        throw authenticationRequired();
    }

    private AuthenticationFailedException authenticationRequired() {
        return new AuthenticationFailedException(
                "AUTHENTICATION_REQUIRED",
                "Authentication required."
        );
    }
}
