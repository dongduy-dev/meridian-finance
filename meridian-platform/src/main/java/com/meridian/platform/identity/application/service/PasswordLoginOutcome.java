package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthenticationResult;

import java.util.Objects;
import java.util.Optional;

record PasswordLoginOutcome(Optional<AuthenticationResult> result, Failure failure) {

    PasswordLoginOutcome {
        result = Objects.requireNonNull(result, "result must not be null");
        if (result.isPresent() == (failure != null)) {
            throw new IllegalArgumentException("outcome must contain exactly one result or failure");
        }
    }

    static PasswordLoginOutcome success(AuthenticationResult result) {
        return new PasswordLoginOutcome(Optional.of(result), null);
    }

    static PasswordLoginOutcome invalidCredentials() {
        return new PasswordLoginOutcome(Optional.empty(), Failure.INVALID_CREDENTIALS);
    }

    static PasswordLoginOutcome accountSuspended() {
        return new PasswordLoginOutcome(Optional.empty(), Failure.ACCOUNT_SUSPENDED);
    }

    static PasswordLoginOutcome emailVerificationRequired() {
        return new PasswordLoginOutcome(Optional.empty(), Failure.EMAIL_VERIFICATION_REQUIRED);
    }

    enum Failure {
        INVALID_CREDENTIALS,
        ACCOUNT_SUSPENDED,
        EMAIL_VERIFICATION_REQUIRED
    }
}
