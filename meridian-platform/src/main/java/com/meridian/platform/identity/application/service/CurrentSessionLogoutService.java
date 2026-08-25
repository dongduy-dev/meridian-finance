package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AccessTokenReference;
import com.meridian.platform.identity.application.dto.CurrentSessionLogoutCommand;
import com.meridian.platform.identity.application.port.in.LogoutUseCase;
import com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.domain.model.RefreshTokenSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class CurrentSessionLogoutService implements LogoutUseCase {

    private final RefreshTokenCodecPort refreshTokenCodec;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final AccessTokenRevocationRepository accessTokenRevocationRepository;
    private final Clock clock;

    public CurrentSessionLogoutService(
            RefreshTokenCodecPort refreshTokenCodec,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            AccessTokenRevocationRepository accessTokenRevocationRepository,
            Clock clock
    ) {
        this.refreshTokenCodec = refreshTokenCodec;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.accessTokenRevocationRepository = accessTokenRevocationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void logout(CurrentSessionLogoutCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<RefreshTokenSession> refreshSession = command.refreshToken()
                .map(refreshTokenCodec::digest)
                .flatMap(refreshTokenSessionRepository::findByDigestForUpdate);
        Instant now = Instant.now(clock);

        refreshSession.ifPresent(session ->
                refreshTokenSessionRepository.revokeFamily(session.familyId(), now));

        command.accessToken()
                .filter(accessToken -> accessToken.expiresAt().isAfter(now))
                .ifPresent(accessToken -> revokeAccessToken(accessToken, now));
    }

    private void revokeAccessToken(AccessTokenReference accessToken, Instant revokedAt) {
        accessTokenRevocationRepository.revoke(
                accessToken.tokenId(),
                revokedAt,
                accessToken.expiresAt()
        );
    }
}
