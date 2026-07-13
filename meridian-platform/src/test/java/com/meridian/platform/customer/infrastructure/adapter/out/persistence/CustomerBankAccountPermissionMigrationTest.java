package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerBankAccountPermissionMigrationTest {

    private static String readMigration() throws IOException {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V20__add_customer_bank_account_permissions_and_audit_actions.sql"
        )).replace("\r\n", "\n");
    }

    @Test
    void v20SeedsOwnBankAccountPermissionsAndAllowsBankAccountAudit() throws IOException {
        String migration = readMigration();

        assertTrue(migration.contains("customer:bank-account:read:own"));
        assertTrue(migration.contains("customer:bank-account:write:own"));
        assertTrue(migration.contains("WHERE role.code = 'CUSTOMER'"));
        assertTrue(migration.contains("'CUSTOMER_BANK_ACCOUNT'"));
        assertTrue(migration.contains("'CUSTOMER_BANK_ACCOUNT_ADDED'"));
        assertTrue(migration.contains("'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY'"));
        assertTrue(migration.contains("'CUSTOMER_BANK_ACCOUNT_DEACTIVATED'"));
    }
}
