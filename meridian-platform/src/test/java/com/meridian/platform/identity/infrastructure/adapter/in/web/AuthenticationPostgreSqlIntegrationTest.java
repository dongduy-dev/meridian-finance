package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.CurrentSessionLogoutCommand;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.LogoutUseCase;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
import com.meridian.platform.identity.infrastructure.adapter.out.persistence.AccessTokenRevocationRepositoryAdapter;
import com.meridian.platform.identity.infrastructure.security.JwtTokenService;
import com.meridian.platform.identity.infrastructure.security.ParsedAccessToken;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "meridian.identity.account-lockout.max-failed-attempts=3",
        "meridian.identity.account-lockout.lock-duration=15m"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_identity_session_v50_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ADMIN_CONFIG_PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000221");
    private static final LoginRequest LOGIN = new LoginRequest(
            "customer.demo@meridian.local",
            "Meridian@123"
    );

    @Autowired
    private AuthenticationUseCase authenticationUseCase;

    @Autowired
    private LogoutUseCase logoutUseCase;

    @Autowired
    private RefreshTokenCodecPort refreshTokenCodec;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @BeforeEach
    void resetIdentityState() {
        jdbcTemplate.update("DELETE FROM access_token_revocations");
        jdbcTemplate.update("DELETE FROM refresh_token_sessions");
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET status = 'ACTIVE', failed_login_attempts = 0, locked_until = NULL
                        """
        );
        jdbcTemplate.update(
                "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ?",
                CUSTOMER_ROLE_ID,
                ADMIN_CONFIG_PERMISSION_ID
        );
    }

    @Test
    void loginPersistsOnlyDigestAndReturnsRefreshTokenOnlyAsProtectedCookie(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer.demo@meridian.local",
                                  "password": "Meridian@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("Max-Age=604800"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertFalse(setCookie.contains("; Secure"));

        String rawRefreshToken = cookieValue(setCookie);
        String storedDigest = jdbcTemplate.queryForObject(
                "SELECT token_digest FROM refresh_token_sessions",
                String.class
        );
        assertEquals(refreshTokenCodec.digest(rawRefreshToken), storedDigest);
        assertNotEquals(rawRefreshToken, storedDigest);
        assertFalse(result.getResponse().getContentAsString().contains(rawRefreshToken));
        assertFalse(output.getAll().contains(rawRefreshToken));
    }

    @Test
    void failedPasswordStateCommitsAlthoughLoginReturnsSafeUnauthorizedResponse(CapturedOutput output)
            throws Exception {
        mockMvc.perform(loginRequest("wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid credentials."));

        assertEquals(1, failedLoginAttempts());
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE locked_until IS NOT NULL"));
        assertFalse(output.getAll().contains("wrong-password"));
        assertFalse(output.getAll().contains("customer.demo@meridian.local"));
    }

    @Test
    void v50ConstraintRejectsNegativeFailedAttemptState() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "UPDATE users SET failed_login_attempts = -1 WHERE id = ?",
                        CUSTOMER_USER_ID
                )
        );
        assertEquals(0, failedLoginAttempts());
    }

    @Test
    void thresholdLockIsDurableAndCorrectPasswordRemainsEnumerationSafe() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(loginRequest("wrong-password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        assertEquals(3, failedLoginAttempts());
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE locked_until > CURRENT_TIMESTAMP"));

        mockMvc.perform(loginRequest("Meridian@123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid credentials."));

        assertEquals(3, failedLoginAttempts());
    }

    @Test
    void expiredLockRestoresLoginWithoutSleepingAndSuccessfulLoginClearsState() throws Exception {
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET failed_login_attempts = 3,
                            locked_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                        WHERE id = ?
                        """,
                CUSTOMER_USER_ID
        );

        mockMvc.perform(loginRequest("Meridian@123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());

        assertEquals(0, failedLoginAttempts());
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE locked_until IS NOT NULL"));
    }

    @Test
    void successfulLoginClearsPersistedPreThresholdFailure() throws Exception {
        mockMvc.perform(loginRequest("wrong-password"))
                .andExpect(status().isUnauthorized());
        assertEquals(1, failedLoginAttempts());

        mockMvc.perform(loginRequest("Meridian@123"))
                .andExpect(status().isOk());

        assertEquals(0, failedLoginAttempts());
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE locked_until IS NOT NULL"));
    }

    @Test
    void concurrentFailedAttemptsCannotLoseIncrementsOrBypassThreshold() throws Exception {
        int contenders = 5;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(contenders)) {
            List<Future<Boolean>> futures = java.util.stream.IntStream.range(0, contenders)
                    .mapToObj(unused -> executor.submit(() -> attemptFailedLogin(ready, start)))
                    .toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            for (Future<Boolean> future : futures) {
                assertTrue(future.get(30, TimeUnit.SECONDS));
            }
        }

        assertEquals(3, failedLoginAttempts());
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE locked_until > CURRENT_TIMESTAMP"));
        mockMvc.perform(loginRequest("Meridian@123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void passwordLockoutDoesNotInvalidateExistingAccessOrRefreshSession() throws Exception {
        AuthenticationResult existingSession = authenticationUseCase.login(LOGIN);

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(loginRequest("wrong-password"))
                    .andExpect(status().isUnauthorized());
        }

        protectedCustomerProfile(existingSession.response().accessToken())
                .andExpect(status().isOk());
        AuthenticationResult refreshed = authenticationUseCase.refresh(existingSession.refreshToken());
        protectedCustomerProfile(refreshed.response().accessToken())
                .andExpect(status().isOk());
        assertEquals(0, count("SELECT COUNT(*) FROM access_token_revocations"));
    }

    @Test
    void validRefreshRotatesCookieConsumesOriginalAndIssuesVerifiableAccessToken() throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        UUID originalSessionId = sessionId(login.refreshToken());

        MvcResult refresh = refresh(login.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String replacementCookie = refresh.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(replacementCookie);
        assertTrue(replacementCookie.contains("Path=/api/v1/auth"));
        String replacementRefreshToken = cookieValue(replacementCookie);
        JsonNode response = objectMapper.readTree(refresh.getResponse().getContentAsString());

        assertNotEquals(login.refreshToken(), replacementRefreshToken);
        assertNotEquals(login.response().accessToken(), response.get("accessToken").asText());
        assertEquals(CUSTOMER_USER_ID, jwtTokenService.parseAccessToken(response.get("accessToken").asText()).userId());
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT consumed_at FROM refresh_token_sessions WHERE id = ?",
                LocalDateTime.class,
                originalSessionId
        ));
        assertEquals(2, count("SELECT COUNT(*) FROM refresh_token_sessions"));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE consumed_at IS NULL AND revoked_at IS NULL
                """));
    }

    @Test
    void consumedTokenReplayRevokesReplacementFamilyAndRevocationCommitsWithFailure() throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        UUID familyId = familyId(login.refreshToken());
        refresh(login.refreshToken()).andExpect(status().isOk());

        MvcResult replay = refresh(login.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("Refresh authentication failed."))
                .andReturn();

        assertEquals(0, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE family_id = '%s' AND revoked_at IS NULL
                """.formatted(familyId)));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE family_id = '%s' AND revoked_at IS NOT NULL
                """.formatted(familyId)));
        assertFalse(replay.getResponse().getContentAsString().contains(familyId.toString()));
        assertFalse(replay.getResponse().getContentAsString().contains(login.refreshToken()));
    }

    @Test
    void concurrentUseCannotProduceTwoSuccessfulRotations() throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        UUID familyId = familyId(login.refreshToken());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures = List.of(
                    executor.submit(() -> attemptRefresh(login.refreshToken(), ready, start)),
                    executor.submit(() -> attemptRefresh(login.refreshToken(), ready, start))
            );
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(20, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        }

        assertEquals(2, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE family_id = '%s'
                """.formatted(familyId)));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE family_id = '%s' AND consumed_at IS NULL AND revoked_at IS NULL
                """.formatted(familyId)));
    }

    @Test
    void expiredRevokedAndUnknownTokensFailWithOneSafeError() {
        AuthenticationResult expired = authenticationUseCase.login(LOGIN);
        jdbcTemplate.update(
                """
                        UPDATE refresh_token_sessions
                        SET issued_at = CURRENT_TIMESTAMP - INTERVAL '8 days',
                            expires_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                        WHERE token_digest = ?
                        """,
                refreshTokenCodec.digest(expired.refreshToken())
        );
        assertInvalidRefresh(expired.refreshToken());

        AuthenticationResult revoked = authenticationUseCase.login(LOGIN);
        jdbcTemplate.update(
                "UPDATE refresh_token_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_digest = ?",
                refreshTokenCodec.digest(revoked.refreshToken())
        );
        assertInvalidRefresh(revoked.refreshToken());
        assertInvalidRefresh("unknown-refresh-token");
    }

    @Test
    void suspendedAndDisabledUsersCannotRefreshAndTheirFamilyIsRevoked() {
        for (String status : List.of("SUSPENDED", "DISABLED")) {
            AuthenticationResult login = authenticationUseCase.login(LOGIN);
            UUID familyId = familyId(login.refreshToken());
            jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, CUSTOMER_USER_ID);

            assertInvalidRefresh(login.refreshToken());
            assertEquals(0, count("""
                    SELECT COUNT(*) FROM refresh_token_sessions
                    WHERE family_id = '%s' AND revoked_at IS NULL
                    """.formatted(familyId)));

            jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", CUSTOMER_USER_ID);
        }
    }

    @Test
    void refreshReloadsCurrentRolesAndPermissions() {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        jdbcTemplate.update(
                "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)",
                CUSTOMER_ROLE_ID,
                ADMIN_CONFIG_PERMISSION_ID
        );

        AuthenticationResult refreshed = authenticationUseCase.refresh(login.refreshToken());

        assertTrue(refreshed.response().permissions().contains("admin:config"));
        assertTrue(jwtTokenService.parseAccessToken(refreshed.response().accessToken())
                .permissions().contains("admin:config"));
    }

    @Test
    void logoutRevokesTheKnownRefreshFamilyAndClearsTheCookie() throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        UUID familyId = familyId(login.refreshToken());
        MvcResult rotation = refresh(login.refreshToken())
                .andExpect(status().isOk())
                .andReturn();
        String replacementRefreshToken = cookieValue(
                rotation.getResponse().getHeader(HttpHeaders.SET_COOKIE)
        );

        MvcResult logout = logout(login.refreshToken(), null)
                .andExpect(status().isNoContent())
                .andReturn();

        assertClearedCookie(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE family_id = '%s' AND revoked_at IS NULL
                """.formatted(familyId)));
        refresh(replacementRefreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutPersistsOnlyAccessJtiAndImmediatelyRejectsThatBearer(CapturedOutput output) throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        String accessToken = login.response().accessToken();
        ParsedAccessToken parsed = jwtTokenService.parseAccessTokenDetails(accessToken);

        protectedCustomerProfile(accessToken).andExpect(status().isOk());
        MvcResult logout = logout(null, accessToken)
                .andExpect(status().isNoContent())
                .andReturn();

        assertEquals(parsed.tokenId(), jdbcTemplate.queryForObject(
                "SELECT token_id FROM access_token_revocations",
                UUID.class
        ));
        assertEquals(1, count("SELECT COUNT(*) FROM access_token_revocations"));
        assertEquals(
                List.of("expires_at", "revoked_at", "token_id"),
                jdbcTemplate.queryForList(
                        """
                                SELECT column_name
                                FROM information_schema.columns
                                WHERE table_schema = ?
                                  AND table_name = 'access_token_revocations'
                                ORDER BY column_name
                                """,
                        String.class,
                        TEST_SCHEMA
                )
        );
        assertFalse(logout.getResponse().getContentAsString().contains(accessToken));
        assertFalse(output.getAll().contains(accessToken));

        protectedCustomerProfile(accessToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(result -> assertFalse(result.getResponse()
                        .getContentAsString().contains(parsed.tokenId().toString())));

        String otherAccessToken = authenticationUseCase.login(LOGIN).response().accessToken();
        protectedCustomerProfile(otherAccessToken).andExpect(status().isOk());
    }

    @Test
    void repeatedMissingAndUnknownCredentialLogoutIsIdempotent() throws Exception {
        logout(null, null)
                .andExpect(status().isNoContent())
                .andExpect(result -> assertClearedCookie(
                        result.getResponse().getHeader(HttpHeaders.SET_COOKIE)
                ));
        logout("unknown-refresh-token", "malformed-access-token")
                .andExpect(status().isNoContent());

        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        logout(login.refreshToken(), login.response().accessToken())
                .andExpect(status().isNoContent());
        logout(login.refreshToken(), login.response().accessToken())
                .andExpect(status().isNoContent());

        assertEquals(1, count("SELECT COUNT(*) FROM access_token_revocations"));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE consumed_at IS NULL AND revoked_at IS NULL
                """));
    }

    @Test
    void malformedBearerDoesNotPreventRefreshFamilyLogout() throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);

        logout(login.refreshToken(), "malformed-access-token")
                .andExpect(status().isNoContent());

        refresh(login.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
        assertEquals(0, count("SELECT COUNT(*) FROM access_token_revocations"));
    }

    @Test
    void logoutKeepsIndependentLoginFamiliesForTheSameUserUsable() throws Exception {
        AuthenticationResult sessionA = authenticationUseCase.login(LOGIN);
        AuthenticationResult sessionB = authenticationUseCase.login(LOGIN);
        UUID familyA = familyId(sessionA.refreshToken());
        UUID familyB = familyId(sessionB.refreshToken());

        logout(sessionA.refreshToken(), sessionA.response().accessToken())
                .andExpect(status().isNoContent());

        assertEquals(0, activeFamilySessions(familyA));
        assertEquals(1, activeFamilySessions(familyB));
        refresh(sessionA.refreshToken()).andExpect(status().isUnauthorized());
        refresh(sessionB.refreshToken()).andExpect(status().isOk());
        protectedCustomerProfile(sessionA.response().accessToken()).andExpect(status().isUnauthorized());
        protectedCustomerProfile(sessionB.response().accessToken()).andExpect(status().isOk());
    }

    @Test
    void logoutAndRefreshRaceCannotLeaveAUsableReplacement() throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        UUID familyId = familyId(login.refreshToken());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AuthenticationResult> refresh = executor.submit(() ->
                    attemptRefreshResult(login.refreshToken(), ready, start));
            Future<Void> logout = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                logoutUseCase.logout(new CurrentSessionLogoutCommand(
                        java.util.Optional.of(login.refreshToken()),
                        java.util.Optional.empty()
                ));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            AuthenticationResult replacement = refresh.get(20, TimeUnit.SECONDS);
            logout.get(20, TimeUnit.SECONDS);

            if (replacement != null) {
                assertInvalidRefresh(replacement.refreshToken());
            }
        }

        assertEquals(0, activeFamilySessions(familyId));
    }

    @Test
    void accessRevocationRemainsDurableAcrossRepositoryRecreationAndExpiresFromChecks() {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        ParsedAccessToken parsed = jwtTokenService.parseAccessTokenDetails(login.response().accessToken());
        logoutUseCase.logout(new CurrentSessionLogoutCommand(
                java.util.Optional.empty(),
                java.util.Optional.of(new com.meridian.platform.identity.application.dto.AccessTokenReference(
                        parsed.tokenId(),
                        parsed.expiresAt()
                ))
        ));

        AccessTokenRevocationRepositoryAdapter recreated =
                new AccessTokenRevocationRepositoryAdapter(jdbcTemplate);
        assertTrue(recreated.isRevoked(parsed.tokenId()));

        UUID expiredTokenId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO access_token_revocations (token_id, revoked_at, expires_at)
                        VALUES (?, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour')
                        """,
                expiredTokenId
        );
        assertFalse(recreated.isRevoked(expiredTokenId));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("MERIDIAN_REFRESH_TOKEN", refreshToken)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "customer.demo@meridian.local",
                          "password": "%s"
                        }
                        """.formatted(password));
    }

    private boolean attemptFailedLogin(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            authenticationUseCase.login(new LoginRequest(
                    "customer.demo@meridian.local",
                    "wrong-password"
            ));
            return false;
        } catch (AuthenticationFailedException exception) {
            assertEquals("INVALID_CREDENTIALS", exception.getErrorCode());
            return true;
        }
    }

    private org.springframework.test.web.servlet.ResultActions logout(
            String refreshToken,
            String accessToken
    ) throws Exception {
        var request = post("/api/v1/auth/logout");
        if (refreshToken != null) {
            request.cookie(new Cookie("MERIDIAN_REFRESH_TOKEN", refreshToken));
        }
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions protectedCustomerProfile(
            String accessToken
    ) throws Exception {
        return mockMvc.perform(get("/api/v1/customers/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private boolean attemptRefresh(String refreshToken, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            authenticationUseCase.refresh(refreshToken);
            return true;
        } catch (AuthenticationFailedException exception) {
            assertEquals("INVALID_REFRESH_TOKEN", exception.getErrorCode());
            return false;
        }
    }

    private AuthenticationResult attemptRefreshResult(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            return authenticationUseCase.refresh(refreshToken);
        } catch (AuthenticationFailedException exception) {
            assertEquals("INVALID_REFRESH_TOKEN", exception.getErrorCode());
            return null;
        }
    }

    private void assertInvalidRefresh(String token) {
        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationUseCase.refresh(token)
        );
        assertEquals("INVALID_REFRESH_TOKEN", exception.getErrorCode());
        assertEquals("Refresh authentication failed.", exception.getMessage());
    }

    private UUID sessionId(String rawRefreshToken) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM refresh_token_sessions WHERE token_digest = ?",
                UUID.class,
                refreshTokenCodec.digest(rawRefreshToken)
        );
    }

    private UUID familyId(String rawRefreshToken) {
        return jdbcTemplate.queryForObject(
                "SELECT family_id FROM refresh_token_sessions WHERE token_digest = ?",
                UUID.class,
                refreshTokenCodec.digest(rawRefreshToken)
        );
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private int failedLoginAttempts() {
        return jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE id = ?",
                Integer.class,
                CUSTOMER_USER_ID
        );
    }

    private int activeFamilySessions(UUID familyId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM refresh_token_sessions
                        WHERE family_id = ?
                          AND consumed_at IS NULL
                          AND revoked_at IS NULL
                        """,
                Integer.class,
                familyId
        );
    }

    private void assertClearedCookie(String setCookieHeader) {
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.startsWith("MERIDIAN_REFRESH_TOKEN="));
        assertTrue(setCookieHeader.contains("Path=/api/v1/auth"));
        assertTrue(setCookieHeader.contains("Max-Age=0"));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("SameSite=Strict"));
        assertFalse(setCookieHeader.contains("; Secure"));
    }

    private String cookieValue(String setCookieHeader) {
        assertNotNull(setCookieHeader);
        String prefix = "MERIDIAN_REFRESH_TOKEN=";
        assertTrue(setCookieHeader.startsWith(prefix));
        int separator = setCookieHeader.indexOf(';');
        return setCookieHeader.substring(prefix.length(), separator);
    }
}
