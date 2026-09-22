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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class PartnerAdministrationV53MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V53__add_partner_administration_commands.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }

    @Test
    void upgradesExistingV52StateWithoutInvalidatingHistoricalBatches() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "52");
            Integer historicalCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".partner_employee_import_batches",
                    Integer.class
            );

            assertEquals(1, migrateTo(schema, "53"));
            assertEquals(historicalCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".partner_employee_import_batches",
                    Integer.class
            ));
            assertEquals(historicalCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema
                            + ".partner_employee_import_batches WHERE request_id IS NULL"
                            + " AND request_fingerprint IS NULL AND rejection_summary = '[]'::jsonb",
                    Integer.class
            ));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void enforcesReplayMonthFingerprintAndSafeRejectionSummaryIntegrity() {
        UUID requestId = UUID.randomUUID();
        insertBatch(UUID.randomUUID(), "2026-09", requestId, "a".repeat(64), 1, 0, "[]");

        assertThrows(DataIntegrityViolationException.class, () ->
                insertBatch(UUID.randomUUID(), "2026-99", UUID.randomUUID(), "b".repeat(64), 1, 0, "[]"));
        assertThrows(DataIntegrityViolationException.class, () ->
                insertBatch(UUID.randomUUID(), "2026-09", UUID.randomUUID(), null, 1, 0, "[]"));
        assertThrows(DataIntegrityViolationException.class, () ->
                insertBatch(UUID.randomUUID(), "2026-09", UUID.randomUUID(), "not-sha256", 1, 0, "[]"));
        assertThrows(DataIntegrityViolationException.class, () ->
                insertBatch(UUID.randomUUID(), "2026-09", requestId, "a".repeat(64), 1, 0, "[]"));
        assertThrows(DataIntegrityViolationException.class, () ->
                insertBatch(UUID.randomUUID(), "2026-09", UUID.randomUUID(), "c".repeat(64), 0, 1, "[]"));
    }

    @Test
    void allowsCorrectedLaterCompletedBatchForTheSameCompanyAndMonth() {
        insertBatch(UUID.randomUUID(), "2026-10", UUID.randomUUID(), "d".repeat(64), 1, 0, "[]");
        insertBatch(UUID.randomUUID(), "2026-10", UUID.randomUUID(), "e".repeat(64), 1, 0, "[]");

        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partner_employee_import_batches"
                        + " WHERE partner_company_id = ? AND effective_month = '2026-10'",
                Integer.class,
                COMPANY_ID
        ) >= 2);
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV53State() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("uq_partner_employee_import_batches_request_id"));
        assertTrue(migration.contains("chk_partner_employee_import_batches_replay_pair"));
        assertTrue(migration.contains("PARTNER_EMPLOYEE_IMPORT_COMPLETED"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V60"));
        assertTrue(snapshot.contains("uq_partner_employee_import_batches_request_id"));
        assertTrue(snapshot.contains("PARTNER_EMPLOYEE_IMPORT_COMPLETED"));
    }

    private void insertBatch(
            UUID id,
            String effectiveMonth,
            UUID requestId,
            String fingerprint,
            int validRows,
            int invalidRows,
            String rejectionSummary
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO partner_employee_import_batches (
                            id, partner_company_id, effective_month, status,
                            valid_row_count, invalid_row_count, request_id,
                            request_fingerprint, rejection_summary
                        ) VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?, ?, CAST(? AS jsonb))
                        """,
                id, COMPANY_ID, effectiveMonth, validRows, invalidRows,
                requestId, fingerprint, rejectionSummary
        );
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
        return "meridian_v53_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
