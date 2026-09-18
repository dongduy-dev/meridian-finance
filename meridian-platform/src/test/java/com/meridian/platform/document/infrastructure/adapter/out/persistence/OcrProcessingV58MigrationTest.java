package com.meridian.platform.document.infrastructure.adapter.out.persistence;

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
class OcrProcessingV58MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V58__add_ocr_processing_foundation.sql");
    private static final Path CURRENT_SCHEMA = Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }

    @Test
    void upgradesV57AndEnforcesJobResultLeaseEncryptionAndAuditConstraints() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "57");
            UUID versionId = insertIntakeVersion(schema);
            assertEquals(1, migrateTo(schema, "58"));

            UUID jobId = insertJob(schema, versionId, "PENDING", null, false);
            assertThrows(DataAccessException.class,
                    () -> insertJob(schema, versionId, "PENDING", null, false));
            assertThrows(DataAccessException.class,
                    () -> insertJob(schema, UUID.randomUUID(), "PENDING", null, false));
            assertThrows(DataAccessException.class,
                    () -> insertJob(schema, insertIntakeVersion(schema), "PROCESSING", null, false));
            assertThrows(DataAccessException.class,
                    () -> insertJob(schema, insertIntakeVersion(schema), "PENDING", "worker", false));
            assertThrows(DataAccessException.class,
                    () -> insertJob(schema, insertIntakeVersion(schema), "PENDING", null, true));

            insertResult(schema, jobId, "v1:gcm:nonce:ciphertext");
            assertThrows(DataAccessException.class,
                    () -> insertResult(schema, jobId, "v1:gcm:nonce:ciphertext"));
            assertThrows(DataAccessException.class,
                    () -> insertResult(schema, UUID.randomUUID(), "v1:gcm:nonce:ciphertext"));
            UUID anotherJob = insertJob(schema, insertIntakeVersion(schema), "PENDING", null, false);
            assertThrows(DataAccessException.class,
                    () -> insertResult(schema, anotherJob, "plaintext"));

            jdbc.update("INSERT INTO " + schema + ".audit_events "
                            + "(id, operation_id, sequence_number, actor_type, actor_user_id, entity_type, "
                            + "entity_id, action, payload, occurred_at) "
                            + "VALUES (?, ?, 1, 'SYSTEM', NULL, 'OCR_JOB', ?, 'OCR_JOB_CREATED', "
                            + "'{}'::jsonb, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), UUID.randomUUID(), jobId);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV58Foundation() throws IOException {
        String migration = Files.readString(MIGRATION).replace("\r\n", "\n");
        String snapshot = Files.readString(CURRENT_SCHEMA).replace("\r\n", "\n");
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V58"));
        assertTrue(snapshot.contains(migration.trim()));
        assertTrue(migration.contains("UNIQUE (intake_document_version_id)"));
        assertTrue(migration.contains("idx_ocr_jobs_expired_lease"));
        assertTrue(migration.contains("encrypted_extracted_text"));
    }

    private UUID insertIntakeVersion(String schema) {
        UUID staffId = jdbc.queryForObject(
                "SELECT id FROM " + schema + ".users WHERE user_type = 'STAFF' ORDER BY id LIMIT 1",
                UUID.class);
        UUID caseId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".assisted_origination_cases "
                        + "(id, product_code, status, created_by_staff_user_id, created_at, updated_at) "
                        + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                caseId, staffId);
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".intake_documents "
                        + "(id, assisted_origination_case_id, evidence_type, created_at, updated_at) "
                        + "VALUES (?, ?, 'CUSTOMER_IDENTITY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                documentId, caseId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".intake_document_versions "
                        + "(id, intake_document_id, version_number, upload_request_id, original_filename, "
                        + "declared_mime_type, detected_mime_type, byte_size, sha256_hex, storage_key, "
                        + "uploader_staff_user_id, uploaded_at, created_at) "
                        + "VALUES (?, ?, 1, ?, 'identity.pdf', 'application/pdf', 'application/pdf', "
                        + "128, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                versionId, documentId, UUID.randomUUID(), "a".repeat(64),
                "ab/" + UUID.randomUUID(), staffId);
        jdbc.update("UPDATE " + schema + ".intake_documents SET current_version_id = ? WHERE id = ?",
                versionId, documentId);
        return versionId;
    }

    private UUID insertJob(
            String schema, UUID versionId, String state, String leaseOwner, boolean invalidHash
    ) {
        UUID jobId = UUID.randomUUID();
        String leaseExpiry = leaseOwner == null ? "NULL" : "CURRENT_TIMESTAMP + INTERVAL '1 minute'";
        jdbc.update("INSERT INTO " + schema + ".ocr_jobs "
                        + "(id, intake_document_version_id, evidence_type, source_storage_key, "
                        + "source_mime_type, source_sha256_hex, state, lease_owner, lease_expires_at, "
                        + "attempt_count, next_attempt_at, trace_id, created_at, updated_at) "
                        + "VALUES (?, ?, 'CUSTOMER_IDENTITY', 'ab/opaque', 'application/pdf', ?, ?, ?, "
                        + leaseExpiry + ", 0, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                jobId, versionId, invalidHash ? "invalid" : "a".repeat(64), state, leaseOwner, UUID.randomUUID());
        return jobId;
    }

    private void insertResult(String schema, UUID jobId, String envelope) {
        jdbc.update("INSERT INTO " + schema + ".ocr_results "
                        + "(id, ocr_job_id, provider, processor_name, encrypted_extracted_text, "
                        + "encrypted_normalized_payload, encrypted_structured_suggestions, "
                        + "normalized_confidence, disposition, processing_duration_ms, created_at) "
                        + "VALUES (?, ?, 'GOOGLE_DOCUMENT_AI', 'Enterprise Document OCR', ?, ?, ?, "
                        + "0.9000, 'HIGH_CONFIDENCE', 100, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), jobId, envelope, envelope, envelope);
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }

    private static String schemaName(String suffix) {
        return "meridian_v58_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
