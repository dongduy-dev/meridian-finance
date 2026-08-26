package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.out.GeneratedRefreshToken;
import com.meridian.platform.identity.application.port.out.IssuedAccessToken;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenSessionRepository;
import com.meridian.platform.identity.application.port.out.UserRepository;
import com.meridian.platform.identity.domain.model.RefreshTokenSession;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.identity.domain.model.UserType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordLoginServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Test
    void validLoginSucceedsForCustomerAndStaffUsers() {
        for (User user : Set.of(
                user(UserType.CUSTOMER, UserStatus.ACTIVE, 0, null),
                user(UserType.STAFF, UserStatus.ACTIVE, 0, null)
        )) {
            InMemoryUserRepository users = new InMemoryUserRepository(user);
            CapturingRefreshTokenRepository refreshTokens = new CapturingRefreshTokenRepository();

            PasswordLoginOutcome outcome = service(users, refreshTokens).login(login("valid-password"));

            assertTrue(outcome.result().isPresent());
            assertEquals(USER_ID, outcome.result().orElseThrow().response().userId());
            assertEquals(user.userType().name(), outcome.result().orElseThrow().response().userType());
            assertEquals(0, users.user.failedLoginAttempts());
            assertNull(users.user.lockedUntil());
            assertEquals(USER_ID, refreshTokens.created.userId());
        }
    }

    @Test
    void wrongPasswordRecordsFailure() {
        InMemoryUserRepository users = new InMemoryUserRepository(activeUser());

        PasswordLoginOutcome outcome = service(users, new CapturingRefreshTokenRepository())
                .login(login("wrong-password"));

        assertEquals(PasswordLoginOutcome.Failure.INVALID_CREDENTIALS, outcome.failure());
        assertEquals(1, users.user.failedLoginAttempts());
        assertNull(users.user.lockedUntil());
        assertEquals(1, users.updateCount);
    }

    @Test
    void repeatedFailuresReachThresholdAndLock() {
        InMemoryUserRepository users = new InMemoryUserRepository(activeUser());
        PasswordLoginService service = service(users, new CapturingRefreshTokenRepository());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals(
                    PasswordLoginOutcome.Failure.INVALID_CREDENTIALS,
                    service.login(login("wrong-password")).failure()
            );
        }

        assertEquals(3, users.user.failedLoginAttempts());
        assertEquals(NOW.plus(LOCK_DURATION), users.user.lockedUntil());
    }

    @Test
    void correctPasswordCannotLoginDuringActiveLock() {
        InMemoryUserRepository users = new InMemoryUserRepository(
                user(UserType.CUSTOMER, UserStatus.ACTIVE, 3, NOW.plus(LOCK_DURATION))
        );
        CapturingRefreshTokenRepository refreshTokens = new CapturingRefreshTokenRepository();

        PasswordLoginOutcome outcome = service(users, refreshTokens).login(login("valid-password"));

        assertEquals(PasswordLoginOutcome.Failure.INVALID_CREDENTIALS, outcome.failure());
        assertEquals(0, users.updateCount);
        assertNull(refreshTokens.created);
    }

    @Test
    void expiredLockStartsFreshFailureSequence() {
        InMemoryUserRepository users = new InMemoryUserRepository(
                user(UserType.CUSTOMER, UserStatus.ACTIVE, 3, NOW.minusSeconds(1))
        );

        PasswordLoginOutcome outcome = service(users, new CapturingRefreshTokenRepository())
                .login(login("wrong-password"));

        assertEquals(PasswordLoginOutcome.Failure.INVALID_CREDENTIALS, outcome.failure());
        assertEquals(1, users.user.failedLoginAttempts());
        assertNull(users.user.lockedUntil());
    }

    @Test
    void successfulLoginClearsPreviousFailureState() {
        InMemoryUserRepository users = new InMemoryUserRepository(
                user(UserType.CUSTOMER, UserStatus.ACTIVE, 2, null)
        );

        PasswordLoginOutcome outcome = service(users, new CapturingRefreshTokenRepository())
                .login(login("valid-password"));

        assertTrue(outcome.result().isPresent());
        assertEquals(0, users.user.failedLoginAttempts());
        assertNull(users.user.lockedUntil());
        assertEquals(1, users.updateCount);
    }

    @Test
    void correctPasswordForUnverifiedCustomerCreatesNoCredentialsAndClearsStaleFailures() {
        User verifiedShape = user(UserType.CUSTOMER, UserStatus.ACTIVE, 2, null);
        InMemoryUserRepository users = new InMemoryUserRepository(new User(
                verifiedShape.id(),
                verifiedShape.email(),
                verifiedShape.passwordHash(),
                verifiedShape.userType(),
                verifiedShape.status(),
                verifiedShape.displayName(),
                verifiedShape.customerId(),
                verifiedShape.roles(),
                verifiedShape.permissions(),
                verifiedShape.failedLoginAttempts(),
                verifiedShape.lockedUntil(),
                null
        ));
        CapturingRefreshTokenRepository refreshTokens = new CapturingRefreshTokenRepository();

        PasswordLoginOutcome outcome = service(users, refreshTokens).login(login("valid-password"));

        assertEquals(PasswordLoginOutcome.Failure.EMAIL_VERIFICATION_REQUIRED, outcome.failure());
        assertEquals(0, users.user.failedLoginAttempts());
        assertNull(users.user.lockedUntil());
        assertNull(refreshTokens.created);
    }

    @Test
    void unknownEmailReturnsSafeFailureWithoutPersistenceState() {
        InMemoryUserRepository users = new InMemoryUserRepository(null);

        PasswordLoginOutcome outcome = service(users, new CapturingRefreshTokenRepository())
                .login(new LoginRequest("unknown@meridian.local", "wrong-password"));

        assertEquals(PasswordLoginOutcome.Failure.INVALID_CREDENTIALS, outcome.failure());
        assertEquals(0, users.updateCount);
        assertNull(users.user);
    }

    @Test
    void suspendedAndDisabledBehaviorRemainsCompatible() {
        for (UserStatus status : Set.of(UserStatus.SUSPENDED, UserStatus.DISABLED)) {
            InMemoryUserRepository correctPasswordUsers = new InMemoryUserRepository(
                    user(UserType.STAFF, status, 0, null)
            );
            assertEquals(
                    PasswordLoginOutcome.Failure.ACCOUNT_SUSPENDED,
                    service(correctPasswordUsers, new CapturingRefreshTokenRepository())
                            .login(login("valid-password"))
                            .failure()
            );

            InMemoryUserRepository wrongPasswordUsers = new InMemoryUserRepository(
                    user(UserType.STAFF, status, 0, null)
            );
            assertEquals(
                    PasswordLoginOutcome.Failure.INVALID_CREDENTIALS,
                    service(wrongPasswordUsers, new CapturingRefreshTokenRepository())
                            .login(login("wrong-password"))
                            .failure()
            );
            assertEquals(1, wrongPasswordUsers.user.failedLoginAttempts());
        }
    }

    @Test
    void invalidLockoutConfigurationFailsFast() {
        InMemoryUserRepository users = new InMemoryUserRepository(activeUser());
        CapturingRefreshTokenRepository refreshTokens = new CapturingRefreshTokenRepository();

        assertThrows(IllegalArgumentException.class, () -> service(users, refreshTokens, 0, LOCK_DURATION));
        assertThrows(IllegalArgumentException.class, () -> service(users, refreshTokens, 3, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> service(users, refreshTokens, 3, Duration.ofSeconds(-1))
        );
    }

    private PasswordLoginService service(
            InMemoryUserRepository users,
            CapturingRefreshTokenRepository refreshTokens
    ) {
        return service(users, refreshTokens, 3, LOCK_DURATION);
    }

    private PasswordLoginService service(
            InMemoryUserRepository users,
            CapturingRefreshTokenRepository refreshTokens,
            int maxFailedAttempts,
            Duration lockDuration
    ) {
        return new PasswordLoginService(
                users,
                (rawPassword, passwordHash) -> rawPassword.equals("valid-password"),
                user -> new IssuedAccessToken("access-token", NOW.plusSeconds(3600)),
                new RefreshTokenCodecPort() {
                    @Override
                    public GeneratedRefreshToken generate() {
                        return new GeneratedRefreshToken("raw-refresh-token", "sha256-digest");
                    }

                    @Override
                    public String digest(String rawToken) {
                        return "sha256-digest";
                    }
                },
                refreshTokens,
                Duration.ofDays(7),
                maxFailedAttempts,
                lockDuration,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private LoginRequest login(String password) {
        return new LoginRequest(" Customer.Demo@Meridian.Local ", password);
    }

    private User activeUser() {
        return user(UserType.CUSTOMER, UserStatus.ACTIVE, 0, null);
    }

    private User user(UserType userType, UserStatus status, int attempts, Instant lockedUntil) {
        return new User(
                USER_ID,
                "customer.demo@meridian.local",
                "hash",
                userType,
                status,
                "Demo User",
                userType == UserType.CUSTOMER ? CUSTOMER_ID : null,
                userType == UserType.CUSTOMER ? Set.of("CUSTOMER") : Set.of("LOAN_OFFICER"),
                userType == UserType.CUSTOMER ? Set.of("loan:submit") : Set.of("loan:read"),
                attempts,
                lockedUntil,
                Instant.EPOCH
        );
    }

    private static final class InMemoryUserRepository implements UserRepository {
        private User user;
        private int updateCount;

        private InMemoryUserRepository(User user) {
            this.user = user;
        }

        @Override
        public Optional<User> findByNormalizedEmail(String normalizedEmail) {
            return Optional.ofNullable(user);
        }

        @Override
        public Optional<User> findByNormalizedEmailForUpdate(String normalizedEmail) {
            return Optional.ofNullable(user);
        }

        @Override
        public Optional<User> findById(UUID userId) {
            return Optional.ofNullable(user);
        }

        @Override
        public Optional<User> findByIdForUpdate(UUID userId) {
            return Optional.ofNullable(user);
        }

        @Override
        public void createCustomerUser(User user) {
            throw new AssertionError("User creation should not be called.");
        }

        @Override
        public void markEmailVerified(UUID userId, Instant verifiedAt) {
            throw new AssertionError("Email verification should not be called.");
        }

        @Override
        public void updateLoginProtection(UUID userId, int failedLoginAttempts, Instant lockedUntil) {
            updateCount++;
            user = new User(
                    user.id(),
                    user.email(),
                    user.passwordHash(),
                    user.userType(),
                    user.status(),
                    user.displayName(),
                    user.customerId(),
                    user.roles(),
                    user.permissions(),
                    failedLoginAttempts,
                    lockedUntil,
                    user.emailVerifiedAt()
            );
        }

        @Override
        public void replacePasswordAndClearLoginProtection(UUID userId, String passwordHash) {
            throw new AssertionError("Password replacement should not be called.");
        }
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

        @Override
        public void revokeAllForUser(UUID userId, Instant revokedAt) {
            throw new AssertionError("User-wide revocation should not be called.");
        }
    }
}
