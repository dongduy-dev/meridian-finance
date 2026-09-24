package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class CustomerEmploymentV64MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V64__enforce_single_current_customer_employment.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.password", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "meridian156"));
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }

    @Test
    void normalizesMultipleVerifiedRelationshipsBeforeInstallingGlobalInvariant() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "63");
            UUID customerId = UUID.randomUUID();
            UUID older = UUID.fromString("10000000-0000-4000-8000-000000000001");
            UUID winner = UUID.fromString("10000000-0000-4000-8000-000000000002");
            seedVerifiedLink(schema, older, customerId, "OLD", "2026-09-01 08:00:00");
            seedVerifiedLink(schema, winner, customerId, "NEW", "2026-09-02 08:00:00");

            assertEquals(1, migrateTo(schema, "64"));
            Map<UUID, String> statuses = jdbcTemplate.query(
                    "SELECT id, link_status FROM " + schema
                            + ".customer_partner_employee_links WHERE customer_id = ?",
                    result -> {
                        java.util.HashMap<UUID, String> selected = new java.util.HashMap<>();
                        while (result.next()) {
                            selected.put(result.getObject("id", UUID.class), result.getString("link_status"));
                        }
                        return selected;
                    },
                    customerId
            );
            assertEquals("VERIFIED", statuses.get(winner));
            assertEquals("DISABLED", statuses.get(older));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void rejectsSecondVerifiedRelationshipAcrossCompaniesButAllowsHistory() {
        UUID customerId = UUID.randomUUID();
        seedVerifiedLink(CONTEXT_SCHEMA, UUID.randomUUID(), customerId, "ONE", "2026-09-01 08:00:00");

        assertThrows(DataIntegrityViolationException.class, () ->
                seedVerifiedLink(CONTEXT_SCHEMA, UUID.randomUUID(), customerId, "TWO", "2026-09-02 08:00:00"));

        jdbcTemplate.update("UPDATE customer_partner_employee_links SET link_status = 'DISABLED' WHERE customer_id = ?",
                customerId);
        seedVerifiedLink(CONTEXT_SCHEMA, UUID.randomUUID(), customerId, "THREE", "2026-09-03 08:00:00");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_partner_employee_links WHERE customer_id = ? AND link_status = 'VERIFIED'",
                Integer.class, customerId));
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV64Invariant() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("ORDER BY last_refreshed_at DESC, id ASC"));
        assertTrue(migration.contains("ON customer_partner_employee_links (customer_id)"));
        assertTrue(migration.contains("idx_partner_eligibility_reviews_pending_customer_month"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V64"));
        assertTrue(snapshot.contains("ON customer_partner_employee_links (customer_id)"));
        assertTrue(snapshot.contains("idx_partner_eligibility_reviews_pending_customer_month"));
    }

    private void seedVerifiedLink(
            String schema,
            UUID linkId,
            UUID customerId,
            String suffix,
            String refreshedAt
    ) {
        UUID companyId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO %s.customers (
                    id, customer_number, status, verification_status, profile_completion_status
                ) VALUES (?, ?, 'ACTIVE', 'VERIFIED', 'COMPLETE')
                ON CONFLICT (id) DO NOTHING
                """.formatted(schema), customerId, "CUS-V64-" + customerId);
        jdbcTemplate.update("""
                INSERT INTO %s.partner_companies (
                    id, company_code, name, status, salary_advance_policy_limit
                ) VALUES (?, ?, ?, 'ACTIVE', 20000000)
                """.formatted(schema), companyId, "V64-" + suffix + "-" + UUID.randomUUID(), "Partner " + suffix);
        jdbcTemplate.update("""
                INSERT INTO %s.partner_employee_import_batches (
                    id, partner_company_id, effective_month, status, valid_row_count, invalid_row_count
                ) VALUES (?, ?, '2026-09', 'COMPLETED', 1, 0)
                """.formatted(schema), batchId, companyId);
        jdbcTemplate.update("""
                INSERT INTO %s.partner_employees (
                    id, partner_company_id, import_batch_id, employee_code, identity_reference,
                    salary_amount, salary_advance_limit, employment_status, active
                ) VALUES (?, ?, ?, ?, 'IDENTITY-001', 12000000, 4000000, 'ACTIVE', TRUE)
                """.formatted(schema), employeeId, companyId, batchId, "EMP-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO %s.customer_partner_employee_links (
                    id, customer_id, partner_company_id, partner_employee_id, source_import_batch_id,
                    verification_outcome, link_status, verified_identity_ref, verified_employee_code,
                    last_verified_at, last_refreshed_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED', 'IDENTITY-001', ?,
                    CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
                """.formatted(schema), linkId, customerId, companyId, employeeId, batchId, "EMP-" + suffix,
                refreshedAt, refreshedAt, refreshedAt, refreshedAt);
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate()
                .migrationsExecuted;
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static String schemaName(String suffix) {
        return "meridian_v64_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
