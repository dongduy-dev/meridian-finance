package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerFoundationMigrationTest {

    private static String readMigration() throws IOException {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V18__create_customer_profile_and_bank_account_foundation.sql"
        )).replace("\r\n", "\n");
    }

    @Test
    void v18CreatesCustomerProfileAndBankAccountFoundation() throws IOException {
        String migration = readMigration();

        assertTrue(migration.contains("CREATE SEQUENCE customer_number_seq"));
        assertTrue(migration.contains("CREATE TABLE customers"));
        assertTrue(migration.contains("CREATE TABLE customer_profiles"));
        assertTrue(migration.contains("CREATE TABLE customer_bank_accounts"));
        assertTrue(migration.contains("identity_reference_ciphertext"));
        assertTrue(migration.contains("identity_reference_fingerprint"));
        assertTrue(migration.contains("account_number_ciphertext"));
        assertTrue(migration.contains("account_number_fingerprint"));
        assertTrue(migration.contains("uq_customer_bank_accounts_primary_active"));
        assertTrue(migration.contains("uq_customer_bank_accounts_active_fingerprint"));
    }

    @Test
    void v18BackfillsAllExistingCustomerReferencesBeforeAddingForeignKeys() throws IOException {
        String migration = readMigration();

        int backfillPosition = migration.indexOf("WITH referenced_customers AS");
        int firstForeignKeyPosition = migration.indexOf("ALTER TABLE users");

        assertTrue(backfillPosition > 0);
        assertTrue(firstForeignKeyPosition > backfillPosition);
        assertTrue(migration.contains("SELECT customer_id FROM users WHERE customer_id IS NOT NULL"));
        assertTrue(migration.contains("SELECT customer_id FROM loan_applications WHERE customer_id IS NOT NULL"));
        assertTrue(migration.contains("SELECT customer_id FROM customer_partner_employee_links WHERE customer_id IS NOT NULL"));
        assertTrue(migration.contains("SELECT customer_id FROM salary_advance_limits WHERE customer_id IS NOT NULL"));
        assertTrue(migration.contains("SELECT customer_id FROM salary_advance_verifications WHERE customer_id IS NOT NULL"));
        assertTrue(migration.contains("fk_users_customer"));
        assertTrue(migration.contains("fk_loan_applications_customer"));
        assertTrue(migration.contains("fk_customer_partner_employee_links_customer"));
        assertTrue(migration.contains("fk_salary_advance_limits_customer"));
        assertTrue(migration.contains("fk_salary_advance_verifications_customer"));
    }

    @Test
    void v18KeepsUsersAsTheAuthoritativeIdentityCustomerMapping() throws IOException {
        String migration = readMigration();

        assertTrue(migration.contains("CREATE UNIQUE INDEX uq_users_customer_id_present"));
        assertFalse(migration.contains("user_id UUID"));
        assertFalse(migration.contains("customer_bank_infos"));
        assertFalse(migration.contains("bank_account_infos"));
    }
}