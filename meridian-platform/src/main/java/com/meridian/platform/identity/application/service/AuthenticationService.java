package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
public class AuthenticationService implements AuthenticationUseCase {

    private final PasswordLoginService passwordLoginService;
    private final RefreshTokenRotationService refreshTokenRotationService;

    public AuthenticationService(
            PasswordLoginService passwordLoginService,
            RefreshTokenRotationService refreshTokenRotationService
    ) {
        this.passwordLoginService = Objects.requireNonNull(passwordLoginService);
        this.refreshTokenRotationService = Objects.requireNonNull(refreshTokenRotationService);
    }

    @Override
    public AuthenticationResult login(LoginRequest request) {
        PasswordLoginOutcome outcome = passwordLoginService.login(request);
        if (outcome.result().isPresent()) {
            return outcome.result().orElseThrow();
        }
        if (outcome.failure() == PasswordLoginOutcome.Failure.ACCOUNT_SUSPENDED) {
            throw new AuthenticationFailedException("ACCOUNT_SUSPENDED", "Account is not active.");
        }
        if (outcome.failure() == PasswordLoginOutcome.Failure.EMAIL_VERIFICATION_REQUIRED) {
            throw new AuthenticationFailedException(
                    "EMAIL_VERIFICATION_REQUIRED",
                    "Email verification required."
            );
        }
        throw new AuthenticationFailedException("INVALID_CREDENTIALS", "Invalid credentials.");
    }

    @Override
    public AuthenticationResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }

        return refreshTokenRotationService.rotate(refreshToken)
                .orElseThrow(AuthenticationService::invalidRefreshToken);
    }

    static AuthenticationResult authenticationResult(
            User user,
            IssuedAccessToken accessToken,
            String refreshToken,
            Instant refreshTokenIssuedAt,
            Instant refreshTokenExpiresAt
    ) {
        AuthResponse response = new AuthResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                user.id(),
                user.email(),
                user.userType().name(),
                user.customerId(),
                user.roles(),
                user.permissions()
        );
        return new AuthenticationResult(
                response,
                refreshToken,
                refreshTokenIssuedAt,
                refreshTokenExpiresAt
        );
    }

    private static AuthenticationFailedException invalidRefreshToken() {
        return new AuthenticationFailedException(
                "INVALID_REFRESH_TOKEN",
                "Refresh authentication failed."
        );
    }

}
