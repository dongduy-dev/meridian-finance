package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LoanContractV25MigrationTest {
    private static String migration() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V25__create_immutable_loan_contract_foundation.sql"));
    }

    @Test void createsOnlyImmutableContractFoundationWithOwnershipAndIdempotencyConstraints() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("CREATE TABLE loan_contracts"));
        assertTrue(sql.contains("CREATE TABLE loan_contract_repayment_items"));
        assertTrue(sql.contains("fk_loan_contracts_offer_application"));
        assertTrue(sql.contains("fk_loan_contracts_source_account_customer"));
        assertTrue(sql.contains("uq_loan_contracts_current_application"));
        assertTrue(sql.contains("uq_loan_contracts_preparation_request"));
        assertTrue(sql.contains("uq_loan_contracts_acknowledgment_request"));
        assertTrue(sql.contains("uq_loan_contracts_confirmation_request"));
        assertTrue(sql.contains("enforce_loan_contract_immutability"));
        assertTrue(sql.contains("validate_loan_contract_repayment_reconciliation"));
        assertTrue(sql.contains("CONFIRM_DISBURSEMENT_READINESS"));
    }

    @Test void storesOnlyPurposeProtectedAccountAndDoesNotIntroduceIncrementTwoSchema() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("protected_account_number BYTEA"));
        assertTrue(sql.contains("protection_key_id"));
        assertTrue(sql.contains("DISBURSEMENT_ACCOUNT_REFRESH"));
        assertFalse(sql.contains("account_number_fingerprint"));
        assertFalse(sql.contains("account_number_ciphertext"));
        assertFalse(sql.contains("loan_contract_document_evidence"));
        assertFalse(sql.contains("signed_contract"));
        assertFalse(sql.contains("loan_accounts"));
        assertFalse(sql.contains("disbursements"));
        assertFalse(sql.contains("INSERT INTO permissions"));
    }
}
