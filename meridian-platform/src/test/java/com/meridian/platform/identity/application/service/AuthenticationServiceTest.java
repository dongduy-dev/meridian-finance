package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private static final LoginRequest LOGIN = new LoginRequest("user@meridian.local", "password");

    @Test
    void returnsCommittedPasswordLoginSuccess() {
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        AuthenticationResult expected = authenticationResult();
        when(passwordLoginService.login(LOGIN)).thenReturn(PasswordLoginOutcome.success(expected));

        AuthenticationResult actual = new AuthenticationService(passwordLoginService, mock(RefreshTokenRotationService.class))
                .login(LOGIN);

        assertSame(expected, actual);
    }

    @Test
    void convertsSafeFailedOutcomeAfterCollaboratorReturns() {
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        when(passwordLoginService.login(LOGIN)).thenReturn(PasswordLoginOutcome.invalidCredentials());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> new AuthenticationService(passwordLoginService, mock(RefreshTokenRotationService.class))
                        .login(LOGIN)
        );

        assertEquals("INVALID_CREDENTIALS", exception.getErrorCode());
        assertEquals("Invalid credentials.", exception.getMessage());
    }

    @Test
    void preservesAdministrativeAccountStatusFailure() {
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        when(passwordLoginService.login(LOGIN)).thenReturn(PasswordLoginOutcome.accountSuspended());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> new AuthenticationService(passwordLoginService, mock(RefreshTokenRotationService.class))
                        .login(LOGIN)
        );

        assertEquals("ACCOUNT_SUSPENDED", exception.getErrorCode());
        assertEquals("Account is not active.", exception.getMessage());
    }

    @Test
    void exposesTheSpecificVerificationRequirementOnlyAfterCorrectPasswordProcessing() {
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        when(passwordLoginService.login(LOGIN)).thenReturn(PasswordLoginOutcome.emailVerificationRequired());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> new AuthenticationService(passwordLoginService, mock(RefreshTokenRotationService.class))
                        .login(LOGIN)
        );

        assertEquals("EMAIL_VERIFICATION_REQUIRED", exception.getErrorCode());
        assertEquals("Email verification required.", exception.getMessage());
    }

    private AuthenticationResult authenticationResult() {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        return new AuthenticationResult(
                new AuthResponse(
                        "Bearer",
                        "access-token",
                        now.plusSeconds(3600),
                        UUID.randomUUID(),
                        "user@meridian.local",
                        "STAFF",
                        null,
                        Set.of("LOAN_OFFICER"),
                        Set.of("loan:read")
                ),
                "refresh-token",
                now,
                now.plusSeconds(604800)
        );
    }
}
