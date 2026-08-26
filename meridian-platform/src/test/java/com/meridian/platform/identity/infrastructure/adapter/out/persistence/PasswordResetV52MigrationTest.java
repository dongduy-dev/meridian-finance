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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class PasswordResetV52MigrationTest {

    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V52__add_password_reset_tokens.sql"
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
    void upgradesExistingV51StateAndCreatesPasswordResetTokenState() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "51");
            assertEquals(0, jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = 'password_reset_tokens'
                            """,
                    Integer.class,
                    schema
            ));

            assertEquals(1, migrateTo(schema, "52"));
            assertNotNull(jdbcTemplate.queryForObject(
                    """
                            SELECT table_name
                            FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = 'password_reset_tokens'
                            """,
                    String.class,
                    schema
            ));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void v52RejectsInvalidDigestExpiryTerminalAndMultipleActiveState() {
        LocalDateTime issuedAt = LocalDateTime.parse("2026-08-26T00:00:00");
        LocalDateTime expiresAt = issuedAt.plusMinutes(30);

        assertThrows(DataIntegrityViolationException.class, () -> insert(
                UUID.randomUUID(), "not-a-digest", issuedAt, expiresAt, null, null
        ));
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                UUID.randomUUID(), "a".repeat(64), issuedAt, issuedAt, null, null
        ));
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                UUID.randomUUID(), "b".repeat(64), issuedAt, expiresAt, issuedAt, issuedAt
        ));
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                UUID.randomUUID(), "e".repeat(64), issuedAt, expiresAt, issuedAt.minusSeconds(1), null
        ));
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                UUID.randomUUID(), "f".repeat(64), issuedAt, expiresAt, null, issuedAt.minusSeconds(1)
        ));

        insert(UUID.randomUUID(), "c".repeat(64), issuedAt, expiresAt, null, null);
        assertThrows(DataIntegrityViolationException.class, () -> insert(
                UUID.randomUUID(), "d".repeat(64), issuedAt, expiresAt, null, null
        ));
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV52SecurityState() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("CREATE TABLE password_reset_tokens"));
        assertTrue(migration.contains("uq_password_reset_tokens_active_user"));
        assertTrue(migration.contains("chk_password_reset_tokens_digest_sha256_hex"));
        assertTrue(migration.contains("chk_password_reset_tokens_single_terminal_state"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V52"));
        assertTrue(snapshot.contains("CREATE TABLE password_reset_tokens"));
        assertTrue(snapshot.contains("uq_password_reset_tokens_active_user"));
    }

    private void insert(
            UUID id,
            String digest,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt,
            LocalDateTime consumedAt,
            LocalDateTime revokedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO password_reset_tokens (
                            id, user_id, token_digest, issued_at, expires_at, consumed_at, revoked_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                USER_ID,
                digest,
                issuedAt,
                expiresAt,
                consumedAt,
                revokedAt
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
        return "meridian_v52_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
