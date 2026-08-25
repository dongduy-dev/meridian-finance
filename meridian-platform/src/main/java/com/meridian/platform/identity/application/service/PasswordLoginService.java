package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.out.GeneratedRefreshToken;
import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
import com.meridian.platform.identity.application.port.out.PasswordVerifierPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.application.port.out.TokenIssuerPort;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.RefreshTokenSession;
import com.meridian.platform.identity.domain.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordLoginService {

    private final UserRepository userRepository;
    private final PasswordVerifierPort passwordVerifier;
    private final TokenIssuerPort tokenIssuer;
    private final RefreshTokenCodecPort refreshTokenCodec;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final Duration refreshTokenLifetime;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public PasswordLoginService(
            UserRepository userRepository,
            PasswordVerifierPort passwordVerifier,
            TokenIssuerPort tokenIssuer,
            RefreshTokenCodecPort refreshTokenCodec,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            @Value("${meridian.identity.refresh-token.lifetime:7d}") Duration refreshTokenLifetime,
            @Value("${meridian.identity.account-lockout.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${meridian.identity.account-lockout.lock-duration:15m}") Duration lockDuration
    ) {
        this(
                userRepository,
                passwordVerifier,
                tokenIssuer,
                refreshTokenCodec,
                refreshTokenSessionRepository,
                refreshTokenLifetime,
                maxFailedAttempts,
                lockDuration,
                Clock.systemUTC()
        );
    }

    PasswordLoginService(
            UserRepository userRepository,
            PasswordVerifierPort passwordVerifier,
            TokenIssuerPort tokenIssuer,
            RefreshTokenCodecPort refreshTokenCodec,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            Duration refreshTokenLifetime,
            int maxFailedAttempts,
            Duration lockDuration,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier, "passwordVerifier must not be null");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer must not be null");
        this.refreshTokenCodec = Objects.requireNonNull(refreshTokenCodec, "refreshTokenCodec must not be null");
        this.refreshTokenSessionRepository = Objects.requireNonNull(
                refreshTokenSessionRepository,
                "refreshTokenSessionRepository must not be null"
        );
        this.refreshTokenLifetime = requirePositive(refreshTokenLifetime, "refresh-token lifetime");
        if (maxFailedAttempts <= 0) {
            throw new IllegalArgumentException("account-lockout maximum failed attempts must be positive");
        }
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = requirePositive(lockDuration, "account-lockout duration");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public PasswordLoginOutcome login(LoginRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Optional<User> storedUser = userRepository.findByNormalizedEmailForUpdate(normalizeEmail(request.email()));
        if (storedUser.isEmpty()) {
            return PasswordLoginOutcome.invalidCredentials();
        }

        User user = storedUser.get();
        Instant now = Instant.now(clock);
        if (user.isTemporarilyLockedAt(now)) {
            return PasswordLoginOutcome.invalidCredentials();
        }

        boolean expiredLock = user.hasExpiredLockAt(now);
        int currentFailedAttempts = expiredLock ? 0 : user.failedLoginAttempts();
        if (!passwordVerifier.matches(request.password(), user.passwordHash())) {
            int failedAttempts = currentFailedAttempts + 1;
            Instant lockedUntil = failedAttempts >= maxFailedAttempts ? now.plus(lockDuration) : null;
            userRepository.updateLoginProtection(user.id(), failedAttempts, lockedUntil);
            return PasswordLoginOutcome.invalidCredentials();
        }

        if (!user.isActive()) {
            if (expiredLock) {
                userRepository.updateLoginProtection(user.id(), 0, null);
            }
            return PasswordLoginOutcome.accountSuspended();
        }

        if (currentFailedAttempts != 0 || user.lockedUntil() != null) {
            userRepository.updateLoginProtection(user.id(), 0, null);
        }

        if (!user.isEmailVerified()) {
            return PasswordLoginOutcome.emailVerificationRequired();
        }

        IssuedAccessToken accessToken = tokenIssuer.issueAccessToken(user);
        GeneratedRefreshToken refreshToken = refreshTokenCodec.generate();
        Instant expiresAt = now.plus(refreshTokenLifetime);
        refreshTokenSessionRepository.create(new RefreshTokenSession(
                UUID.randomUUID(),
                user.id(),
                UUID.randomUUID(),
                refreshToken.tokenDigest(),
                now,
                expiresAt,
                null,
                null
        ));

        AuthenticationResult result = AuthenticationService.authenticationResult(
                user,
                accessToken,
                refreshToken.tokenValue(),
                now,
                expiresAt
        );
        return PasswordLoginOutcome.success(result);
    }

    private String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static Duration requirePositive(Duration duration, String propertyName) {
        Objects.requireNonNull(duration, propertyName + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
        return duration;
    }
}
