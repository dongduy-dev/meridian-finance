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
class AssistedCustomerCorrectionV61MigrationTest {
    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V61__add_assisted_customer_correction_upload_permission.sql");
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
    void upgradesV60AndGrantsOnlyLoanOfficerAssistedCorrectionUpload() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "60");
            assertEquals(1, migrateTo(schema, "61"));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".permissions "
                            + "WHERE code = 'document:upload:assisted-correction' "
                            + "AND description = 'Upload Customer-provided checklist evidence for an authorized Staff-assisted correction'",
                    Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE r.code = 'LOAN_OFFICER' "
                            + "AND p.code = 'document:upload:assisted-correction'",
                    Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".role_permissions rp "
                            + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                            + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                            + "WHERE r.code <> 'LOAN_OFFICER' "
                            + "AND p.code = 'document:upload:assisted-correction'",
                    Integer.class));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV61Boundary() throws IOException {
        String migration = Files.readString(MIGRATION).replace("\r\n", "\n");
        String snapshot = Files.readString(CURRENT_SCHEMA).replace("\r\n", "\n");
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V63"));
        assertTrue(snapshot.contains(migration.trim()));
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted;
    }

    private static String schemaName(String suffix) {
        return "meridian_v61_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
