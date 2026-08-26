package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountLockoutV50MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V50__add_user_account_lockout_state.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of(
            "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
    );

    @Test
    void migrationAddsMinimalUserOwnedLockoutState() throws IOException {
        String sql = normalized(MIGRATION);

        assertTrue(sql.contains("ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("ADD COLUMN locked_until TIMESTAMP"));
        assertTrue(sql.contains("chk_users_failed_login_attempts_non_negative"));
        assertTrue(sql.contains("CHECK (failed_login_attempts >= 0)"));
    }

    @Test
    void currentSchemaIncludesV50AccountLockoutState() throws IOException {
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V52"));
        assertTrue(snapshot.contains("failed_login_attempts INTEGER NOT NULL DEFAULT 0"));
        assertTrue(snapshot.contains("locked_until TIMESTAMP"));
        assertTrue(snapshot.contains("chk_users_failed_login_attempts_non_negative"));
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }
}
