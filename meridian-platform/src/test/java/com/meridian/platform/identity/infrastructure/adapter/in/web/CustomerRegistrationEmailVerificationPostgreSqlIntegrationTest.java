package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.CustomerRegistrationRequest;
import com.meridian.platform.identity.application.dto.EmailVerificationConfirmationRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.meridian.platform.identity.application.port.in.RegisterCustomerUseCase;
import com.meridian.platform.identity.application.port.out.EmailVerificationNotificationPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenCodecPort;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "meridian.identity.rate-limit.login.max-requests=1000",
        "meridian.identity.rate-limit.refresh.max-requests=1000",
        "meridian.identity.rate-limit.registration.max-requests=1000",
        "meridian.identity.rate-limit.email-verification-request.max-requests=1000"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CustomerRegistrationEmailVerificationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_reg_v51_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RegisterCustomerUseCase registerCustomerUseCase;

    @Autowired
    private ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    @Autowired
    private AuthenticationUseCase authenticationUseCase;

    @Autowired
    private EmailVerificationTokenCodecPort verificationTokenCodec;

    @Autowired
    private RefreshTokenCodecPort refreshTokenCodec;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailVerificationNotificationPort notificationPort;

    private final ConcurrentLinkedQueue<String> deliveredTokens = new ConcurrentLinkedQueue<>();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @BeforeEach
    void captureVerificationDeliveries() {
        reset(notificationPort);
        deliveredTokens.clear();
        doAnswer(invocation -> {
            deliveredTokens.add(invocation.getArgument(1, String.class));
            return null;
        }).when(notificationPort).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void registrationPersistsOneCustomerUserRoleAndDigestWithoutRawSecrets(CapturedOutput output) throws Exception {
        String email = uniqueEmail("valid");
        String password = "a-valid-password";

        mockMvc.perform(registrationRequest("  " + email.toUpperCase() + "  ", password))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        UUID userId = userId(email);
        UUID customerId = jdbcTemplate.queryForObject(
                "SELECT customer_id FROM users WHERE id = ?",
                UUID.class,
                userId
        );
        assertNotNull(customerId);
        assertEquals("ACTIVE", text("SELECT status FROM customers WHERE id = ?", customerId));
        assertEquals("UNVERIFIED", text("SELECT verification_status FROM customers WHERE id = ?", customerId));
        assertEquals("INCOMPLETE", text("SELECT profile_completion_status FROM customers WHERE id = ?", customerId));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_profiles WHERE customer_id = ?", customerId));
        assertEquals(0, count("SELECT COUNT(*) FROM customer_bank_accounts WHERE customer_id = ?", customerId));
        assertEquals("CUSTOMER", text("SELECT user_type FROM users WHERE id = ?", userId));
        assertEquals("ACTIVE", text("SELECT status FROM users WHERE id = ?", userId));
        assertEquals(0, count("SELECT failed_login_attempts FROM users WHERE id = ?", userId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT locked_until FROM users WHERE id = ?",
                LocalDateTime.class,
                userId
        ));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE id = ?",
                LocalDateTime.class,
                userId
        ));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM role_assignments assignment
                JOIN roles role ON role.id = assignment.role_id
                WHERE assignment.user_id = ? AND role.code = 'CUSTOMER'
                """, userId));

        String storedHash = text("SELECT password_hash FROM users WHERE id = ?", userId);
        assertNotEquals(password, storedHash);
        assertTrue(passwordEncoder.matches(password, storedHash));

        String rawToken = deliveredTokens.remove();
        String storedDigest = text(
                "SELECT token_digest FROM email_verification_tokens WHERE user_id = ?",
                userId
        );
        assertEquals(verificationTokenCodec.digest(rawToken), storedDigest);
        assertNotEquals(rawToken, storedDigest);
        assertEquals(0, count(
                "SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ?",
                "%" + rawToken + "%"
        ));
        assertFalse(output.getAll().contains(rawToken));
        assertFalse(output.getAll().contains(password));
    }

    @Test
    void duplicateNormalizedEmailReturnsSafeConflictWithoutAnOrphanCustomer() throws Exception {
        String email = uniqueEmail("duplicate");
        mockMvc.perform(registrationRequest(email, "first-password-value"))
                .andExpect(status().isCreated());
        int customerCount = count("SELECT COUNT(*) FROM customers");

        mockMvc.perform(registrationRequest("  " + email.toUpperCase() + " ", "second-password-val"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists."))
                .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(userId(email).toString())));

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE normalized_email = ?", email));
        assertEquals(customerCount, count("SELECT COUNT(*) FROM customers"));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM email_verification_tokens token
                JOIN users user_account ON user_account.id = token.user_id
                WHERE user_account.normalized_email = ?
                """, email));
    }

    @Test
    void notificationFailureAfterCommitReturnsSuccessAndResendRecovers() throws Exception {
        String email = uniqueEmail("delivery-failure");
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(notificationPort)
                .sendVerificationEmail(anyString(), anyString());

        mockMvc.perform(registrationRequest(email, "delivery-failure-pass"))
                .andExpect(status().isCreated());

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE normalized_email = ?", email));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM email_verification_tokens token
                JOIN users user_account ON user_account.id = token.user_id
                WHERE user_account.normalized_email = ?
                """, email));

        captureVerificationDeliveries();
        mockMvc.perform(verificationRequest(email))
                .andExpect(status().isAccepted());
        assertEquals(1, deliveredTokens.size());
    }

    @Test
    void resendIsEnumerationSafeAndReplacesOnlyAnUnverifiedCustomerToken() throws Exception {
        mockMvc.perform(verificationRequest(uniqueEmail("unknown")))
                .andExpect(status().isAccepted());
        mockMvc.perform(verificationRequest("loan.officer@meridian.local"))
                .andExpect(status().isAccepted());
        mockMvc.perform(verificationRequest("customer.demo@meridian.local"))
                .andExpect(status().isAccepted());
        assertTrue(deliveredTokens.isEmpty());

        String email = uniqueEmail("resend");
        mockMvc.perform(registrationRequest(email, "resend-password-value"))
                .andExpect(status().isCreated());
        String firstToken = deliveredTokens.remove();

        mockMvc.perform(verificationRequest(email))
                .andExpect(status().isAccepted());
        String replacementToken = deliveredTokens.remove();

        assertNotEquals(firstToken, replacementToken);
        assertEquals(1, count("""
                SELECT COUNT(*) FROM email_verification_tokens token
                JOIN users user_account ON user_account.id = token.user_id
                WHERE user_account.normalized_email = ? AND token.revoked_at IS NOT NULL
                """, email));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM email_verification_tokens token
                JOIN users user_account ON user_account.id = token.user_id
                WHERE user_account.normalized_email = ?
                  AND token.consumed_at IS NULL AND token.revoked_at IS NULL
                """, email));
    }

    @Test
    void confirmationIsAtomicIdempotentAndDoesNotChangeCustomerVerificationStatus() throws Exception {
        String email = uniqueEmail("confirm");
        mockMvc.perform(registrationRequest(email, "confirmation-password"))
                .andExpect(status().isCreated());
        String rawToken = deliveredTokens.remove();
        UUID userId = userId(email);
        UUID customerId = jdbcTemplate.queryForObject(
                "SELECT customer_id FROM users WHERE id = ?", UUID.class, userId
        );

        mockMvc.perform(confirmationRequest(rawToken)).andExpect(status().isNoContent());
        mockMvc.perform(confirmationRequest(rawToken)).andExpect(status().isNoContent());

        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE id = ?", LocalDateTime.class, userId
        ));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT consumed_at FROM email_verification_tokens WHERE user_id = ?",
                LocalDateTime.class,
                userId
        ));
        assertEquals("UNVERIFIED", text("SELECT verification_status FROM customers WHERE id = ?", customerId));
    }

    @Test
    void unknownExpiredAndRevokedTokensShareOneSafeError() throws Exception {
        assertInvalidToken("unknown-token");

        String expiredEmail = uniqueEmail("expired");
        mockMvc.perform(registrationRequest(expiredEmail, "expired-password-val"))
                .andExpect(status().isCreated());
        String expiredToken = deliveredTokens.remove();
        jdbcTemplate.update(
                """
                        UPDATE email_verification_tokens
                        SET issued_at = CURRENT_TIMESTAMP - INTERVAL '2 days',
                            expires_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                        WHERE token_digest = ?
                        """,
                verificationTokenCodec.digest(expiredToken)
        );
        assertInvalidToken(expiredToken);

        String revokedEmail = uniqueEmail("revoked");
        mockMvc.perform(registrationRequest(revokedEmail, "revoked-password-val"))
                .andExpect(status().isCreated());
        String revokedToken = deliveredTokens.remove();
        mockMvc.perform(verificationRequest(revokedEmail)).andExpect(status().isAccepted());
        deliveredTokens.clear();
        assertInvalidToken(revokedToken);
    }

    @Test
    void loginRequiresVerificationThenCreatesCredentialsAfterConfirmation() throws Exception {
        String email = uniqueEmail("login");
        String password = "login-password-value";
        mockMvc.perform(registrationRequest(email, password)).andExpect(status().isCreated());
        String rawToken = deliveredTokens.remove();
        UUID userId = userId(email);

        mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFICATION_REQUIRED"));
        assertEquals(0, count("SELECT COUNT(*) FROM refresh_token_sessions WHERE user_id = ?", userId));

        mockMvc.perform(confirmationRequest(rawToken)).andExpect(status().isNoContent());
        mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        assertEquals(1, count("SELECT COUNT(*) FROM refresh_token_sessions WHERE user_id = ?", userId));
    }

    @Test
    void refreshRejectsAndRevokesAnInconsistentUnverifiedSession() {
        String email = uniqueEmail("refresh");
        registerCustomerUseCase.register(new CustomerRegistrationRequest(
                email,
                "refresh-password-val",
                "Refresh Customer"
        ));
        UUID userId = userId(email);
        String rawRefreshToken = "manually-created-refresh-token";
        UUID familyId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO refresh_token_sessions (
                            id, user_id, family_id, token_digest, issued_at, expires_at
                        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')
                        """,
                UUID.randomUUID(),
                userId,
                familyId,
                refreshTokenCodec.digest(rawRefreshToken)
        );

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationUseCase.refresh(rawRefreshToken)
        );

        assertEquals("INVALID_REFRESH_TOKEN", exception.getErrorCode());
        assertEquals(0, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE family_id = ? AND revoked_at IS NULL
                """, familyId));
    }

    @Test
    void concurrentSameEmailRegistrationCreatesAtMostOneCustomerUserPair() throws Exception {
        String email = uniqueEmail("concurrent-register");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> attemptRegistration(email, ready, start)),
                    executor.submit(() -> attemptRegistration(email, ready, start))
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

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE normalized_email = ?", email));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM customers customer_record
                JOIN users user_account ON user_account.customer_id = customer_record.id
                WHERE user_account.normalized_email = ?
                """, email));
    }

    @Test
    void concurrentConfirmationSerializesAndRemainsIdempotent() throws Exception {
        String email = uniqueEmail("concurrent-confirm");
        registerCustomerUseCase.register(new CustomerRegistrationRequest(
                email,
                "concurrent-password",
                "Concurrent Customer"
        ));
        String rawToken = deliveredTokens.remove();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> attemptConfirmation(rawToken, ready, start)),
                    executor.submit(() -> attemptConfirmation(rawToken, ready, start))
            );
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Boolean> attempt : attempts) {
                assertTrue(attempt.get(30, TimeUnit.SECONDS));
            }
        }

        assertEquals(1, count("""
                SELECT COUNT(*) FROM email_verification_tokens token
                JOIN users user_account ON user_account.id = token.user_id
                WHERE user_account.normalized_email = ? AND token.consumed_at IS NOT NULL
                """, email));
    }

    @Test
    void failureAfterCustomerAndUserWritesRollsBackTheWholeRegistration() {
        String email = uniqueEmail("rollback");
        int customersBefore = count("SELECT COUNT(*) FROM customers");
        jdbcTemplate.update("UPDATE roles SET code = 'CUSTOMER_UNAVAILABLE' WHERE code = 'CUSTOMER'");
        try {
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> registerCustomerUseCase.register(new CustomerRegistrationRequest(
                            email,
                            "rollback-password-val",
                            "Rollback Customer"
                    ))
            );
            assertTrue(hasCauseMessage(exception, "CUSTOMER role is not configured."));
        } finally {
            jdbcTemplate.update("UPDATE roles SET code = 'CUSTOMER' WHERE code = 'CUSTOMER_UNAVAILABLE'");
        }

        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE normalized_email = ?", email));
        assertEquals(customersBefore, count("SELECT COUNT(*) FROM customers"));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM email_verification_tokens token
                JOIN users user_account ON user_account.id = token.user_id
                WHERE user_account.normalized_email = ?
                """, email));
    }

    @Test
    void v51ConstraintsRejectInvalidDurableTokenState() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                        INSERT INTO email_verification_tokens (
                            id, user_id, token_digest, issued_at, expires_at
                        ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                UUID.randomUUID(),
                userId,
                "a".repeat(64)
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                        INSERT INTO email_verification_tokens (
                            id, user_id, token_digest, issued_at, expires_at
                        ) VALUES (?, ?, 'not-a-digest', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')
                        """,
                UUID.randomUUID(),
                userId
        ));
    }

    private boolean attemptRegistration(String email, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            registerCustomerUseCase.register(new CustomerRegistrationRequest(
                    email,
                    "concurrent-password",
                    "Concurrent Customer"
            ));
            return true;
        } catch (BusinessStateConflictException exception) {
            assertEquals("EMAIL_ALREADY_REGISTERED", exception.getErrorCode());
            return false;
        }
    }

    private boolean attemptConfirmation(String token, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        confirmEmailVerificationUseCase.confirmVerification(new EmailVerificationConfirmationRequest(token));
        return true;
    }

    private void assertInvalidToken(String rawToken) throws Exception {
        mockMvc.perform(confirmationRequest(rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_EMAIL_VERIFICATION_TOKEN"))
                .andExpect(jsonPath("$.message").value("Email verification token is invalid or expired."));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registrationRequest(
            String email,
            String password
    ) {
        return post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "displayName": "Checkpoint Customer"
                        }
                        """.formatted(email, password));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verificationRequest(String email) {
        return post("/api/v1/auth/email-verification/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder confirmationRequest(String token) {
        return post("/api/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}
                        """.formatted(token));
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

    private UUID userId(String normalizedEmail) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = ?",
                UUID.class,
                normalizedEmail
        );
    }

    private String text(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, String.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private String uniqueEmail(String purpose) {
        return "checkpoint." + purpose + "." + UUID.randomUUID() + "@example.com";
    }

    private boolean hasCauseMessage(Throwable throwable, String expectedMessage) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedMessage.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
