package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollateralManualVerificationV45MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V45__add_collateral_manual_verification_and_correction_safety.sql"
    );

    @Test
    void addsNarrowCollateralCycleAndDocumentCorrectionIntegrity() throws IOException {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");

        assertTrue(sql.contains("verification_sequence"));
        assertTrue(sql.contains("source_correction_request_id"));
        assertTrue(sql.contains("reviewed_by_user_id"));
        assertTrue(sql.contains("assessment_note VARCHAR(2000)"));
        assertTrue(sql.contains("uq_collateral_verifications_application_sequence"));
        assertTrue(sql.contains("uq_collateral_verifications_source_correction"));
        assertTrue(sql.contains("trg_collateral_verification_cycles_immutable"));
        assertTrue(sql.contains("trg_collateral_verification_cycles_reconcile_source"));
        assertTrue(sql.contains("COLLATERAL_OWNERSHIP_EVIDENCE"));
        assertTrue(sql.contains("COLLATERAL_LOAN_VERIFICATION_STARTED"));
        assertTrue(sql.contains("COLLATERAL_LOAN_VERIFICATION_COMPLETED"));
        assertTrue(sql.contains("DOCUMENT_REPLACEMENT"));
        assertTrue(sql.contains("DOCUMENT_REVIEW"));

        assertFalse(sql.contains("CREATE TABLE collateral_loan_verifications"));
        assertFalse(sql.contains("ALTER TABLE collaterals"));
        assertFalse(sql.contains("loan_to_value"));
        assertFalse(sql.contains("interest_rate"));
        assertFalse(sql.contains("CREATE TABLE approved_offers"));
        assertFalse(sql.contains("CREATE TABLE collateral_valuations"));
    }
}
