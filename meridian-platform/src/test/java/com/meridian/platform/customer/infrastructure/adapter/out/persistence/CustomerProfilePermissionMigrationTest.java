package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerProfilePermissionMigrationTest {

    private static String readMigration() throws IOException {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V19__add_customer_profile_permissions_and_audit_actions.sql"
        )).replace("\r\n", "\n");
    }

    @Test
    void v19SeedsOwnProfilePermissionsAndAllowsCustomerProfileAudit() throws IOException {
        String migration = readMigration();

        assertTrue(migration.contains("customer:profile:read:own"));
        assertTrue(migration.contains("customer:profile:write:own"));
        assertTrue(migration.contains("WHERE role.code = 'CUSTOMER'"));
        assertTrue(migration.contains("'CUSTOMER'"));
        assertTrue(migration.contains("'CUSTOMER_PROFILE_CREATED'"));
        assertTrue(migration.contains("'CUSTOMER_PROFILE_UPDATED'"));
        assertTrue(migration.contains("'CUSTOMER_PROFILE_COMPLETED'"));
    }
}
