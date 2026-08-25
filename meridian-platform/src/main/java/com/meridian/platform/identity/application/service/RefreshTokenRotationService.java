package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.port.out.GeneratedRefreshToken;
import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenRotationService {

    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final RefreshTokenCodecPort refreshTokenCodec;
    private final UserRepository userRepository;
    private final TokenIssuerPort tokenIssuer;
    private final Duration refreshTokenLifetime;
    private final Clock clock;

    @Autowired
    public RefreshTokenRotationService(
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            RefreshTokenCodecPort refreshTokenCodec,
            UserRepository userRepository,
            TokenIssuerPort tokenIssuer,
            @Value("${meridian.identity.refresh-token.lifetime:7d}") Duration refreshTokenLifetime
    ) {
        this(
                refreshTokenSessionRepository,
                refreshTokenCodec,
                userRepository,
                tokenIssuer,
                refreshTokenLifetime,
                Clock.systemUTC()
        );
    }

    RefreshTokenRotationService(
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            RefreshTokenCodecPort refreshTokenCodec,
            UserRepository userRepository,
            TokenIssuerPort tokenIssuer,
            Duration refreshTokenLifetime,
            Clock clock
    ) {
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.refreshTokenCodec = refreshTokenCodec;
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenLifetime = Objects.requireNonNull(refreshTokenLifetime);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Optional<AuthenticationResult> rotate(String rawRefreshToken) {
        String digest = refreshTokenCodec.digest(rawRefreshToken);
        Optional<RefreshTokenSession> storedSession = refreshTokenSessionRepository.findByDigestForUpdate(digest);
        if (storedSession.isEmpty()) {
            return Optional.empty();
        }

        RefreshTokenSession presentedSession = storedSession.get();
        Instant now = Instant.now(clock);
        if (presentedSession.isConsumed()) {
            refreshTokenSessionRepository.revokeFamily(presentedSession.familyId(), now);
            return Optional.empty();
        }
        if (presentedSession.isRevoked() || presentedSession.isExpiredAt(now)) {
            return Optional.empty();
        }

        Optional<User> currentUser = userRepository.findById(presentedSession.userId());
        if (currentUser.isEmpty()
                || !currentUser.get().isActive()
                || !currentUser.get().isEmailVerified()) {
            refreshTokenSessionRepository.revokeFamily(presentedSession.familyId(), now);
            return Optional.empty();
        }

        User user = currentUser.get();
        GeneratedRefreshToken replacement = refreshTokenCodec.generate();
        Instant replacementExpiresAt = now.plus(refreshTokenLifetime);

        refreshTokenSessionRepository.markConsumed(presentedSession.id(), now);
        refreshTokenSessionRepository.create(new RefreshTokenSession(
                UUID.randomUUID(),
                user.id(),
                presentedSession.familyId(),
                replacement.tokenDigest(),
                now,
                replacementExpiresAt,
                null,
                null
        ));

        IssuedAccessToken accessToken = tokenIssuer.issueAccessToken(user);
        return Optional.of(AuthenticationService.authenticationResult(
                user,
                accessToken,
                replacement.tokenValue(),
                now,
                replacementExpiresAt
        ));
    }
}
