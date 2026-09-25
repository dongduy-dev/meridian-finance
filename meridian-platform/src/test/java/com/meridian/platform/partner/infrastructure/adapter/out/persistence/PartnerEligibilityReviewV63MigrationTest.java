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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class PartnerEligibilityReviewV63MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V63__add_partner_eligibility_manual_reviews.sql"
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
    void upgradesV62WithOneNewMigration() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "62");
            assertEquals(1, migrateTo(schema, "63"));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void enforcesOnePendingReviewAndTerminalEvidenceShape() {
        UUID companyId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        jdbcTemplate.update("""
                INSERT INTO partner_companies (
                    id, company_code, name, status, salary_advance_policy_limit
                ) VALUES (?, ?, 'Migration Partner', 'ACTIVE', 20000000)
                """, companyId, "V63-" + UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO partner_employee_import_batches (
                    id, partner_company_id, effective_month, status, valid_row_count, invalid_row_count
                ) VALUES (?, ?, ?, 'COMPLETED', 0, 0)
                """, batchId, companyId, month);
        insertPending(UUID.randomUUID(), customerId, companyId, batchId, month);

        assertThrows(DataIntegrityViolationException.class, () ->
                insertPending(UUID.randomUUID(), customerId, companyId, batchId, month));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO partner_eligibility_reviews (
                    id, customer_id, partner_company_id, effective_month, source_import_batch_id,
                    trigger_outcome, requested_employee_code, status, decision_outcome,
                    decision_reason, reviewer_user_id, reviewed_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'NOT_FOUND', 'EMP-001', 'APPROVED',
                    'MANUAL_REVIEW_APPROVED', 'CURRENT_EMPLOYEE_CONFIRMED', ?, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), UUID.randomUUID(), companyId, month, batchId, UUID.randomUUID()));
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV63State() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("partner_eligibility_reviews"));
        assertTrue(migration.contains("uq_partner_eligibility_reviews_pending_customer_company"));
        assertTrue(migration.contains("PARTNER_ELIGIBILITY_REVIEW_APPROVED"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V64"));
        assertTrue(snapshot.contains("partner_eligibility_reviews"));
        assertTrue(snapshot.contains("PARTNER_ELIGIBILITY_REVIEW_APPROVED"));
    }

    private void insertPending(UUID id, UUID customerId, UUID companyId, UUID batchId, String month) {
        jdbcTemplate.update("""
                INSERT INTO partner_eligibility_reviews (
                    id, customer_id, partner_company_id, effective_month, source_import_batch_id,
                    trigger_outcome, requested_employee_code, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'NOT_FOUND', 'EMP-001', 'PENDING',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, customerId, companyId, month, batchId);
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
        return "meridian_v63_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
