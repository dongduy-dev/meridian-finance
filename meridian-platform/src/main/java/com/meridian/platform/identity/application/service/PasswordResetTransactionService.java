package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.port.out.GeneratedPasswordResetToken;
import com.meridian.platform.identity.application.port.out.PasswordHashingPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenRepository;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.PasswordResetToken;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetTransactionService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetTokenCodecPort tokenCodec;
    private final PasswordHashingPort passwordHashingPort;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final Duration tokenLifetime;
    private final Clock clock;

    @Autowired
    public PasswordResetTransactionService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetTokenCodecPort tokenCodec,
            PasswordHashingPort passwordHashingPort,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            @Value("${meridian.identity.password-reset.lifetime:30m}") Duration tokenLifetime
    ) {
        this(
                userRepository,
                tokenRepository,
                tokenCodec,
                passwordHashingPort,
                refreshTokenSessionRepository,
                tokenLifetime,
                Clock.systemUTC()
        );
    }

    PasswordResetTransactionService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetTokenCodecPort tokenCodec,
            PasswordHashingPort passwordHashingPort,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            Duration tokenLifetime,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.passwordHashingPort = Objects.requireNonNull(passwordHashingPort);
        this.refreshTokenSessionRepository = Objects.requireNonNull(refreshTokenSessionRepository);
        this.tokenLifetime = requirePositive(tokenLifetime);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Optional<PendingPasswordResetDelivery> issueForEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        Optional<User> storedUser = userRepository.findByNormalizedEmailForUpdate(normalizedEmail);
        if (storedUser.isEmpty()) {
            return Optional.empty();
        }

        User user = storedUser.orElseThrow();
        if (!user.isActive() || !user.isEmailVerified()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        tokenRepository.revokeActiveForUser(user.id(), now);
        GeneratedPasswordResetToken generated = tokenCodec.generate();
        tokenRepository.create(new PasswordResetToken(
                UUID.randomUUID(),
                user.id(),
                generated.tokenDigest(),
                now,
                now.plus(tokenLifetime),
                null,
                null
        ));
        return Optional.of(new PendingPasswordResetDelivery(user.email(), generated.tokenValue()));
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        String digest = tokenCodec.digest(rawToken);
        PasswordResetToken token = tokenRepository.findByDigestForUpdate(digest)
                .orElseThrow(PasswordResetTransactionService::invalidToken);
        Instant now = Instant.now(clock);

        User user = userRepository.findByIdForUpdate(token.userId())
                .orElseThrow(PasswordResetTransactionService::invalidToken);
        if (token.isConsumed()
                || token.isRevoked()
                || token.isExpiredAt(now)
                || !user.isActive()
                || !user.isEmailVerified()) {
            throw invalidToken();
        }

        String passwordHash = passwordHashingPort.hash(Objects.requireNonNull(newPassword, "newPassword must not be null"));
        userRepository.replacePasswordAndClearLoginProtection(user.id(), passwordHash);
        tokenRepository.markConsumed(token.id(), now);
        refreshTokenSessionRepository.revokeAllForUser(user.id(), now);
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "password-reset lifetime must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("password-reset lifetime must be positive");
        }
        return duration;
    }

    private static AuthenticationFailedException invalidToken() {
        return new AuthenticationFailedException(
                "INVALID_PASSWORD_RESET_TOKEN",
                "Password reset token is invalid or expired."
        );
    }
}
