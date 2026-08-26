package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class EmailVerificationV51MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V51__add_customer_registration_email_verification.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of(
            "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
    );

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
    void upgradesExistingV50UsersAsVerifiedAndCreatesTokenState() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "50");
            assertEquals(0, jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM information_schema.columns
                            WHERE table_schema = ?
                              AND table_name = 'users'
                              AND column_name = 'email_verified_at'
                            """,
                    Integer.class,
                    schema
            ));

            assertEquals(1, migrateTo(schema, "51"));

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".users WHERE email_verified_at IS NULL",
                    Integer.class
            ));
            assertNotNull(jdbcTemplate.queryForObject(
                    """
                            SELECT table_name
                            FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = 'email_verification_tokens'
                            """,
                    String.class,
                    schema
            ));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV51SecurityState() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("ADD COLUMN email_verified_at TIMESTAMP"));
        assertTrue(migration.contains("CREATE TABLE email_verification_tokens"));
        assertTrue(migration.contains("uq_email_verification_tokens_active_user"));
        assertTrue(migration.contains("chk_email_verification_tokens_digest_sha256_hex"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V52"));
        assertTrue(snapshot.contains("email_verified_at TIMESTAMP"));
        assertTrue(snapshot.contains("CREATE TABLE email_verification_tokens"));
        assertTrue(snapshot.contains("uq_email_verification_tokens_active_user"));
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
        return "meridian_v51_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
