package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.out.RefreshTokenCodecPort;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_identity_refresh_v48_"
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
        jdbcTemplate.update("DELETE FROM refresh_token_sessions");
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", CUSTOMER_USER_ID);
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
        assertTrue(setCookie.contains("Path=/api/v1/auth/refresh"));
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
    void validRefreshRotatesCookieConsumesOriginalAndIssuesVerifiableAccessToken() throws Exception {
        AuthenticationResult login = authenticationUseCase.login(LOGIN);
        UUID originalSessionId = sessionId(login.refreshToken());

        MvcResult refresh = refresh(login.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String replacementRefreshToken = cookieValue(
                refresh.getResponse().getHeader(HttpHeaders.SET_COOKIE)
        );
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

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("MERIDIAN_REFRESH_TOKEN", refreshToken)));
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

    private String cookieValue(String setCookieHeader) {
        assertNotNull(setCookieHeader);
        String prefix = "MERIDIAN_REFRESH_TOKEN=";
        assertTrue(setCookieHeader.startsWith(prefix));
        int separator = setCookieHeader.indexOf(';');
        return setCookieHeader.substring(prefix.length(), separator);
    }
}
