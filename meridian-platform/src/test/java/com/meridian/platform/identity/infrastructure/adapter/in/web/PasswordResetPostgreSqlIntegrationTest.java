package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.dto.PasswordResetConfirmationRequest;
import com.meridian.platform.identity.application.dto.PasswordResetRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase;
import com.meridian.platform.identity.application.port.in.RequestPasswordResetUseCase;
import com.meridian.platform.identity.application.port.out.PasswordResetNotificationPort;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.identity.infrastructure.security.JwtTokenService;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "meridian.identity.rate-limit.login.max-requests=1000",
        "meridian.identity.rate-limit.refresh.max-requests=1000",
        "meridian.identity.rate-limit.password-reset-request.max-requests=1000"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_password_reset_v52_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID STAFF_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final String CUSTOMER_EMAIL = "customer.demo@meridian.local";
    private static final String STAFF_EMAIL = "loan.officer@meridian.local";
    private static final String OLD_PASSWORD = "Meridian@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @Autowired
    private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @Autowired
    private AuthenticationUseCase authenticationUseCase;

    @Autowired
    private PasswordResetTokenCodecPort tokenCodec;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private PasswordResetNotificationPort notificationPort;

    private final ConcurrentLinkedQueue<Delivery> deliveries = new ConcurrentLinkedQueue<>();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @BeforeEach
    void resetIdentityState() {
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM refresh_token_sessions");
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET password_hash = ?, status = 'ACTIVE', failed_login_attempts = 0,
                            locked_until = NULL, email_verified_at = CURRENT_TIMESTAMP
                        WHERE id IN (?, ?)
                        """,
                passwordEncoder.encode(OLD_PASSWORD),
                CUSTOMER_USER_ID,
                STAFF_USER_ID
        );
        reset(notificationPort);
        deliveries.clear();
        doAnswer(invocation -> {
            deliveries.add(new Delivery(
                    invocation.getArgument(0, String.class),
                    invocation.getArgument(1, String.class)
            ));
            return null;
        }).when(notificationPort).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestIsEnumerationSafeAndIssuesDigestOnlyStateForVerifiedActiveCustomerAndStaff(CapturedOutput output)
            throws Exception {
        mockMvc.perform(resetRequest("unknown@example.com"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        assertEquals(0, count("SELECT COUNT(*) FROM password_reset_tokens"));
        assertTrue(deliveries.isEmpty());

        jdbcTemplate.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", CUSTOMER_USER_ID);
        mockMvc.perform(resetRequest(CUSTOMER_EMAIL)).andExpect(status().isAccepted());
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE id = ?", CUSTOMER_USER_ID);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", CUSTOMER_USER_ID);
        mockMvc.perform(resetRequest(CUSTOMER_EMAIL)).andExpect(status().isAccepted());
        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", CUSTOMER_USER_ID);
        mockMvc.perform(resetRequest(CUSTOMER_EMAIL)).andExpect(status().isAccepted());
        assertEquals(0, count("SELECT COUNT(*) FROM password_reset_tokens"));
        assertTrue(deliveries.isEmpty());

        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", CUSTOMER_USER_ID);
        mockMvc.perform(resetRequest("  CUSTOMER.DEMO@MERIDIAN.LOCAL  "))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        mockMvc.perform(resetRequest(STAFF_EMAIL)).andExpect(status().isAccepted());

        assertEquals(2, count("SELECT COUNT(*) FROM password_reset_tokens"));
        assertEquals(2, deliveries.size());
        for (Delivery delivery : deliveries) {
            UUID userId = delivery.recipient().equals(CUSTOMER_EMAIL) ? CUSTOMER_USER_ID : STAFF_USER_ID;
            String storedDigest = text(
                    "SELECT token_digest FROM password_reset_tokens WHERE user_id = ?",
                    userId
            );
            assertEquals(tokenCodec.digest(delivery.rawToken()), storedDigest);
            assertNotEquals(delivery.rawToken(), storedDigest);
            assertEquals(0, count(
                    "SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + delivery.rawToken() + "%"
            ));
            assertFalse(output.getAll().contains(delivery.rawToken()));
        }
    }

    @Test
    void replacementRevokesTheOldTokenAndConcurrentRequestsLeaveOneActiveToken() throws Exception {
        requestPasswordResetUseCase.requestReset(new PasswordResetRequest(CUSTOMER_EMAIL));
        String first = deliveries.remove().rawToken();
        requestPasswordResetUseCase.requestReset(new PasswordResetRequest(CUSTOMER_EMAIL));
        String replacement = deliveries.remove().rawToken();

        assertNotEquals(first, replacement);
        assertInvalidToken(first);
        assertEquals(1, count("""
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE user_id = ? AND consumed_at IS NULL AND revoked_at IS NULL
                """, CUSTOMER_USER_ID));

        deliveries.clear();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> requestResetConcurrently(ready, start)),
                    executor.submit(() -> requestResetConcurrently(ready, start))
            );
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Boolean> attempt : attempts) {
                assertTrue(attempt.get(30, TimeUnit.SECONDS));
            }
        }

        assertEquals(1, count("""
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE user_id = ? AND consumed_at IS NULL AND revoked_at IS NULL
                """, CUSTOMER_USER_ID));
        String activeDigest = text("""
                SELECT token_digest FROM password_reset_tokens
                WHERE user_id = ? AND consumed_at IS NULL AND revoked_at IS NULL
                """, CUSTOMER_USER_ID);
        assertEquals(1, deliveries.stream()
                .filter(delivery -> tokenCodec.digest(delivery.rawToken()).equals(activeDigest))
                .count());
    }

    @Test
    void smtpFailureDoesNotRollBackIssuedTokenOrExposeAccountState(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(notificationPort)
                .sendPasswordResetEmail(anyString(), anyString());

        mockMvc.perform(resetRequest(CUSTOMER_EMAIL))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertEquals(1, count("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?", CUSTOMER_USER_ID));
        assertFalse(output.getAll().contains(CUSTOMER_EMAIL));
    }

    @Test
    void confirmationAtomicallyChangesPasswordClearsLockoutAndRevokesOnlyTheUsersRefreshFamilies()
            throws Exception {
        AuthenticationResult customerFamilyA = login(CUSTOMER_EMAIL, OLD_PASSWORD);
        AuthenticationResult customerFamilyB = login(CUSTOMER_EMAIL, OLD_PASSWORD);
        AuthenticationResult unrelatedStaffFamily = login(STAFF_EMAIL, OLD_PASSWORD);
        assertNotNull(jwtTokenService.parseAccessToken(customerFamilyA.response().accessToken()));

        jdbcTemplate.update(
                """
                        UPDATE users
                        SET failed_login_attempts = 5, locked_until = CURRENT_TIMESTAMP + INTERVAL '15 minutes'
                        WHERE id = ?
                        """,
                CUSTOMER_USER_ID
        );
        String statusBefore = text("SELECT status FROM users WHERE id = ?", CUSTOMER_USER_ID);
        LocalDateTime verifiedBefore = jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE id = ?", LocalDateTime.class, CUSTOMER_USER_ID
        );
        UUID customerIdBefore = jdbcTemplate.queryForObject(
                "SELECT customer_id FROM users WHERE id = ?", UUID.class, CUSTOMER_USER_ID
        );
        String customerVerificationBefore = text(
                "SELECT verification_status FROM customers WHERE id = ?", customerIdBefore
        );
        String customerStatusBefore = text("SELECT status FROM customers WHERE id = ?", customerIdBefore);
        String profileCompletionBefore = text(
                "SELECT profile_completion_status FROM customers WHERE id = ?", customerIdBefore
        );
        int roleCountBefore = count("SELECT COUNT(*) FROM role_assignments WHERE user_id = ?", CUSTOMER_USER_ID);
        int permissionCountBefore = count("""
                SELECT COUNT(DISTINCT role_permission.permission_id)
                FROM role_assignments assignment
                JOIN role_permissions role_permission ON role_permission.role_id = assignment.role_id
                WHERE assignment.user_id = ?
                """, CUSTOMER_USER_ID);

        requestPasswordResetUseCase.requestReset(new PasswordResetRequest(CUSTOMER_EMAIL));
        String rawToken = deliveries.remove().rawToken();
        String newPassword = "new-password-value";

        mockMvc.perform(resetConfirmation(rawToken, newPassword)
                        .cookie(new Cookie("MERIDIAN_REFRESH_TOKEN", customerFamilyA.refreshToken())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("MERIDIAN_REFRESH_TOKEN="),
                                org.hamcrest.Matchers.containsString("Max-Age=0"),
                                org.hamcrest.Matchers.containsString("Path=/api/v1/auth")
                        )
                ));

        String storedHash = text("SELECT password_hash FROM users WHERE id = ?", CUSTOMER_USER_ID);
        assertTrue(passwordEncoder.matches(newPassword, storedHash));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, storedHash));
        assertEquals(0, count("SELECT failed_login_attempts FROM users WHERE id = ?", CUSTOMER_USER_ID));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT locked_until FROM users WHERE id = ?", LocalDateTime.class, CUSTOMER_USER_ID
        ));
        assertEquals(statusBefore, text("SELECT status FROM users WHERE id = ?", CUSTOMER_USER_ID));
        assertEquals(verifiedBefore, jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE id = ?", LocalDateTime.class, CUSTOMER_USER_ID
        ));
        assertEquals(customerIdBefore, jdbcTemplate.queryForObject(
                "SELECT customer_id FROM users WHERE id = ?", UUID.class, CUSTOMER_USER_ID
        ));
        assertEquals(customerVerificationBefore, text(
                "SELECT verification_status FROM customers WHERE id = ?", customerIdBefore
        ));
        assertEquals(customerStatusBefore, text("SELECT status FROM customers WHERE id = ?", customerIdBefore));
        assertEquals(profileCompletionBefore, text(
                "SELECT profile_completion_status FROM customers WHERE id = ?", customerIdBefore
        ));
        assertEquals(roleCountBefore, count("SELECT COUNT(*) FROM role_assignments WHERE user_id = ?", CUSTOMER_USER_ID));
        assertEquals(permissionCountBefore, count("""
                SELECT COUNT(DISTINCT role_permission.permission_id)
                FROM role_assignments assignment
                JOIN role_permissions role_permission ON role_permission.role_id = assignment.role_id
                WHERE assignment.user_id = ?
                """, CUSTOMER_USER_ID));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE user_id = ? AND consumed_at IS NOT NULL
                """, CUSTOMER_USER_ID));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE user_id = ? AND revoked_at IS NULL
                """, CUSTOMER_USER_ID));

        assertInvalidRefresh(customerFamilyA.refreshToken());
        assertInvalidRefresh(customerFamilyB.refreshToken());
        assertNotNull(authenticationUseCase.refresh(unrelatedStaffFamily.refreshToken()));
        mockMvc.perform(loginRequest(CUSTOMER_EMAIL, OLD_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        mockMvc.perform(loginRequest(CUSTOMER_EMAIL, newPassword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void unknownExpiredRevokedAndConsumedTokensShareOneErrorAndReplayCannotChangePassword() throws Exception {
        assertInvalidToken("unknown-token");

        String ineligible = issueToken();
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", CUSTOMER_USER_ID);
        assertInvalidToken(ineligible);
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", CUSTOMER_USER_ID);

        String expired = issueToken();
        jdbcTemplate.update(
                """
                        UPDATE password_reset_tokens
                        SET issued_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                            expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                        WHERE token_digest = ?
                        """,
                tokenCodec.digest(expired)
        );
        assertInvalidToken(expired);

        String revoked = issueToken();
        String valid = issueToken();
        assertInvalidToken(revoked);
        confirmPasswordResetUseCase.confirmReset(new PasswordResetConfirmationRequest(valid, "first-new-password"));
        assertInvalidToken(valid);

        String hashAfterSuccess = text("SELECT password_hash FROM users WHERE id = ?", CUSTOMER_USER_ID);
        AuthenticationFailedException replay = assertThrows(
                AuthenticationFailedException.class,
                () -> confirmPasswordResetUseCase.confirmReset(
                        new PasswordResetConfirmationRequest(valid, "different-password-value")
                )
        );
        assertEquals("INVALID_PASSWORD_RESET_TOKEN", replay.getErrorCode());
        assertEquals(hashAfterSuccess, text("SELECT password_hash FROM users WHERE id = ?", CUSTOMER_USER_ID));
        assertTrue(passwordEncoder.matches("first-new-password", hashAfterSuccess));
    }

    @Test
    void concurrentConfirmationsAllowExactlyOnePasswordMutation() throws Exception {
        String token = issueToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> confirmConcurrently(token, "concurrent-password-a", ready, start)),
                    executor.submit(() -> confirmConcurrently(token, "concurrent-password-b", ready, start))
            );
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        }

        String storedHash = text("SELECT password_hash FROM users WHERE id = ?", CUSTOMER_USER_ID);
        assertTrue(passwordEncoder.matches("concurrent-password-a", storedHash)
                ^ passwordEncoder.matches("concurrent-password-b", storedHash));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE user_id = ? AND consumed_at IS NOT NULL
                """, CUSTOMER_USER_ID));
    }

    @Test
    void refreshRevocationFailureRollsBackPasswordLockoutAndTokenConsumption() {
        AuthenticationResult existingSession = login(CUSTOMER_EMAIL, OLD_PASSWORD);
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET failed_login_attempts = 4, locked_until = CURRENT_TIMESTAMP + INTERVAL '10 minutes'
                        WHERE id = ?
                        """,
                CUSTOMER_USER_ID
        );
        String token = issueToken();
        String originalHash = text("SELECT password_hash FROM users WHERE id = ?", CUSTOMER_USER_ID);

        jdbcTemplate.execute("""
                CREATE FUNCTION reject_password_reset_refresh_revoke()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF NEW.user_id = '00000000-0000-0000-0000-000000000301' AND NEW.revoked_at IS NOT NULL THEN
                        RAISE EXCEPTION 'forced refresh revocation failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_reject_password_reset_refresh_revoke
                BEFORE UPDATE ON refresh_token_sessions
                FOR EACH ROW
                EXECUTE FUNCTION reject_password_reset_refresh_revoke()
                """);
        try {
            assertThrows(
                    RuntimeException.class,
                    () -> confirmPasswordResetUseCase.confirmReset(
                            new PasswordResetConfirmationRequest(token, "rollback-password-value")
                    )
            );
        } finally {
            jdbcTemplate.execute("DROP TRIGGER trg_reject_password_reset_refresh_revoke ON refresh_token_sessions");
            jdbcTemplate.execute("DROP FUNCTION reject_password_reset_refresh_revoke()");
        }

        assertEquals(originalHash, text("SELECT password_hash FROM users WHERE id = ?", CUSTOMER_USER_ID));
        assertEquals(4, count("SELECT failed_login_attempts FROM users WHERE id = ?", CUSTOMER_USER_ID));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT locked_until FROM users WHERE id = ?", LocalDateTime.class, CUSTOMER_USER_ID
        ));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE token_digest = ? AND consumed_at IS NOT NULL
                """, tokenCodec.digest(token)));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE token_digest IS NOT NULL AND user_id = ? AND revoked_at IS NULL
                """, CUSTOMER_USER_ID));
        assertNotNull(existingSession.refreshToken());
    }

    private String issueToken() {
        requestPasswordResetUseCase.requestReset(new PasswordResetRequest(CUSTOMER_EMAIL));
        return deliveries.remove().rawToken();
    }

    private boolean requestResetConcurrently(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        requestPasswordResetUseCase.requestReset(new PasswordResetRequest(CUSTOMER_EMAIL));
        return true;
    }

    private boolean confirmConcurrently(
            String token,
            String password,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            confirmPasswordResetUseCase.confirmReset(new PasswordResetConfirmationRequest(token, password));
            return true;
        } catch (AuthenticationFailedException exception) {
            assertEquals("INVALID_PASSWORD_RESET_TOKEN", exception.getErrorCode());
            return false;
        }
    }

    private AuthenticationResult login(String email, String password) {
        return authenticationUseCase.login(new LoginRequest(email, password));
    }

    private void assertInvalidRefresh(String token) {
        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationUseCase.refresh(token)
        );
        assertEquals("INVALID_REFRESH_TOKEN", exception.getErrorCode());
    }

    private void assertInvalidToken(String rawToken) throws Exception {
        mockMvc.perform(resetConfirmation(rawToken, "replacement-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD_RESET_TOKEN"))
                .andExpect(jsonPath("$.message").value("Password reset token is invalid or expired."));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resetRequest(String email) {
        return post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resetConfirmation(
            String token,
            String newPassword
    ) {
        return post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "newPassword": "%s"}
                        """.formatted(token, newPassword));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email,
            String password
    ) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }

    private String text(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, String.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private record Delivery(String recipient, String rawToken) {
    }
}
