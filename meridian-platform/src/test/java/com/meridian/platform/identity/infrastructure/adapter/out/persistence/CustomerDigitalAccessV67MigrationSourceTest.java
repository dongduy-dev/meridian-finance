package com.meridian.platform.identity.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerDigitalAccessV67MigrationSourceTest {
    @Test
    void preflightsAndEnforcesOneUserPerCustomerWithControlledAuditAction() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V67__link_existing_customer_digital_access.sql"));
        String snapshot = Files.readString(Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql"));
        assertTrue(migration.contains("HAVING COUNT(*) > 1"));
        assertTrue(migration.contains("ADD CONSTRAINT uq_users_customer_id UNIQUE (customer_id)"));
        assertTrue(migration.contains("DROP INDEX uq_users_customer_id_present"));
        assertTrue(migration.contains("'IDENTITY_CUSTOMER_DIGITAL_ACCESS_ENABLED'"));
        assertFalse(migration.contains("DROP INDEX idx_users_customer_id"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V68"));
        assertTrue(snapshot.replace("\r\n", "\n").endsWith(migration.replace("\r\n", "\n")));
    }
}
