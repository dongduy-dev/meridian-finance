package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.out.GeneratedRefreshToken;
import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
import com.meridian.platform.identity.application.port.out.PasswordVerifierPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.application.port.out.TokenIssuerPort;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.RefreshTokenSession;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthenticationService implements AuthenticationUseCase {

    private final UserRepository userRepository;
    private final PasswordVerifierPort passwordVerifier;
    private final TokenIssuerPort tokenIssuer;
    private final RefreshTokenCodecPort refreshTokenCodec;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final RefreshTokenRotationService refreshTokenRotationService;
    private final Duration refreshTokenLifetime;
    private final Clock clock;

    @Autowired
    public AuthenticationService(
            UserRepository userRepository,
            PasswordVerifierPort passwordVerifier,
            TokenIssuerPort tokenIssuer,
            RefreshTokenCodecPort refreshTokenCodec,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            RefreshTokenRotationService refreshTokenRotationService,
            @Value("${meridian.identity.refresh-token.lifetime:7d}") Duration refreshTokenLifetime
    ) {
        this(
                userRepository,
                passwordVerifier,
                tokenIssuer,
                refreshTokenCodec,
                refreshTokenSessionRepository,
                refreshTokenRotationService,
                refreshTokenLifetime,
                Clock.systemUTC()
        );
    }

    AuthenticationService(
            UserRepository userRepository,
            PasswordVerifierPort passwordVerifier,
            TokenIssuerPort tokenIssuer,
            RefreshTokenCodecPort refreshTokenCodec,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            RefreshTokenRotationService refreshTokenRotationService,
            Duration refreshTokenLifetime,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordVerifier = passwordVerifier;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenCodec = refreshTokenCodec;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.refreshTokenRotationService = refreshTokenRotationService;
        this.refreshTokenLifetime = requirePositive(refreshTokenLifetime);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public AuthenticationResult login(LoginRequest request) {
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

        IssuedAccessToken accessToken = tokenIssuer.issueAccessToken(user);
        GeneratedRefreshToken refreshToken = refreshTokenCodec.generate();
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(refreshTokenLifetime);
        UUID familyId = UUID.randomUUID();
        refreshTokenSessionRepository.create(new RefreshTokenSession(
                UUID.randomUUID(),
                user.id(),
                familyId,
                refreshToken.tokenDigest(),
                issuedAt,
                expiresAt,
                null,
                null
        ));

        return authenticationResult(user, accessToken, refreshToken.tokenValue(), issuedAt, expiresAt);
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

    private String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static AuthenticationFailedException invalidRefreshToken() {
        return new AuthenticationFailedException(
                "INVALID_REFRESH_TOKEN",
                "Refresh authentication failed."
        );
    }

    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "refreshTokenLifetime must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("refresh-token lifetime must be positive");
        }
        return duration;
    }
}
