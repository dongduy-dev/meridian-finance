package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.CreateInternalUserRequest;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.dto.PasswordResetConfirmationRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase;
import com.meridian.platform.identity.application.port.in.ProvisionInternalUserUseCase;
import com.meridian.platform.identity.application.port.out.PasswordResetTokenCodecPort;
import com.meridian.platform.identity.application.port.out.StaffPasswordSetupNotificationPort;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "meridian.identity.rate-limit.login.max-requests=1000"
})
@AutoConfigureMockMvc
class InternalUserProvisioningPostgreSqlIntegrationTest {
    private static final String SCHEMA = "staff_provisioning_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired ProvisionInternalUserUseCase provisioning;
    @Autowired ConfirmPasswordResetUseCase confirmation;
    @Autowired AuthenticationUseCase authentication;
    @Autowired PasswordResetTokenCodecPort codec;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean StaffPasswordSetupNotificationPort notification;

    @BeforeEach
    void actor() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACTOR_ID, "backoffice.admin@meridian.local", "STAFF", null,
                Set.of("BACK_OFFICE_ADMIN"), Set.of("identity:user:manage")));
    }

    @Test
    void createSetupAndLoginUseDigestOnlyOneTimeTokenAndAuditableRoles() {
        String email = uniqueEmail();
        var created = provisioning.create(new CreateInternalUserRequest(
                "  " + email.toUpperCase() + "  ", "  New Staff  ", List.of("APPROVER", "LOAN_OFFICER")));
        assertEquals(email, created.email());
        assertEquals("New Staff", created.displayName());
        assertEquals("ACTIVE", created.status());
        assertEquals(List.of("APPROVER", "LOAN_OFFICER"), created.assignedRoleCodes());

        var row = jdbc.queryForMap("SELECT user_type, status, customer_id, email_verified_at, authorization_version, failed_login_attempts, locked_until, password_hash FROM users WHERE id = ?", created.userId());
        assertEquals("STAFF", row.get("user_type"));
        assertEquals("ACTIVE", row.get("status"));
        assertEquals(null, row.get("customer_id"));
        assertNotNull(row.get("email_verified_at"));
        assertEquals(0L, ((Number) row.get("authorization_version")).longValue());
        assertEquals(0, ((Number) row.get("failed_login_attempts")).intValue());
        assertEquals(null, row.get("locked_until"));
        assertFalse(encoder.matches("Meridian@123", (String) row.get("password_hash")));
        assertThrows(AuthenticationFailedException.class,
                () -> authentication.login(new LoginRequest(email, "Meridian@123", UserType.STAFF)));

        var token = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notification).sendSetupEmail(eq(email), token.capture());
        String raw = token.getValue();
        String digest = jdbc.queryForObject("SELECT token_digest FROM password_reset_tokens WHERE user_id = ?", String.class, created.userId());
        assertEquals(codec.digest(raw), digest);
        assertNotEquals(raw, digest);

        var audit = jdbc.queryForList("SELECT action, actor_user_id, entity_id, payload::text AS payload FROM audit_events WHERE entity_id = ? ORDER BY sequence_number", created.userId());
        assertEquals(List.of("IDENTITY_USER_CREATED", "IDENTITY_USER_ROLE_ASSIGNED", "IDENTITY_USER_ROLE_ASSIGNED"),
                audit.stream().map(entry -> entry.get("action")).toList());
        for (var entry : audit) {
            assertEquals(ACTOR_ID, entry.get("actor_user_id"));
            assertEquals(created.userId(), entry.get("entity_id"));
            assertFalse(((String) entry.get("payload")).contains(email));
            assertFalse(((String) entry.get("payload")).contains("New Staff"));
            assertFalse(((String) entry.get("payload")).contains(raw));
        }

        confirmation.confirmReset(new PasswordResetConfirmationRequest(raw, "New-Staff-Password-123"));
        assertEquals(created.userId(), authentication.login(new LoginRequest(email, "New-Staff-Password-123", UserType.STAFF)).response().userId());
        assertThrows(AuthenticationFailedException.class,
                () -> confirmation.confirmReset(new PasswordResetConfirmationRequest(raw, "Another-Password-123")));
    }

    @Test
    void duplicateAndNonAssignableRolesLeaveNoNewUser() {
        for (String email : List.of("customer.demo@meridian.local", "loan.officer@meridian.local")) {
            assertEquals("EMAIL_ALREADY_REGISTERED", assertThrows(BusinessStateConflictException.class,
                    () -> provisioning.create(new CreateInternalUserRequest(email.toUpperCase(), "Duplicate", List.of("APPROVER")))).getErrorCode());
        }
        String email = uniqueEmail();
        for (String role : List.of("CUSTOMER", "UNKNOWN_ROLE")) {
            assertEquals("INTERNAL_ROLE_NOT_FOUND", assertThrows(EntityNotFoundException.class,
                    () -> provisioning.create(new CreateInternalUserRequest(email, "Invalid", List.of("APPROVER", role)))).getErrorCode());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE normalized_email = ?", Integer.class, email));
    }

    @Test
    void resendRevokesOldTokenAndRejectsCustomerOrInactiveStaff() {
        String email = uniqueEmail();
        var created = provisioning.create(new CreateInternalUserRequest(email, "Setup Target", List.of("ACCOUNTING_OFFICER")));
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notification).sendSetupEmail(eq(email), captured.capture());
        String first = captured.getValue();
        provisioning.sendPasswordSetup(created.userId());
        verify(notification, times(2)).sendSetupEmail(eq(email), captured.capture());
        String second = captured.getAllValues().getLast();
        assertNotEquals(first, second);
        assertThrows(AuthenticationFailedException.class,
                () -> confirmation.confirmReset(new PasswordResetConfirmationRequest(first, "Invalid-Old-Password")));
        assertEquals("INTERNAL_USER_NOT_FOUND", assertThrows(EntityNotFoundException.class,
                () -> provisioning.sendPasswordSetup(CUSTOMER_ID)).getErrorCode());
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", created.userId());
        assertEquals("INTERNAL_USER_NOT_ACTIVE", assertThrows(BusinessStateConflictException.class,
                () -> provisioning.sendPasswordSetup(created.userId())).getErrorCode());
        assertThrows(AuthenticationFailedException.class,
                () -> confirmation.confirmReset(new PasswordResetConfirmationRequest(second, "Disabled-User-Password")));
    }

    private static String uniqueEmail() {
        return "staff-" + UUID.randomUUID() + "@meridian.local";
    }
}
