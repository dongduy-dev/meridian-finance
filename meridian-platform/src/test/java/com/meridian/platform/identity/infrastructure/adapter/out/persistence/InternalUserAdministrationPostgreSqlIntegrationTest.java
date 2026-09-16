package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.ChangeInternalUserRoleRequest;
import com.meridian.platform.identity.application.dto.ChangeInternalUserStatusRequest;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.ManageInternalUserUseCase;
import com.meridian.platform.identity.application.port.in.QueryInternalUsersUseCase;
import com.meridian.platform.identity.domain.model.UserStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "meridian.identity.rate-limit.login.max-requests=1000",
        "meridian.identity.rate-limit.refresh.max-requests=1000"
})
@AutoConfigureMockMvc
class InternalUserAdministrationPostgreSqlIntegrationTest {

    private static final String SCHEMA = "identity_admin_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");
    private static final UUID LOAN_OFFICER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Autowired ManageInternalUserUseCase commands;
    @Autowired QueryInternalUsersUseCase queries;
    @Autowired AuthenticationUseCase authentication;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void resetTarget() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACTOR_ID,
                "backoffice.admin@meridian.local",
                "STAFF",
                null,
                Set.of("BACK_OFFICE_ADMIN"),
                Set.of("identity:user:manage")
        ));
        jdbcTemplate.update("DELETE FROM refresh_token_sessions WHERE user_id = ?", TARGET_ID);
        jdbcTemplate.update("DELETE FROM audit_events WHERE entity_type = 'IDENTITY_USER'");
        jdbcTemplate.update("DELETE FROM role_assignments WHERE user_id = ?", TARGET_ID);
        jdbcTemplate.update(
                "INSERT INTO role_assignments (id, user_id, role_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), TARGET_ID, LOAN_OFFICER_ROLE_ID
        );
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET status = 'ACTIVE', authorization_version = 0,
                            updated_at = TIMESTAMP '2020-01-01 00:00:00',
                            failed_login_attempts = 0, locked_until = NULL
                        WHERE id = ?
                        """,
                TARGET_ID
        );
    }

    @Test
    void discoveryIsStaffOnlySafeAndDeterministicAndRolesExcludeCustomer() {
        var users = queries.findAll();

        assertEquals(List.of(
                "accounting.officer@meridian.local",
                "approver@meridian.local",
                "backoffice.admin@meridian.local",
                "loan.officer@meridian.local"
        ), users.stream().map(user -> user.email()).toList());
        assertFalse(users.stream().anyMatch(user -> user.userId().equals(CUSTOMER_ID)));
        assertEquals(List.of("LOAN_OFFICER"), users.getLast().assignedRoleCodes());

        assertEquals(List.of("ACCOUNTING_OFFICER", "APPROVER", "BACK_OFFICE_ADMIN", "LOAN_OFFICER"),
                queries.findAssignableRoles().stream().map(role -> role.code()).toList());
    }

    @Test
    void statusMutationBumpsVersionUpdatesTimestampRevokesRefreshAndAuditsOnlyRealChanges() throws Exception {
        AuthenticationResult session = loginTarget();
        LocalDateTime originalUpdatedAt = updatedAt();

        var suspended = commands.changeStatus(TARGET_ID, new ChangeInternalUserStatusRequest(UserStatus.SUSPENDED));
        LocalDateTime changedUpdatedAt = updatedAt();
        commands.changeStatus(TARGET_ID, new ChangeInternalUserStatusRequest(UserStatus.SUSPENDED));

        assertEquals("SUSPENDED", suspended.status());
        assertEquals(1L, authorizationVersion());
        assertNotEquals(originalUpdatedAt, changedUpdatedAt);
        assertEquals(changedUpdatedAt, updatedAt());
        assertEquals(1, auditCount("IDENTITY_USER_STATUS_CHANGED"));
        assertEquals(ACTOR_ID, auditActor("IDENTITY_USER_STATUS_CHANGED"));
        assertEquals("ACTIVE", auditPayload("IDENTITY_USER_STATUS_CHANGED", "previousUserStatus"));
        assertEquals("SUSPENDED", auditPayload("IDENTITY_USER_STATUS_CHANGED", "finalUserStatus"));
        assertEquals(0, activeRefreshSessions());
        assertInvalidAccess(session.response().accessToken());
        assertThrows(AuthenticationFailedException.class, () -> authentication.refresh(session.refreshToken()));
        assertEquals("SUSPENDED", userStatus());
    }

    @Test
    void reactivationDoesNotResurrectRefreshSessionsAndRequiresNewLogin() {
        AuthenticationResult session = loginTarget();
        commands.changeStatus(TARGET_ID, new ChangeInternalUserStatusRequest(UserStatus.DISABLED));
        commands.changeStatus(TARGET_ID, new ChangeInternalUserStatusRequest(UserStatus.ACTIVE));

        assertEquals(2L, authorizationVersion());
        assertEquals(0, activeRefreshSessions());
        assertThrows(AuthenticationFailedException.class, () -> authentication.refresh(session.refreshToken()));
        AuthenticationResult replacement = loginTarget();
        assertTrue(replacement.response().roles().contains("LOAN_OFFICER"));
    }

    @Test
    void roleChangesInvalidateOldAccessAndRefreshIntoCurrentAuthority() throws Exception {
        AuthenticationResult original = loginTarget();
        commands.changeRoleAssignment(
                TARGET_ID, "BACK_OFFICE_ADMIN", new ChangeInternalUserRoleRequest(true)
        );

        assertEquals(1L, authorizationVersion());
        assertInvalidAccess(original.response().accessToken());
        AuthenticationResult added = authentication.refresh(original.refreshToken());
        assertTrue(added.response().roles().contains("BACK_OFFICE_ADMIN"));
        assertTrue(added.response().permissions().contains("identity:user:manage"));
        mockMvc.perform(get("/api/v1/admin/internal-users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + added.response().accessToken()))
                .andExpect(status().isOk());

        commands.changeRoleAssignment(
                TARGET_ID, "BACK_OFFICE_ADMIN", new ChangeInternalUserRoleRequest(false)
        );
        assertInvalidAccess(added.response().accessToken());
        AuthenticationResult removed = authentication.refresh(added.refreshToken());
        assertFalse(removed.response().roles().contains("BACK_OFFICE_ADMIN"));
        assertFalse(removed.response().permissions().contains("identity:user:manage"));
        assertEquals(1, auditCount("IDENTITY_USER_ROLE_ASSIGNED"));
        assertEquals(1, auditCount("IDENTITY_USER_ROLE_REMOVED"));
        assertEquals("BACK_OFFICE_ADMIN", auditPayload("IDENTITY_USER_ROLE_ASSIGNED", "roleCode"));
    }

    @Test
    void roleNoOpsDoNotMutateVersionTimestampOrAuditAndCustomerRoleIsRejected() {
        LocalDateTime originalUpdatedAt = updatedAt();

        commands.changeRoleAssignment(TARGET_ID, "LOAN_OFFICER", new ChangeInternalUserRoleRequest(true));
        commands.changeRoleAssignment(TARGET_ID, "APPROVER", new ChangeInternalUserRoleRequest(false));

        assertEquals(0L, authorizationVersion());
        assertEquals(originalUpdatedAt, updatedAt());
        assertEquals(0, auditCount("IDENTITY_USER_ROLE_ASSIGNED"));
        assertEquals(0, auditCount("IDENTITY_USER_ROLE_REMOVED"));
        assertEquals("INTERNAL_ROLE_NOT_FOUND", assertThrows(
                com.meridian.platform.shared.domain.exception.EntityNotFoundException.class,
                () -> commands.changeRoleAssignment(
                        TARGET_ID, "CUSTOMER", new ChangeInternalUserRoleRequest(true)
                )
        ).getErrorCode());
        assertEquals("INTERNAL_ROLE_NOT_FOUND", assertThrows(
                com.meridian.platform.shared.domain.exception.EntityNotFoundException.class,
                () -> commands.changeRoleAssignment(
                        TARGET_ID, "NOT_A_ROLE", new ChangeInternalUserRoleRequest(true)
                )
        ).getErrorCode());
    }

    @Test
    void customerAndMissingTargetsShareConcealedNotFoundBoundary() {
        for (UUID target : List.of(CUSTOMER_ID, UUID.randomUUID())) {
            assertEquals("INTERNAL_USER_NOT_FOUND", assertThrows(
                    com.meridian.platform.shared.domain.exception.EntityNotFoundException.class,
                    () -> commands.changeStatus(target, new ChangeInternalUserStatusRequest(UserStatus.SUSPENDED))
            ).getErrorCode());
            assertEquals("INTERNAL_USER_NOT_FOUND", assertThrows(
                    com.meridian.platform.shared.domain.exception.EntityNotFoundException.class,
                    () -> commands.changeRoleAssignment(
                            target, "APPROVER", new ChangeInternalUserRoleRequest(true)
                    )
            ).getErrorCode());
        }
    }

    @Test
    void concurrentSameTargetStatusAndRoleCommandsSerializeToOneEffect() throws Exception {
        runConcurrently(
                () -> commands.changeStatus(TARGET_ID, new ChangeInternalUserStatusRequest(UserStatus.SUSPENDED)),
                () -> commands.changeStatus(TARGET_ID, new ChangeInternalUserStatusRequest(UserStatus.SUSPENDED))
        );
        assertEquals(1L, authorizationVersion());
        assertEquals(1, auditCount("IDENTITY_USER_STATUS_CHANGED"));

        jdbcTemplate.update(
                "UPDATE users SET status = 'ACTIVE', authorization_version = 0 WHERE id = ?",
                TARGET_ID
        );
        jdbcTemplate.update("DELETE FROM audit_events WHERE entity_type = 'IDENTITY_USER'");
        runConcurrently(
                () -> commands.changeRoleAssignment(
                        TARGET_ID, "APPROVER", new ChangeInternalUserRoleRequest(true)
                ),
                () -> commands.changeRoleAssignment(
                        TARGET_ID, "APPROVER", new ChangeInternalUserRoleRequest(true)
                )
        );
        assertEquals(1L, authorizationVersion());
        assertEquals(1, auditCount("IDENTITY_USER_ROLE_ASSIGNED"));
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM role_assignments ra
                        JOIN roles r ON r.id = ra.role_id
                        WHERE ra.user_id = ? AND r.code = 'APPROVER'
                        """,
                Integer.class,
                TARGET_ID
        ));
    }

    @Test
    void statusAndRoleCommandsShareUserRowSerializationWithoutLostVersionBumps() throws Exception {
        runConcurrently(
                () -> commands.changeStatus(TARGET_ID, new ChangeInternalUserStatusRequest(UserStatus.SUSPENDED)),
                () -> commands.changeRoleAssignment(
                        TARGET_ID, "APPROVER", new ChangeInternalUserRoleRequest(true)
                )
        );

        assertEquals(2L, authorizationVersion());
        assertEquals(1, auditCount("IDENTITY_USER_STATUS_CHANGED"));
        assertEquals(1, auditCount("IDENTITY_USER_ROLE_ASSIGNED"));
    }

    private AuthenticationResult loginTarget() {
        return authentication.login(new LoginRequest("loan.officer@meridian.local", "Meridian@123"));
    }

    private void assertInvalidAccess(String accessToken) throws Exception {
        mockMvc.perform(get("/api/v1/admin/internal-users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    private void runConcurrently(Runnable firstCommand, Runnable secondCommand) throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> runAfterBarrier(start, firstCommand));
            Future<?> second = executor.submit(() -> runAfterBarrier(start, secondCommand));
            start.await(5, TimeUnit.SECONDS);
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
    }

    private void runAfterBarrier(CyclicBarrier barrier, Runnable command) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            command.run();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private long authorizationVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT authorization_version FROM users WHERE id = ?", Long.class, TARGET_ID
        );
    }

    private String userStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, TARGET_ID);
    }

    private LocalDateTime updatedAt() {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM users WHERE id = ?", LocalDateTime.class, TARGET_ID
        );
    }

    private int activeRefreshSessions() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_sessions WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class,
                TARGET_ID
        );
    }

    private int auditCount(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE entity_type = 'IDENTITY_USER' AND action = ?",
                Integer.class,
                action
        );
    }

    private UUID auditActor(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM audit_events WHERE action = ?", UUID.class, action
        );
    }

    private String auditPayload(String action, String key) {
        return jdbcTemplate.queryForObject(
                "SELECT payload ->> ? FROM audit_events WHERE action = ?", String.class, key, action
        );
    }
}
