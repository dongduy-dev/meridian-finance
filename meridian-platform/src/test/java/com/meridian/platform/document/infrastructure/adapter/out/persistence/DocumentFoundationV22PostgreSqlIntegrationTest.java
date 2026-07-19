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
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class DocumentFoundationV22PostgreSqlIntegrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");

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
    void upgradesV21BackfillsChecklistAndEnforcesImmutableVersionGraph() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "21");
            UUID applicationId = insertLoanApplication(schema);

            assertEquals(1, migrateLatest(schema));
            UUID checklistId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schema + ".document_checklists WHERE loan_application_id = ?",
                    UUID.class,
                    applicationId
            );
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".document_checklists WHERE loan_application_id = ?",
                    Integer.class,
                    applicationId
            ));

            UUID itemId = UUID.randomUUID();
            UUID documentId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            UUID uploaderUserId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + schema + ".users WHERE user_type = 'CUSTOMER' ORDER BY id LIMIT 1",
                    UUID.class
            );
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".document_checklist_items "
                            + "(id, checklist_id, document_type, requirement_status, created_at, updated_at) "
                            + "VALUES (?, ?, 'RECENT_PAYSLIP', 'REQUIRED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    itemId,
                    checklistId
            );
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".documents "
                            + "(id, checklist_item_id, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    documentId,
                    itemId
            );
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".document_versions "
                            + "(id, document_id, version_number, upload_request_id, original_filename, "
                            + "declared_mime_type, detected_mime_type, byte_size, sha256_hex, storage_key, "
                            + "uploader_actor_type, uploader_user_id, uploaded_at, created_at) "
                            + "VALUES (?, ?, 1, ?, 'evidence.pdf', 'application/pdf', 'application/pdf', 10, ?, ?, "
                            + "'CUSTOMER', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    versionId,
                    documentId,
                    UUID.randomUUID(),
                    "a".repeat(64),
                    "opaque/" + UUID.randomUUID(),
                    uploaderUserId
            );
            jdbcTemplate.update(
                    "UPDATE " + schema + ".documents SET current_version_id = ? WHERE id = ?",
                    versionId,
                    documentId
            );

            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "UPDATE " + schema + ".document_versions SET byte_size = 11 WHERE id = ?",
                    versionId
            ));
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".document_versions "
                            + "(document_id, version_number, upload_request_id, original_filename, declared_mime_type, "
                            + "detected_mime_type, byte_size, sha256_hex, storage_key, uploader_actor_type, "
                            + "uploader_user_id, uploaded_at, created_at) VALUES (?, 2, ?, 'bad.pdf', "
                            + "'application/pdf', 'application/pdf', 10485761, ?, ?, 'CUSTOMER', ?, "
                            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    documentId,
                    UUID.randomUUID(),
                    "b".repeat(64),
                    "opaque/" + UUID.randomUUID(),
                    uploaderUserId
            ));
        } finally {
            dropSchema(schema);
        }
    }

    private void migrateTo(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private int migrateLatest(String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate()
                .migrationsExecuted;
    }

    private UUID insertLoanApplication(String schema) {
        UUID customerId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".customers ORDER BY customer_number LIMIT 1",
                UUID.class
        );
        UUID productId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".loan_products WHERE product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
        UUID applicationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".loan_applications "
                        + "(id, customer_id, loan_product_id, application_number, product_code, product_type, status, "
                        + "requested_amount, requested_term_months, submitted_at) "
                        + "VALUES (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', 'SUBMITTED', ?, 1, CURRENT_TIMESTAMP)",
                applicationId,
                customerId,
                productId,
                "SA-V22-" + UUID.randomUUID(),
                new BigDecimal("3000000.00")
        );
        return applicationId;
    }

    private void dropSchema(String schema) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    private static String schemaName(String suffix) {
        return "meridian_v22_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
