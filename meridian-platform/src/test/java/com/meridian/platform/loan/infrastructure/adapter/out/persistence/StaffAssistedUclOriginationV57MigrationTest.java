package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class StaffAssistedUclOriginationV57MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V57__add_staff_assisted_ucl_origination.sql");
    private static final Path CURRENT_SCHEMA = Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }

    @Test
    void upgradesV56AndEnforcesChannelCasePermissionAndAuditRules() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "56");
            UUID customerId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schema + ".customers ORDER BY id LIMIT 1", UUID.class);
            UUID uclProductId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schema + ".loan_products WHERE product_code = 'UNSECURED_CONSUMER_LOAN'",
                    UUID.class);
            UUID salaryProductId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schema + ".loan_products WHERE product_code = 'SALARY_ADVANCE'",
                    UUID.class);
            UUID staffId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schema + ".users WHERE user_type = 'STAFF' ORDER BY id LIMIT 1",
                    UUID.class);
            UUID existingApplication = UUID.randomUUID();
            insertApplication(schema, existingApplication, customerId, uclProductId,
                    "UCL-V57-BACKFILL", "UNSECURED_CONSUMER_LOAN", "UNSECURED", null);

            assertEquals(1, migrateTo(schema, "57"));
            assertEquals("CUSTOMER_DIGITAL", jdbcTemplate.queryForObject(
                    "SELECT origination_channel FROM " + schema + ".loan_applications WHERE id = ?",
                    String.class, existingApplication));
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "UPDATE " + schema + ".loan_applications SET origination_channel = 'STAFF_ASSISTED' WHERE id = ?",
                    existingApplication));
            assertThrows(DataAccessException.class, () -> insertApplication(
                    schema, UUID.randomUUID(), customerId, salaryProductId,
                    "SA-V57-INVALID", "SALARY_ADVANCE", "SALARY_BASED", "STAFF_ASSISTED"));
            assertThrows(DataAccessException.class, () -> insertApplication(
                    schema, UUID.randomUUID(), customerId, uclProductId,
                    "UCL-V57-INVALID", "UNSECURED_CONSUMER_LOAN", "UNSECURED", "UNSUPPORTED"));

            jdbcTemplate.update("UPDATE " + schema + ".loan_applications SET status = 'CANCELLED' WHERE id = ?",
                    existingApplication);

            UUID assistedApplication = UUID.randomUUID();
            insertApplication(schema, assistedApplication, customerId, uclProductId,
                    "UCL-V57-ASSISTED", "UNSECURED_CONSUMER_LOAN", "UNSECURED", "STAFF_ASSISTED");
            UUID completedCase = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + schema + ".assisted_origination_cases "
                            + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at, terminal_at, loan_application_id) "
                            + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', ?, 'COMPLETED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
                    completedCase, customerId, staffId, assistedApplication);
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".assisted_origination_cases "
                            + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at, terminal_at, loan_application_id) "
                            + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', ?, 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, ?)",
                    UUID.randomUUID(), customerId, staffId, assistedApplication));
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".assisted_origination_cases "
                            + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at, terminal_at, loan_application_id) "
                            + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', ?, 'COMPLETED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)",
                    UUID.randomUUID(), customerId, staffId));
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".assisted_origination_cases "
                            + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at, terminal_at, loan_application_id) "
                            + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', ?, 'COMPLETED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
                    UUID.randomUUID(), customerId, staffId, assistedApplication));

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE r.code = 'LOAN_OFFICER' AND p.code = 'document:upload:assisted'",
                    Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".permissions WHERE code = 'document:upload:staff'",
                    Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".permissions WHERE code = 'document:upload:intake'",
                    Integer.class));

            insertAudit(schema, completedCase, "ASSISTED_ORIGINATION_CASE_COMPLETED");
            assertThrows(DataAccessException.class, () -> insertAudit(schema, completedCase, "UNKNOWN_ACTION"));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV57Foundation() throws IOException {
        String migration = Files.readString(MIGRATION).replace("\r\n", "\n");
        String snapshot = Files.readString(CURRENT_SCHEMA).replace("\r\n", "\n");
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V64"));
        assertTrue(snapshot.contains(migration.trim()));
        assertTrue(migration.contains("origination_channel"));
        assertTrue(migration.contains("document:upload:assisted"));
        assertTrue(migration.contains("ASSISTED_ORIGINATION_CASE_COMPLETED"));
    }

    private void insertApplication(
            String schema, UUID id, UUID customerId, UUID productId, String number,
            String productCode, String productType, String channel
    ) {
        String channelColumns = channel == null ? "" : ", origination_channel";
        String channelValue = channel == null ? "" : ", '" + channel + "'";
        jdbcTemplate.update("INSERT INTO " + schema + ".loan_applications "
                        + "(id, customer_id, loan_product_id, application_number, product_code, product_type, "
                        + "status, requested_amount, requested_term_months, submitted_at, created_at, updated_at"
                        + channelColumns + ") VALUES (?, ?, ?, ?, ?, ?, 'DOCUMENTS_PENDING', 10000000, 12, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP" + channelValue + ")",
                id, customerId, productId, number, productCode, productType);
    }

    private void insertAudit(String schema, UUID entityId, String action) {
        jdbcTemplate.update("INSERT INTO " + schema + ".audit_events "
                        + "(id, operation_id, sequence_number, actor_type, actor_user_id, entity_type, "
                        + "entity_id, action, payload, occurred_at) "
                        + "VALUES (?, ?, 1, 'SYSTEM', NULL, 'ASSISTED_ORIGINATION_CASE', ?, ?, '{}'::jsonb, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), UUID.randomUUID(), entityId, action);
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }

    private static String schemaName(String suffix) {
        return "meridian_v57_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
