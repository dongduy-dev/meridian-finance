package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.application.service.UpdateOwnCustomerProfileService;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationsUseCase;
import com.meridian.platform.loan.application.port.in.RevealStaffCustomerIdentityReferenceUseCase;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false"})
class CustomerIdentityReferenceRevealPostgreSqlIntegrationTest {
    private static final String SCHEMA = "identity_reveal_" + UUID.randomUUID().toString().replace("-", "");
    private static final Set<String> PERMISSIONS = Set.of("loan:read", "customer:read", "customer:identity:reveal");
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired UpdateOwnCustomerProfileService profiles;
    @Autowired QueryStaffLoanApplicationsUseCase cases;
    @Autowired RevealStaffCustomerIdentityReferenceUseCase reveal;
    @MockitoSpyBean CustomerSensitiveValueProtector protector;
    private UUID customerId;
    private UUID applicationId;
    private UUID officer;

    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.flyway.schemas", () -> SCHEMA); r.add("spring.flyway.default-schema", () -> SCHEMA);
        r.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }
    private void auth(String type, UUID customer, UUID user, Set<String> permissions) {
        var actor = new AuthenticatedUser(user, "fictional@meridian.test", type, customer,
                Set.of(type.equals("STAFF") ? "LOAN_OFFICER" : "CUSTOMER"), permissions);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        SecurityContextHolder.setContext(context);
    }
    @BeforeEach void setup() {
        customerId = UUID.randomUUID(); applicationId = UUID.randomUUID();
        UUID customerUser = jdbc.queryForObject("select id from users where user_type='CUSTOMER' order by id limit 1", UUID.class);
        officer = jdbc.queryForObject("select u.id from users u join role_assignments ur on ur.user_id=u.id join roles r on r.id=ur.role_id where r.code='LOAN_OFFICER' order by u.id limit 1", UUID.class);
        jdbc.update("insert into customers (id,customer_number,status,verification_status,profile_completion_status) values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')", customerId, "FICTIONAL-" + customerId);
        auth("CUSTOMER", customerId, customerUser, Set.of("customer:profile:write:own"));
        profiles.updateOwnProfile(new UpdateCustomerProfileRequest("Ari Fictional", "FICTIONAL-ID-8901-" + customerId,
                "0900000000", "Fictional address", "EMPLOYED", "Fictional employer", true, true));
        jdbc.update("insert into loan_applications (id,customer_id,loan_product_id,application_number,product_code,product_type,status,requested_amount,requested_term_months,submitted_at) select ?,?,id,?,product_code,product_type,'CANCELLED',3000000,1,now() from loan_products where product_code='UNSECURED_CONSUMER_LOAN'", applicationId, customerId, "FICTIONAL-" + applicationId);
        auth("STAFF", null, officer, PERMISSIONS);
        clearInvocations(protector);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void ordinaryCaseNeverDecryptsAndExplicitRevealWritesExactlyOneSafeAudit() throws Exception {
        var context = cases.queryCase(applicationId).customerContext();
        String reference = ("FICTIONAL-ID-8901-" + customerId).toUpperCase(Locale.ROOT);
        assertEquals("****" + reference.substring(reference.length()-4), context.maskedIdentityReference());
        String json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(context);
        assertFalse(json.contains(reference)); assertFalse(json.contains("ciphertext")); assertFalse(json.contains("fingerprint"));
        verify(protector, never()).revealToBytes(any()); verify(protector, never()).reveal(any());
        assertEquals(reference, reveal.reveal(applicationId).identityReference());
        var row = jdbc.queryForMap("select entity_type, entity_id, actor_user_id, payload::text as payload from audit_events where action='CUSTOMER_IDENTITY_REFERENCE_REVEALED' and entity_id=?", customerId);
        assertEquals("CUSTOMER", row.get("entity_type")); assertEquals(customerId, row.get("entity_id")); assertEquals(officer, row.get("actor_user_id"));
        var payload = tools.jackson.databind.json.JsonMapper.builder().build().readTree((String) row.get("payload"));
        assertEquals(2, payload.size()); assertEquals(customerId.toString(), payload.get("customerId").asText());
        assertEquals(applicationId.toString(), payload.get("loanApplicationId").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action='CUSTOMER_IDENTITY_REFERENCE_REVEALED' and entity_id=?", Integer.class, customerId));
        assertEquals("UNVERIFIED", jdbc.queryForObject("select verification_status from customers where id=?", String.class, customerId));
    }

    @Test void failedAuditPersistencePreventsSuccessfulRevealAndLeavesNoAccessAudit() {
        jdbc.execute("create function reject_identity_reveal_audit() returns trigger language plpgsql as $$ begin if NEW.action='CUSTOMER_IDENTITY_REFERENCE_REVEALED' then raise exception 'fictional audit failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger reject_identity_reveal_audit before insert on audit_events for each row execute function reject_identity_reveal_audit()");
        try {
            assertThrows(RuntimeException.class, () -> reveal.reveal(applicationId));
            assertEquals(0, jdbc.queryForObject("select count(*) from audit_events where action='CUSTOMER_IDENTITY_REFERENCE_REVEALED' and entity_id=?", Integer.class, customerId));
        } finally {
            jdbc.execute("drop trigger reject_identity_reveal_audit on audit_events");
            jdbc.execute("drop function reject_identity_reveal_audit()");
        }
    }

    @Test void v70UpgradeAndSnapshotPreserveAllAuditActionsAndGrantOnlyLoanOfficer() throws Exception {
        assertEquals(List.of("LOAN_OFFICER"), grants(SCHEMA));
        String constraint = auditConstraint(SCHEMA);
        for (var action : BusinessAuditAction.values()) assertTrue(constraint.contains("'" + action.name() + "'"), action.name());
        String schema = "reveal_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String snapshotSchema = "reveal_snapshot_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrate(schema, "69");
            assertTrue(grants(schema).isEmpty());
            assertEquals(1, migrate(schema, "70"));
            assertEquals(List.of("LOAN_OFFICER"), grants(schema));
            assertEquals(0, migrate(schema, "70"));
            migrate(snapshotSchema, "69");
            String snapshot = Files.readString(Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql"));
            String delta = snapshot.substring(snapshot.indexOf("-- V70 protected Customer Identity Reference reveal"), snapshot.indexOf("-- V71 controlled Customer Identity Reference correction audit"));
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                try { statement.execute("set search_path to " + snapshotSchema); statement.execute(delta); }
                finally { statement.execute("set search_path to " + SCHEMA); }
            }
            assertEquals(grants(schema), grants(snapshotSchema));
            assertEquals(auditConstraint(schema), auditConstraint(snapshotSchema));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
            jdbc.execute("drop schema if exists " + snapshotSchema + " cascade");
        }
    }
    private int migrate(String schema, String target) {
        return org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }
    private List<String> grants(String schema) {
        return jdbc.queryForList("select r.code from " + schema + ".roles r join " + schema + ".role_permissions rp on rp.role_id=r.id join " + schema + ".permissions p on p.id=rp.permission_id where p.code='customer:identity:reveal' order by r.code", String.class);
    }
    private String auditConstraint(String schema) {
        return jdbc.queryForObject("select pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname=? and c.conname='chk_audit_events_action'", String.class, schema);
    }
}
