package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AccessTokenReference;
import com.meridian.platform.identity.application.dto.CurrentSessionLogoutCommand;
import com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.domain.model.RefreshTokenSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentSessionLogoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final UUID FAMILY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TOKEN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final RefreshTokenCodecPort refreshTokenCodec = mock(RefreshTokenCodecPort.class);
    private final RefreshTokenSessionRepository refreshTokenSessionRepository =
            mock(RefreshTokenSessionRepository.class);
    private final AccessTokenRevocationRepository accessTokenRevocationRepository =
            mock(AccessTokenRevocationRepository.class);
    private final CurrentSessionLogoutService service = new CurrentSessionLogoutService(
            refreshTokenCodec,
            refreshTokenSessionRepository,
            accessTokenRevocationRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void revokesKnownRefreshFamilyAndUnexpiredAccessToken() {
        when(refreshTokenCodec.digest("raw-refresh-token")).thenReturn("digest");
        when(refreshTokenSessionRepository.findByDigestForUpdate("digest"))
                .thenReturn(Optional.of(refreshSession()));
        Instant accessTokenExpiry = NOW.plusSeconds(3600);

        service.logout(new CurrentSessionLogoutCommand(
                Optional.of("raw-refresh-token"),
                Optional.of(new AccessTokenReference(TOKEN_ID, accessTokenExpiry))
        ));

        verify(refreshTokenSessionRepository).revokeFamily(FAMILY_ID, NOW);
        verify(accessTokenRevocationRepository).revoke(TOKEN_ID, NOW, accessTokenExpiry);
    }

    @Test
    void unknownRefreshAndExpiredAccessCredentialsDoNotMutateState() {
        when(refreshTokenCodec.digest("unknown-refresh-token")).thenReturn("unknown-digest");
        when(refreshTokenSessionRepository.findByDigestForUpdate("unknown-digest"))
                .thenReturn(Optional.empty());

        service.logout(new CurrentSessionLogoutCommand(
                Optional.of("unknown-refresh-token"),
                Optional.of(new AccessTokenReference(TOKEN_ID, NOW))
        ));

        verify(refreshTokenSessionRepository, never()).revokeFamily(FAMILY_ID, NOW);
        verify(accessTokenRevocationRepository, never()).revoke(TOKEN_ID, NOW, NOW);
    }

    private RefreshTokenSession refreshSession() {
        return new RefreshTokenSession(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                FAMILY_ID,
                "digest",
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                NOW.minusSeconds(30),
                null
        );
    }
}
