package com.meridian.platform.shared.application.security;

public interface CurrentUserProvider {

    AuthenticatedUser currentUser();
}
