package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UclManualVerificationV39MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V39__add_ucl_manual_verification_and_review_safety.sql"
    );

    @Test
    void containsOnlyManualVerificationEvidenceAndLifecycleVocabulary() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("reviewed_by_user_id"));
        assertTrue(sql.contains("reviewed_at"));
        assertTrue(sql.contains("assessment_note"));
        assertTrue(sql.contains("chk_ucl_verifications_decision_evidence_consistency"));
        assertTrue(sql.contains("chk_ucl_verifications_pending_evidence"));
        assertTrue(sql.contains("chk_ucl_verifications_verified_evidence"));
        assertTrue(sql.contains("START_PRODUCT_VERIFICATION"));
        assertTrue(sql.contains("COMPLETE_PRODUCT_VERIFICATION"));
        assertTrue(sql.contains("UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED"));
        assertTrue(sql.contains("UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED"));

        assertFalse(sql.contains("MONTHLY_INSTALLMENT"));
        assertFalse(sql.contains("flat_monthly_interest_rate"));
        assertFalse(sql.contains("ALTER TABLE approved_offers"));
        assertFalse(sql.contains("CREATE TABLE loan_contracts"));
        assertFalse(sql.contains("ALTER TABLE loan_contracts"));
        assertFalse(sql.contains("CREATE TABLE repayment_transactions"));
        assertFalse(sql.contains("ALTER TABLE repayment_transactions"));
        assertFalse(sql.contains("CREATE TABLE loan_correction"));
        assertFalse(sql.contains("ALTER TABLE loan_correction"));
    }
}
