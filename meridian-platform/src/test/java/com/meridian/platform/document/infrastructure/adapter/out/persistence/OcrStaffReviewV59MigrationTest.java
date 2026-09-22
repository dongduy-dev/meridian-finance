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
class OcrStaffReviewV59MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V59__add_ocr_staff_review.sql");
    private static final Path CURRENT_SCHEMA = Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }

    @Test
    void upgradesV58AndEnforcesImmutableEncryptedReviewAndAuditVocabulary() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "58");
            Seed seed = seedCompletedResult(schema);
            assertEquals(1, migrateTo(schema, "59"));

            insertReview(schema, seed, "v1:gcm:nonce:ciphertext");
            assertThrows(DataAccessException.class,
                    () -> insertReview(schema, seed, "v1:gcm:nonce:another"));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE " + schema + ".ocr_reviews SET encrypted_reviewed_fields = ? "
                            + "WHERE ocr_result_id = ?",
                    "v1:gcm:changed:ciphertext", seed.resultId()));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "DELETE FROM " + schema + ".ocr_reviews WHERE ocr_result_id = ?",
                    seed.resultId()));
            Seed second = seedCompletedResult(schema);
            assertThrows(DataAccessException.class,
                    () -> insertReview(schema, second, "plaintext"));

            jdbc.update("INSERT INTO " + schema + ".audit_events "
                            + "(id, operation_id, sequence_number, actor_type, actor_user_id, entity_type, "
                            + "entity_id, action, payload, occurred_at) "
                            + "VALUES (?, ?, 1, 'USER', ?, 'OCR_REVIEW', ?, 'OCR_RESULT_REVIEWED', "
                            + "'{}'::jsonb, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), UUID.randomUUID(), seed.staffId(), UUID.randomUUID());
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV59ReviewTable() throws IOException {
        String migration = Files.readString(MIGRATION).replace("\r\n", "\n");
        String snapshot = Files.readString(CURRENT_SCHEMA).replace("\r\n", "\n");
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V62"));
        assertTrue(snapshot.contains(migration.trim()));
        assertTrue(migration.contains("UNIQUE (ocr_result_id)"));
        assertTrue(migration.contains("encrypted_reviewed_fields"));
        assertTrue(migration.contains("OCR_RESULT_REVIEWED"));
    }

    private Seed seedCompletedResult(String schema) {
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
        UUID jobId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".ocr_jobs "
                        + "(id, intake_document_version_id, evidence_type, source_storage_key, "
                        + "source_mime_type, source_sha256_hex, state, attempt_count, next_attempt_at, "
                        + "trace_id, created_at, updated_at, completed_at) "
                        + "VALUES (?, ?, 'CUSTOMER_IDENTITY', 'ab/opaque', 'application/pdf', ?, "
                        + "'COMPLETED', 1, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                jobId, versionId, "a".repeat(64), UUID.randomUUID());
        UUID resultId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".ocr_results "
                        + "(id, ocr_job_id, provider, processor_name, encrypted_extracted_text, "
                        + "encrypted_normalized_payload, encrypted_structured_suggestions, disposition, "
                        + "processing_duration_ms, created_at) VALUES (?, ?, 'TEST', 'test', ?, ?, ?, "
                        + "'PENDING_REVIEW', 1, CURRENT_TIMESTAMP)",
                resultId, jobId, "v1:gcm:n:c", "v1:gcm:n:c", "v1:gcm:n:c");
        return new Seed(resultId, staffId);
    }

    private void insertReview(String schema, Seed seed, String envelope) {
        jdbc.update("INSERT INTO " + schema + ".ocr_reviews "
                        + "(id, ocr_result_id, reviewer_staff_user_id, source_disposition, "
                        + "encrypted_reviewed_fields, reviewed_at) "
                        + "VALUES (?, ?, ?, 'PENDING_REVIEW', ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), seed.resultId(), seed.staffId(), envelope);
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }

    private static String schemaName(String suffix) {
        return "meridian_v59_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private record Seed(UUID resultId, UUID staffId) {
    }
}
