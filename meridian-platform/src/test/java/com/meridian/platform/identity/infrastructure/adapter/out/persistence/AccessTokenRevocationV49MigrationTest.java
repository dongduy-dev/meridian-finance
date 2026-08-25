package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenRevocationV49MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V49__add_access_token_revocations.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of(
            "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
    );

    @Test
    void storesOnlyTheAccessTokenRevocationIdentityAndLifetime() throws IOException {
        String sql = normalized(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE access_token_revocations"));
        assertTrue(sql.contains("token_id UUID PRIMARY KEY"));
        assertTrue(sql.contains("revoked_at TIMESTAMP NOT NULL"));
        assertTrue(sql.contains("expires_at TIMESTAMP NOT NULL"));
        assertTrue(sql.contains("CHECK (expires_at > revoked_at)"));
        assertTrue(sql.contains("idx_access_token_revocations_expires_at"));
        assertFalse(sql.contains("access_token "));
        assertFalse(sql.contains("authorization"));
        assertFalse(sql.contains("email"));
        assertFalse(sql.contains("signature"));
    }

    @Test
    void currentSchemaIncludesV49RevocationStateInLatestSnapshot() throws IOException {
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V51"));
        assertTrue(snapshot.contains("CREATE TABLE access_token_revocations"));
        assertTrue(snapshot.contains("idx_access_token_revocations_expires_at"));
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }
}
