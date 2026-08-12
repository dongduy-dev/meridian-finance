package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UclCorrectionSafetyV43MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V43__complete_ucl_correction_and_origination_safety.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of(
            "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
    );

    @Test
    void addsSequencedVerificationCorrectionAndProductAwareCancellationIntegrity()
            throws IOException {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");

        assertTrue(sql.contains("verification_sequence"));
        assertTrue(sql.contains("source_correction_request_id"));
        assertTrue(sql.contains("uq_ucl_verifications_application_sequence"));
        assertTrue(sql.contains("trg_ucl_verification_cycles_immutable"));
        assertTrue(sql.contains("trg_ucl_verification_cycles_reconcile_source"));
        assertTrue(sql.contains("COMPLETE_PRODUCT_VERIFICATION"));
        assertTrue(sql.contains("INCOME_PROOF"));
        assertTrue(sql.contains("BANK_STATEMENT"));
        assertTrue(sql.contains("EMPLOYMENT_PROOF"));
        assertTrue(sql.contains("application_row.product_code = 'UNSECURED_CONSUMER_LOAN'"));
        assertTrue(sql.contains("UCL cancellation must have no Salary exposure effect"));
        assertTrue(sql.contains("Salary Advance cancellation requires an exact reservation release"));

        assertFalse(sql.contains("ALTER TABLE loan_applications\n    ADD COLUMN"));
        assertFalse(sql.contains("CREATE TABLE unsecured_consumer_loan_verifications"));
        assertFalse(sql.contains("unsecured_consumer_loan_limit"));
        assertFalse(sql.contains("unsecured_consumer_loan_movement"));
    }

    @Test
    void currentSchemaEndsWithExactV43Definition() throws IOException {
        String migration = Files.readString(MIGRATION)
                .replace("\r\n", "\n").strip();
        String snapshot = Files.readString(CURRENT_SCHEMA)
                .replace("\r\n", "\n").strip();

        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V43"));
        assertTrue(snapshot.endsWith(migration));
    }
}
