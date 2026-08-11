package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UclOriginationV38MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V38__add_ucl_origination_and_evidence_foundation.sql"
    );

    @Test
    void containsOnlyTheUclOriginationAndEvidenceFoundation() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE unsecured_consumer_loan_verifications"));
        assertTrue(sql.contains("PENDING_MANUAL_REVIEW"));
        assertTrue(sql.contains("INCOME_PROOF"));
        assertTrue(sql.contains("BANK_STATEMENT"));
        assertTrue(sql.contains("EMPLOYMENT_PROOF"));
        assertTrue(sql.contains("UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED"));
        assertFalse(sql.contains("flat_monthly_interest_rate"));
        assertFalse(sql.contains("CREATE TABLE approved_offers"));
        assertFalse(sql.contains("ALTER TABLE approved_offers"));
        assertFalse(sql.contains("CREATE TABLE loan_contracts"));
        assertFalse(sql.contains("ALTER TABLE loan_contracts"));
        assertFalse(sql.contains("CREATE TABLE repayment_transactions"));
        assertFalse(sql.contains("ALTER TABLE repayment_transactions"));
    }
}
