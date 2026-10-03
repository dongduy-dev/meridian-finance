package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalUserCreationV66MigrationSourceTest {
    private static final Path PREVIOUS = Path.of("src/main/resources/db/migration/V63__add_partner_eligibility_manual_reviews.sql");
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V66__add_internal_user_creation_audit_action.sql");
    private static final Path SNAPSHOT = Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql");

    @Test
    void expandsOnlyTheAuditActionVocabularyAndMatchesTheSchemaSnapshot() throws IOException {
        String previous = normalized(PREVIOUS);
        String migration = normalized(MIGRATION);
        String snapshot = normalized(SNAPSHOT);
        String previousActions = previous.substring(previous.lastIndexOf("ADD CONSTRAINT chk_audit_events_action CHECK (action IN ("));
        String newActions = migration.substring(migration.lastIndexOf("ADD CONSTRAINT chk_audit_events_action CHECK (action IN ("));
        Set<String> expected = actions(previousActions);
        expected.add("IDENTITY_USER_CREATED");
        assertEquals(expected, actions(newActions));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V69"));
        assertTrue(snapshot.contains(migration));
    }

    private static Set<String> actions(String sql) {
        var matcher = Pattern.compile("'([A-Z_]+)'").matcher(sql);
        Set<String> result = new HashSet<>();
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }
}
