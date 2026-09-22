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
class StaffAssistedDownstreamV60MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V60__add_staff_assisted_downstream_actions.sql");
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
    void upgradesV59WithActionEvidenceRecordsPermissionsAndImmutableTriggers() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "59");
            assertEquals(1, migrateTo(schema, "60"));
            assertEquals(4, jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? "
                            + "AND table_name IN ('assisted_action_documents', "
                            + "'assisted_action_document_versions', 'staff_assisted_offer_responses', "
                            + "'staff_assisted_contract_acknowledgments')",
                    Integer.class, schema));
            assertEquals(3, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".permissions WHERE code IN "
                            + "('loan:offer:respond:staff', 'loan:contract:acknowledge:staff', "
                            + "'document:upload:assisted-action')",
                    Integer.class));
            assertEquals(4, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE (r.code = 'LOAN_OFFICER' AND p.code IN "
                            + "('loan:offer:respond:staff', 'document:upload:assisted-action')) "
                            + "OR (r.code = 'ACCOUNTING_OFFICER' AND p.code IN "
                            + "('loan:contract:acknowledge:staff', 'document:upload:assisted-action'))",
                    Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE p.code IN ('loan:offer:respond:staff', "
                            + "'loan:contract:acknowledge:staff', 'document:upload:assisted-action') "
                            + "AND r.code NOT IN ('LOAN_OFFICER', 'ACCOUNTING_OFFICER')",
                    Integer.class));
            assertEquals(3, jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger trigger "
                            + "JOIN pg_class table_ref ON table_ref.oid = trigger.tgrelid "
                            + "JOIN pg_namespace namespace_ref ON namespace_ref.oid = table_ref.relnamespace "
                            + "WHERE namespace_ref.nspname = ? AND NOT trigger.tgisinternal "
                            + "AND table_ref.relname IN ('assisted_action_document_versions', "
                            + "'staff_assisted_offer_responses', 'staff_assisted_contract_acknowledgments')",
                    Integer.class, schema));
            assertEquals(8, jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint constraint_ref "
                            + "JOIN pg_namespace namespace_ref "
                            + "ON namespace_ref.oid = constraint_ref.connamespace "
                            + "WHERE namespace_ref.nspname = ? AND constraint_ref.contype = 'u' "
                            + "AND constraint_ref.conname IN ("
                            + "'uq_assisted_action_documents_offer', "
                            + "'uq_assisted_action_documents_contract_version', "
                            + "'uq_assisted_action_versions_document_sequence', "
                            + "'uq_assisted_action_versions_upload_request', "
                            + "'uq_staff_assisted_offer_response_request', "
                            + "'uq_staff_assisted_offer_response_offer', "
                            + "'uq_staff_assisted_contract_ack_request', "
                            + "'uq_staff_assisted_contract_ack_version')",
                    Integer.class, schema));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint constraint_ref "
                            + "JOIN pg_class source_table ON source_table.oid = constraint_ref.conrelid "
                            + "JOIN pg_class target_table ON target_table.oid = constraint_ref.confrelid "
                            + "JOIN pg_namespace namespace_ref ON namespace_ref.oid = source_table.relnamespace "
                            + "WHERE namespace_ref.nspname = ? AND constraint_ref.contype = 'f' "
                            + "AND source_table.relname IN ('staff_assisted_offer_responses', "
                            + "'staff_assisted_contract_acknowledgments') "
                            + "AND target_table.relname = 'assisted_action_document_versions'",
                    Integer.class, schema));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV60Boundary() throws IOException {
        String migration = Files.readString(MIGRATION).replace("\r\n", "\n");
        String snapshot = Files.readString(CURRENT_SCHEMA).replace("\r\n", "\n");
        String snapshotWithoutV63AuditActions = snapshot
                .replace(",\n        'PARTNER_ELIGIBILITY_REVIEW'", "")
                .replace(",\n        'PARTNER_ELIGIBILITY_REVIEW_APPROVED',\n"
                        + "        'PARTNER_ELIGIBILITY_REVIEW_REJECTED'", "");
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V63"));
        assertTrue(snapshotWithoutV63AuditActions.contains(migration.trim()));
        assertTrue(migration.contains("loan:offer:respond:staff"));
        assertTrue(migration.contains("loan:contract:acknowledge:staff"));
        assertTrue(migration.contains("document:upload:assisted-action"));
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }

    private static String schemaName(String suffix) {
        return "meridian_v60_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
