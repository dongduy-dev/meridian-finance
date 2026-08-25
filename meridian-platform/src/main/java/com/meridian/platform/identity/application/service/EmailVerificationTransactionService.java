package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.port.out.EmailVerificationTokenCodecPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenRepository;
import com.meridian.platform.identity.application.port.out.GeneratedEmailVerificationToken;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.EmailVerificationToken;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
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
public class EmailVerificationTransactionService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationTokenCodecPort tokenCodec;
    private final Duration tokenLifetime;
    private final Clock clock;

    public EmailVerificationTransactionService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            EmailVerificationTokenCodecPort tokenCodec,
            @Value("${meridian.identity.email-verification.lifetime:24h}") Duration tokenLifetime,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.tokenLifetime = requirePositive(tokenLifetime);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Optional<PendingEmailVerificationDelivery> issueForEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        Optional<User> storedUser = userRepository.findByNormalizedEmailForUpdate(normalizedEmail);
        if (storedUser.isEmpty()) {
            return Optional.empty();
        }

        User user = storedUser.orElseThrow();
        if (user.userType() != UserType.CUSTOMER || user.isEmailVerified()) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        tokenRepository.revokeActiveForUser(user.id(), now);
        GeneratedEmailVerificationToken generated = tokenCodec.generate();
        tokenRepository.create(new EmailVerificationToken(
                UUID.randomUUID(),
                user.id(),
                generated.tokenDigest(),
                now,
                now.plus(tokenLifetime),
                null,
                null
        ));
        return Optional.of(new PendingEmailVerificationDelivery(user.email(), generated.tokenValue()));
    }

    @Transactional
    public void confirm(String rawToken) {
        String digest = tokenCodec.digest(rawToken);
        EmailVerificationToken token = tokenRepository.findByDigestForUpdate(digest)
                .orElseThrow(EmailVerificationTransactionService::invalidToken);
        Instant now = Instant.now(clock);

        User user = userRepository.findByIdForUpdate(token.userId())
                .orElseThrow(EmailVerificationTransactionService::invalidToken);
        if (token.isConsumed()) {
            if (user.isEmailVerified()) {
                return;
            }
            throw invalidToken();
        }
        if (token.isRevoked() || token.isExpiredAt(now)) {
            throw invalidToken();
        }

        if (!user.isEmailVerified()) {
            userRepository.markEmailVerified(user.id(), now);
        }
        tokenRepository.markConsumed(token.id(), now);
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "email-verification lifetime must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("email-verification lifetime must be positive");
        }
        return duration;
    }

    private static AuthenticationFailedException invalidToken() {
        return new AuthenticationFailedException(
                "INVALID_EMAIL_VERIFICATION_TOKEN",
                "Email verification token is invalid or expired."
        );
    }
}
