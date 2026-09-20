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
class StaffAssistedOriginationV56MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V56__add_staff_assisted_origination_foundation.sql"
    );
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
    void upgradesV55AndEnforcesCaseEvidencePermissionAuditAndImmutabilityRules() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "55");
            assertEquals(1, migrateTo(schema, "56"));

            UUID loanOfficerId = jdbcTemplate.queryForObject(
                    "SELECT u.id FROM " + schema + ".users u "
                            + "JOIN " + schema + ".role_assignments ra ON ra.user_id = u.id "
                            + "JOIN " + schema + ".roles r ON r.id = ra.role_id "
                            + "WHERE r.code = 'LOAN_OFFICER' ORDER BY u.id LIMIT 1",
                    UUID.class
            );
            assertEquals(3, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE r.code = 'LOAN_OFFICER' AND p.code IN "
                            + "('customer:intake:manage', 'loan:originate:staff', 'document:upload:intake')",
                    Integer.class
            ));

            UUID caseId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".assisted_origination_cases "
                            + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at) "
                            + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', NULL, 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    caseId, loanOfficerId
            );
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".assisted_origination_cases "
                            + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at) "
                            + "VALUES (?, 'SALARY_ADVANCE', NULL, 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), loanOfficerId
            ));
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".assisted_origination_cases "
                            + "(id, product_code, customer_id, status, created_by_staff_user_id, created_at, updated_at) "
                            + "VALUES (?, 'COLLATERAL_LOAN', NULL, 'ABANDONED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), loanOfficerId
            ));

            UUID documentId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".intake_documents "
                            + "(id, assisted_origination_case_id, evidence_type, created_at, updated_at) "
                            + "VALUES (?, ?, 'UCL_PAPER_APPLICATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    documentId, caseId
            );
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".intake_document_versions "
                            + "(id, intake_document_id, version_number, upload_request_id, baseline_version_id, "
                            + "original_filename, declared_mime_type, detected_mime_type, byte_size, sha256_hex, "
                            + "storage_key, uploader_staff_user_id, uploaded_at, created_at) "
                            + "VALUES (?, ?, 1, ?, NULL, 'paper-application.pdf', 'application/pdf', "
                            + "'application/pdf', 128, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    versionId, documentId, UUID.randomUUID(), "a".repeat(64),
                    "intake/" + UUID.randomUUID(), loanOfficerId
            );
            jdbcTemplate.update(
                    "UPDATE " + schema + ".intake_documents SET current_version_id = ? WHERE id = ?",
                    versionId, documentId
            );
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "UPDATE " + schema + ".intake_document_versions SET byte_size = 129 WHERE id = ?",
                    versionId
            ));

            insertAudit(schema, "ASSISTED_ORIGINATION_CASE", caseId, "ASSISTED_ORIGINATION_CASE_CREATED");
            insertAudit(schema, "INTAKE_DOCUMENT_VERSION", versionId, "INTAKE_DOCUMENT_VERSION_UPLOADED");
            assertThrows(DataAccessException.class, () ->
                    insertAudit(schema, "INTAKE_DOCUMENT", documentId, "INTAKE_DOCUMENT_DELETED"));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV56Foundation() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("CREATE TABLE assisted_origination_cases"));
        assertTrue(migration.contains("CREATE TABLE intake_documents"));
        assertTrue(migration.contains("CREATE TABLE intake_document_versions"));
        assertTrue(migration.contains("trg_intake_document_versions_immutable"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V59"));
        assertTrue(snapshot.contains("'ASSISTED_ORIGINATION_CASE'"));
        assertTrue(snapshot.contains("'INTAKE_DOCUMENT_VERSION_UPLOADED'"));
    }

    private void insertAudit(String schema, String entityType, UUID entityId, String action) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".audit_events "
                        + "(id, operation_id, sequence_number, actor_type, actor_user_id, entity_type, "
                        + "entity_id, action, payload, occurred_at) "
                        + "VALUES (?, ?, 1, 'SYSTEM', NULL, ?, ?, ?, '{}'::jsonb, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), UUID.randomUUID(), entityType, entityId, action
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
        return "meridian_v56_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
