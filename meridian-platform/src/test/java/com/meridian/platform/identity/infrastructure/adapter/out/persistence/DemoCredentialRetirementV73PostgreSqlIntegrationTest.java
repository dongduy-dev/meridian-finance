package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.dto.PasswordResetConfirmationRequest;
import com.meridian.platform.identity.application.dto.EmailVerificationConfirmationRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.target=72",
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@AutoConfigureMockMvc
class DemoCredentialRetirementV73PostgreSqlIntegrationTest {
    private static final String SCHEMA = "identity_v73_" + UUID.randomUUID().toString().replace("-", "");
    private static final String KNOWN_PASSWORD = "Meridian@123";
    private static final String IDS = "('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000302', "
            + "'00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000305')";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthenticationUseCase authentication;
    @Autowired ConfirmPasswordResetUseCase passwordReset;
    @Autowired ConfirmEmailVerificationUseCase emailVerification;
    @Autowired PasswordResetTokenCodecPort resetCodec;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void upgradeRetiresCredentialsAndRejectsPreviouslyIssuedAuthorityEvenAfterReactivation() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM users WHERE id IN " + IDS + " ORDER BY id");
        var assignments = jdbc.queryForList("SELECT * FROM role_assignments ORDER BY id");
        var sessions = new ArrayList<com.meridian.platform.identity.application.dto.AuthenticationResult>();
        var resetTokens = new ArrayList<String>();
        var verificationTokens = new ArrayList<String>();
        for (var row : before) {
            UUID id = (UUID) row.get("id");
            var initial = authentication.login(new LoginRequest((String) row.get("email"), KNOWN_PASSWORD));
            sessions.add(authentication.refresh(initial.refreshToken()));
            var reset = resetCodec.generate();
            resetTokens.add(reset.tokenValue());
            jdbc.update("INSERT INTO password_reset_tokens (id,user_id,token_digest,issued_at,expires_at) "
                    + "VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '1 day')",
                    UUID.randomUUID(), id, reset.tokenDigest());
            var verification = resetCodec.generate();
            verificationTokens.add(verification.tokenValue());
            jdbc.update("INSERT INTO email_verification_tokens (id,user_id,token_digest,issued_at,expires_at) "
                    + "VALUES (?,?,?,CURRENT_TIMESTAMP + INTERVAL '1 day',CURRENT_TIMESTAMP + INTERVAL '2 days')",
                    UUID.randomUUID(), id, verification.tokenDigest());
            jdbc.update("UPDATE users SET failed_login_attempts=2, locked_until=CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE id=?", id);
        }
        String adminJwt = sessions.getLast().response().accessToken();
        mvc.perform(get("/api/v1/admin/internal-users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt))
                .andExpect(status().isOk());
        UUID auditId = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_events (id,operation_id,sequence_number,actor_type,actor_user_id,entity_type,entity_id,action,occurred_at) "
                + "VALUES (?,?,1,'USER',?,'IDENTITY_USER',?,'IDENTITY_USER_CREATED',CURRENT_TIMESTAMP)",
                auditId, UUID.randomUUID(), before.getLast().get("id"), before.getFirst().get("id"));
        var auditBefore = jdbc.queryForMap("SELECT * FROM audit_events WHERE id=?", auditId);
        UUID ordinary = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,email,normalized_email,password_hash,user_type,status,display_name) "
                + "VALUES (?,?,?,?,'STAFF','ACTIVE','Existing Staff')", ordinary, "ordinary@meridian.test", "ordinary@meridian.test",
                encoder.encode("Ordinary-Private-Test-Password"));
        var ordinaryBefore = jdbc.queryForMap("SELECT * FROM users WHERE id=?", ordinary);
        UUID consumedResetId = UUID.randomUUID();
        jdbc.update("INSERT INTO password_reset_tokens (id,user_id,token_digest,issued_at,expires_at,consumed_at) "
                + "VALUES (?,?,?,CURRENT_TIMESTAMP - INTERVAL '1 day',CURRENT_TIMESTAMP + INTERVAL '1 day',CURRENT_TIMESTAMP)",
                consumedResetId, before.getLast().get("id"), resetCodec.generate().tokenDigest());
        var consumedBefore = jdbc.queryForMap("SELECT * FROM password_reset_tokens WHERE id=?", consumedResetId);

        assertEquals(1, migrate(SCHEMA, "73"));
        assertEquals(assignments, jdbc.queryForList("SELECT * FROM role_assignments ORDER BY id"));
        assertEquals(auditBefore, jdbc.queryForMap("SELECT * FROM audit_events WHERE id=?", auditId));
        assertEquals(ordinaryBefore, jdbc.queryForMap("SELECT * FROM users WHERE id=?", ordinary));
        assertEquals(consumedBefore, jdbc.queryForMap("SELECT * FROM password_reset_tokens WHERE id=?", consumedResetId));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id IN " + IDS, Integer.class));
        for (var row : before) {
            var after = jdbc.queryForMap("SELECT * FROM users WHERE id=?", row.get("id"));
            assertEquals("DISABLED", after.get("status"));
            assertEquals(((Number) row.get("authorization_version")).longValue() + 1,
                    ((Number) after.get("authorization_version")).longValue());
            assertNotEquals(row.get("password_hash"), after.get("password_hash"));
            assertFalse(encoder.matches(KNOWN_PASSWORD, (String) after.get("password_hash")));
            assertEquals(0, ((Number) after.get("failed_login_attempts")).intValue());
            assertNull(after.get("locked_until"));
            assertEquals(row.get("customer_id"), after.get("customer_id"));
            assertThrows(AuthenticationFailedException.class,
                    () -> authentication.login(new LoginRequest((String) row.get("email"), KNOWN_PASSWORD)));
        }
        for (String table : new String[]{"refresh_token_sessions", "password_reset_tokens", "email_verification_tokens"}) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                    + " WHERE consumed_at IS NULL AND revoked_at IS NULL AND user_id IN " + IDS, Integer.class));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token_sessions WHERE revoked_at IS NULL AND user_id IN " + IDS, Integer.class));
        mvc.perform(get("/api/v1/admin/internal-users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        // Reactivation alone must not restore a credential or any old authority.
        jdbc.update("UPDATE users SET status='ACTIVE', failed_login_attempts=0, locked_until=NULL WHERE id IN " + IDS);
        for (int i = 0; i < before.size(); i++) {
            var session = sessions.get(i);
            String rawReset = resetTokens.get(i);
            String rawVerification = verificationTokens.get(i);
            String email = (String) before.get(i).get("email");
            assertThrows(AuthenticationFailedException.class, () -> authentication.login(new LoginRequest(email, KNOWN_PASSWORD)));
            assertThrows(AuthenticationFailedException.class, () -> authentication.refresh(session.refreshToken()));
            assertThrows(AuthenticationFailedException.class,
                    () -> passwordReset.confirmReset(new PasswordResetConfirmationRequest(rawReset, "New-Test-Password-73")));
            assertThrows(AuthenticationFailedException.class,
                    () -> emailVerification.confirmVerification(new EmailVerificationConfirmationRequest(rawVerification)));
            mvc.perform(get("/api/v1/admin/internal-users").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.response().accessToken()))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        }
        var after = jdbc.queryForList("SELECT id,password_hash,authorization_version FROM users WHERE id IN " + IDS + " ORDER BY id");
        assertEquals(0, migrate(SCHEMA, "73"));
        assertEquals(after, jdbc.queryForList("SELECT id,password_hash,authorization_version FROM users WHERE id IN " + IDS + " ORDER BY id"));
    }

    @Test
    void cleanMigrationPreservesActorsRolesAndCustomerMappingWithoutUsableKnownPasswords() {
        String schema = "identity_v73_clean_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrate(schema, "73");
            var rows = jdbc.queryForList("SELECT id,status,password_hash,authorization_version FROM " + schema + ".users WHERE id IN " + IDS);
            assertEquals(5, rows.size());
            for (var row : rows) {
                assertEquals("DISABLED", row.get("status"));
                assertEquals(1L, ((Number) row.get("authorization_version")).longValue());
                assertFalse(encoder.matches(KNOWN_PASSWORD, (String) row.get("password_hash")));
            }
            assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".role_assignments WHERE user_id IN " + IDS, Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".users u JOIN " + schema
                    + ".customers c ON c.id=u.customer_id WHERE u.id='00000000-0000-0000-0000-000000000301'", Integer.class));
            assertEquals(0, migrate(schema, "73"));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private int migrate(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }
}
