package com.meridian.platform.identity.infrastructure.adapter.in.bootstrap;

import com.meridian.platform.identity.application.dto.CreateInternalUserRequest;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.dto.PasswordResetConfirmationRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.BootstrapAdministratorUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.identity.application.port.out.StaffPasswordSetupNotificationPort;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdministratorBootstrapPostgreSqlIntegrationTest {
    private static final String SCHEMA = "admin_bootstrap_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired BootstrapAdministratorUseCase bootstrap;
    @Autowired AuthenticationUseCase authentication;
    @Autowired ConfirmPasswordResetUseCase confirmation;
    @Autowired PasswordResetTokenCodecPort codec;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean StaffPasswordSetupNotificationPort notification;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void concurrentBootstrapCreatesOneOrdinaryAdminAndNormalProvisioningCompletesStaffSetup(CapturedOutput output) throws Exception {
        var deliveries = new ConcurrentLinkedQueue<String>();
        doAnswer(call -> { deliveries.add(call.getArgument(1, String.class)); return null; })
                .when(notification).sendSetupEmail(anyString(), anyString());
        assertThrows(BusinessStateConflictException.class,
                () -> bootstrap.bootstrap("backoffice.admin@meridian.local", "Duplicate"));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        mvc.perform(post("/api/v1/admin/internal-users").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CreateInternalUserRequest("anonymous@meridian.test", "Anonymous", List.of("BACK_OFFICE_ADMIN")))))
                .andExpect(status().isUnauthorized());
        // An audit failure must roll back every provisioning write, including the setup digest.
        jdbc.execute("CREATE FUNCTION reject_bootstrap_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Test audit rejection'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_bootstrap_audit BEFORE INSERT ON audit_events FOR EACH ROW EXECUTE FUNCTION reject_bootstrap_audit()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> bootstrap.bootstrap("rollback@meridian.test", "Rollback"));
            assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
            assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM role_assignments", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class));
            assertTrue(deliveries.isEmpty());
        } finally {
            jdbc.execute("DROP TRIGGER reject_bootstrap_audit ON audit_events");
            jdbc.execute("DROP FUNCTION reject_bootstrap_audit()");
        }
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                try {
                    bootstrap.bootstrap("operator-" + index + "@meridian.test", "Operator");
                    return "created";
                } catch (BusinessStateConflictException failure) {
                    return failure.getErrorCode();
                }
            })).toList();
            var outcomes = List.of(futures.getFirst().get(30, TimeUnit.SECONDS), futures.getLast().get(30, TimeUnit.SECONDS));
            assertTrue(outcomes.contains("created"));
            assertTrue(outcomes.contains("ADMINISTRATOR_ALREADY_ESTABLISHED"));
        }
        assertEquals(1, deliveries.size());
        var admin = jdbc.queryForMap("SELECT * FROM users WHERE normalized_email LIKE 'operator-%'");
        UUID id = (UUID) admin.get("id");
        String email = (String) admin.get("email");
        String raw = deliveries.remove();
        assertEquals("STAFF", admin.get("user_type"));
        assertEquals("ACTIVE", admin.get("status"));
        assertNull(admin.get("customer_id"));
        assertNotNull(admin.get("email_verified_at"));
        assertEquals(codec.digest(raw), jdbc.queryForObject("SELECT token_digest FROM password_reset_tokens WHERE user_id=?", String.class, id));
        assertThrows(AuthenticationFailedException.class, () -> authentication.login(new LoginRequest(email, "Meridian@123")));
        var audit = jdbc.queryForList("SELECT actor_type,actor_user_id,payload::text AS payload FROM audit_events WHERE entity_id=?", id);
        assertEquals(2, audit.size());
        for (var event : audit) {
            assertEquals("SYSTEM", event.get("actor_type"));
            assertNull(event.get("actor_user_id"));
            assertFalse(event.get("payload").toString().contains(raw));
        }
        confirmation.confirmReset(new PasswordResetConfirmationRequest(raw, "Private-Operator-Password-73"));
        assertThrows(AuthenticationFailedException.class,
                () -> confirmation.confirmReset(new PasswordResetConfirmationRequest(raw, "Another-Operator-Password-73")));
        var session = authentication.login(new LoginRequest(email, "Private-Operator-Password-73"));
        assertEquals(List.of("BACK_OFFICE_ADMIN"), session.response().roles().stream().sorted().toList());
        assertTrue(session.response().permissions().contains("identity:user:manage"));
        mvc.perform(get("/api/v1/admin/internal-users").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.response().accessToken()))
                .andExpect(status().isOk());
        for (String role : List.of("LOAN_OFFICER", "APPROVER", "ACCOUNTING_OFFICER", "BACK_OFFICE_ADMIN")) {
            var response = mvc.perform(post("/api/v1/admin/internal-users").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.response().accessToken())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new CreateInternalUserRequest(
                            role.toLowerCase() + "@meridian.test", "Reviewer", List.of(role)))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String setup = deliveries.remove();
            assertFalse(response.contains(setup));
            confirmation.confirmReset(new PasswordResetConfirmationRequest(setup, "Private-Reviewer-Password-73"));
            var reviewer = authentication.login(new LoginRequest(role.toLowerCase() + "@meridian.test", "Private-Reviewer-Password-73"));
            assertEquals(List.of(role), reviewer.response().roles().stream().sorted().toList());
            assertFalse(output.getAll().contains(setup));
        }
        var before = jdbc.queryForMap("SELECT * FROM users WHERE id=?", id);
        assertThrows(BusinessStateConflictException.class, () -> bootstrap.bootstrap(email, "Changed"));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM users WHERE id=?", id));
        // An inactive established admin also blocks a second bootstrap; recovery/administration owns its lifecycle.
        jdbc.update("UPDATE users SET status='DISABLED' WHERE user_type='STAFF'");
        assertThrows(BusinessStateConflictException.class, () -> bootstrap.bootstrap("another@meridian.test", "Another"));
        assertFalse(output.getAll().contains(raw));
        assertFalse(output.getAll().contains("Private-Operator-Password-73"));
    }
}
