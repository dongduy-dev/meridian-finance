package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AuthResponse;
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
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");

    @Test
    void returnsBearerTokenForActiveUserWithValidPassword() {
        CapturingRefreshTokenRepository refreshTokens = new CapturingRefreshTokenRepository();
        AuthenticationService service = new AuthenticationService(
                repository(customerUser(UserStatus.ACTIVE)),
                (rawPassword, passwordHash) -> rawPassword.equals("valid-password"),
                user -> new IssuedAccessToken("token-value", NOW.plusSeconds(3600)),
                fixedRefreshTokenCodec(),
                refreshTokens,
                null,
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        AuthenticationResult result = service.login(new LoginRequest(
                " Customer.Demo@Meridian.Local ",
                "valid-password"
        ));
        AuthResponse response = result.response();

        assertEquals("Bearer", response.tokenType());
        assertEquals("token-value", response.accessToken());
        assertEquals(USER_ID, response.userId());
        assertEquals(CUSTOMER_ID, response.customerId());
        assertEquals(Set.of("CUSTOMER"), response.roles());
        assertEquals(Set.of("loan:submit"), response.permissions());
        assertEquals("raw-refresh-token", result.refreshToken());
        assertEquals(NOW, result.refreshTokenIssuedAt());
        assertEquals(NOW.plus(Duration.ofDays(7)), result.refreshTokenExpiresAt());
        assertEquals("sha256-digest", refreshTokens.created.tokenDigest());
        assertNotEquals(result.refreshToken(), refreshTokens.created.tokenDigest());
        assertNull(refreshTokens.created.consumedAt());
        assertNull(refreshTokens.created.revokedAt());
    }

    @Test
    void rejectsInvalidCredentials() {
        AuthenticationService service = new AuthenticationService(
                repository(customerUser(UserStatus.ACTIVE)),
                (rawPassword, passwordHash) -> false,
                unusedTokenIssuer(),
                fixedRefreshTokenCodec(),
                new CapturingRefreshTokenRepository(),
                null,
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("customer.demo@meridian.local", "wrong-password"))
        );

        assertEquals("INVALID_CREDENTIALS", exception.getErrorCode());
    }

    @Test
    void rejectsInactiveUser() {
        AuthenticationService service = new AuthenticationService(
                repository(customerUser(UserStatus.SUSPENDED)),
                (rawPassword, passwordHash) -> true,
                unusedTokenIssuer(),
                fixedRefreshTokenCodec(),
                new CapturingRefreshTokenRepository(),
                null,
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("customer.demo@meridian.local", "irrelevant"))
        );

        assertEquals("ACCOUNT_SUSPENDED", exception.getErrorCode());
    }

    private User customerUser(UserStatus status) {
        return new User(
                USER_ID,
                "customer.demo@meridian.local",
                "hash",
                UserType.CUSTOMER,
                status,
                "Customer Demo",
                CUSTOMER_ID,
                Set.of("CUSTOMER"),
                Set.of("loan:submit")
        );
    }

    private TokenIssuerPort unusedTokenIssuer() {
        return user -> {
            throw new AssertionError("Token issuer should not be called.");
        };
    }

    private UserRepository repository(User user) {
        return new UserRepository() {
            @Override
            public Optional<User> findByNormalizedEmail(String normalizedEmail) {
                return Optional.of(user);
            }

            @Override
            public Optional<User> findById(UUID userId) {
                return Optional.of(user);
            }
        };
    }

    private RefreshTokenCodecPort fixedRefreshTokenCodec() {
        return new RefreshTokenCodecPort() {
            @Override
            public GeneratedRefreshToken generate() {
                return new GeneratedRefreshToken("raw-refresh-token", "sha256-digest");
            }

            @Override
            public String digest(String rawToken) {
                return "sha256-digest";
            }
        };
    }

    private static final class CapturingRefreshTokenRepository implements RefreshTokenSessionRepository {
        private RefreshTokenSession created;

        @Override
        public void create(RefreshTokenSession session) {
            created = session;
        }

        @Override
        public Optional<RefreshTokenSession> findByDigestForUpdate(String tokenDigest) {
            throw new AssertionError("Refresh lookup should not be called.");
        }

        @Override
        public void markConsumed(UUID sessionId, Instant consumedAt) {
            throw new AssertionError("Refresh consumption should not be called.");
        }

        @Override
        public void revokeFamily(UUID familyId, Instant revokedAt) {
            throw new AssertionError("Family revocation should not be called.");
        }
    }
}

