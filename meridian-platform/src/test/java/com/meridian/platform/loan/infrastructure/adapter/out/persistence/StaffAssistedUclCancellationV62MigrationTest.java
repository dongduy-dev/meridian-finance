package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class StaffAssistedUclCancellationV62MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V62__add_staff_assisted_ucl_cancellation.sql");
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
    void upgradesV61WithNarrowPermissionTargetsAndCrossContextReferences() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "61");
            assertEquals(1, migrateTo(schema, "62"));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".permissions "
                            + "WHERE code = 'loan:cancel:staff'",
                    Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE r.code = 'LOAN_OFFICER' AND p.code = 'loan:cancel:staff'",
                    Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE r.code <> 'LOAN_OFFICER' AND p.code = 'loan:cancel:staff'",
                    Integer.class));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? "
                            + "AND ((table_name = 'assisted_action_documents' "
                            + "AND column_name = 'correction_request_id') "
                            + "OR (table_name = 'loan_application_cancellations' "
                            + "AND column_name = 'assisted_evidence_document_version_id'))",
                    Integer.class, schema));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint constraint_ref "
                            + "JOIN pg_namespace namespace_ref "
                            + "ON namespace_ref.oid = constraint_ref.connamespace "
                            + "WHERE namespace_ref.nspname = ? "
                            + "AND constraint_ref.conname = 'uq_assisted_action_documents_correction'",
                    Integer.class, schema));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint constraint_ref "
                            + "JOIN pg_class source_table ON source_table.oid = constraint_ref.conrelid "
                            + "JOIN pg_class target_table ON target_table.oid = constraint_ref.confrelid "
                            + "JOIN pg_namespace namespace_ref ON namespace_ref.oid = source_table.relnamespace "
                            + "WHERE namespace_ref.nspname = ? AND constraint_ref.contype = 'f' "
                            + "AND ((source_table.relname = 'assisted_action_documents' "
                            + "AND target_table.relname = 'loan_correction_requests') "
                            + "OR (source_table.relname = 'loan_application_cancellations' "
                            + "AND target_table.relname = 'assisted_action_document_versions'))",
                    Integer.class, schema));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV62Boundary() throws IOException {
        String migration = Files.readString(MIGRATION).replace("\r\n", "\n");
        String snapshot = Files.readString(CURRENT_SCHEMA).replace("\r\n", "\n");
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V62"));
        assertTrue(snapshot.contains(migration.trim()));
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }

    private static String schemaName(String suffix) {
        return "meridian_v62_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
