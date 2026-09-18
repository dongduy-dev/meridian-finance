package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

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
class InternalUserAdministrationV55MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V55__add_internal_user_administration.sql"
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
    void upgradesV54WithZeroVersionAndPreservesExistingRoleAndAuditData() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "54");
            int rolesBefore = count(schema, "roles");
            int assignmentsBefore = count(schema, "role_assignments");
            UUID historicalId = UUID.randomUUID();
            insertAudit(schema, historicalId, "LOAN_PRODUCT", "LOAN_PRODUCT_ACTIVATED");

            assertEquals(1, migrateTo(schema, "55"));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT authorization_version FROM " + schema + ".users LIMIT 1", Long.class
            ));
            assertEquals(rolesBefore, count(schema, "roles"));
            assertEquals(assignmentsBefore, count(schema, "role_assignments"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".audit_events WHERE id = ?",
                    Integer.class,
                    historicalId
            ));
            insertAudit(schema, UUID.randomUUID(), "IDENTITY_USER", "IDENTITY_USER_STATUS_CHANGED");
            insertAudit(schema, UUID.randomUUID(), "IDENTITY_USER", "IDENTITY_USER_ROLE_ASSIGNED");
            insertAudit(schema, UUID.randomUUID(), "IDENTITY_USER", "IDENTITY_USER_ROLE_REMOVED");

            assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                    "UPDATE " + schema + ".users SET authorization_version = -1"
            ));
            assertThrows(DataIntegrityViolationException.class, () ->
                    insertAudit(schema, UUID.randomUUID(), "IDENTITY_CONFIGURATION", "ROLE_EDITED"));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV55State() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("authorization_version BIGINT NOT NULL DEFAULT 0"));
        assertTrue(migration.contains("'IDENTITY_USER'"));
        assertTrue(migration.contains("'IDENTITY_USER_ROLE_REMOVED'"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V56"));
        assertTrue(snapshot.contains("authorization_version BIGINT NOT NULL DEFAULT 0"));
        assertTrue(snapshot.contains("'IDENTITY_USER_STATUS_CHANGED'"));
    }

    private void insertAudit(String schema, UUID id, String entityType, String action) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".audit_events "
                        + "(id, operation_id, sequence_number, actor_type, actor_user_id, entity_type, "
                        + "entity_id, action, payload, occurred_at) "
                        + "VALUES (?, ?, 1, 'SYSTEM', NULL, ?, ?, ?, '{}'::jsonb, CURRENT_TIMESTAMP)",
                id, UUID.randomUUID(), entityType, UUID.randomUUID(), action
        );
    }

    private int count(String schema, String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + schema + "." + table, Integer.class);
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
        return "meridian_v55_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
