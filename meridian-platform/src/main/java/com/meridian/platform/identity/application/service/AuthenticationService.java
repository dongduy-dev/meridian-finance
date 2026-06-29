package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
import com.meridian.platform.identity.application.port.out.PasswordVerifierPort;
import com.meridian.platform.identity.application.port.out.TokenIssuerPort;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

@Service
public class AuthenticationService implements AuthenticationUseCase {

    private final UserRepository userRepository;
    private final PasswordVerifierPort passwordVerifier;
    private final TokenIssuerPort tokenIssuer;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordVerifierPort passwordVerifier,
            TokenIssuerPort tokenIssuer
    ) {
        this.userRepository = userRepository;
        this.passwordVerifier = passwordVerifier;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        User user = userRepository.findByNormalizedEmail(normalizeEmail(request.email()))
                .filter(foundUser -> passwordVerifier.matches(request.password(), foundUser.passwordHash()))
                .orElseThrow(() -> new AuthenticationFailedException(
                        "INVALID_CREDENTIALS",
                        "Invalid credentials."
                ));

        if (!user.isActive()) {
            throw new AuthenticationFailedException(
                    "ACCOUNT_SUSPENDED",
                    "Account is not active."
            );
        }

        IssuedAccessToken token = tokenIssuer.issueAccessToken(user);
        return new AuthResponse(
                "Bearer",
                token.tokenValue(),
                token.expiresAt(),
                user.id(),
                user.email(),
                user.userType().name(),
                user.customerId(),
                user.roles(),
                user.permissions()
        );
    }

    private String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
