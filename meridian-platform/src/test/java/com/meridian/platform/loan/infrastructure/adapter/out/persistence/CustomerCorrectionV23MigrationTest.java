package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerCorrectionV23MigrationTest {

    @Test
    void migrationDefinesReviewCyclesCorrectionAuthorityAndNoDecisionCycleDuplication() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V23__add_customer_correction_and_review_cycles.sql")) {
            if (stream == null) {
                throw new IOException("V23 migration resource was not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("CREATE TABLE loan_application_review_cycles"));
        assertTrue(sql.contains("CREATE TABLE loan_correction_requests"));
        assertTrue(sql.contains("CREATE TABLE loan_correction_tasks"));
        assertTrue(sql.contains("UNIQUE NULLS NOT DISTINCT"));
        assertTrue(sql.contains("uq_loan_review_cycles_active_application"));
        assertTrue(sql.contains("uq_review_recommendations_cycle"));
        assertTrue(sql.contains("uq_approval_decisions_recommendation"));
        assertTrue(sql.contains("fk_review_recommendations_cycle_application"));
        assertTrue(sql.contains("V23 cannot backfill"));
        assertFalse(sql.matches("(?s).*ALTER TABLE approval_decisions.*ADD COLUMN review_cycle_id.*"));
        assertFalse(sql.contains("APPLICATION_TERMS"));
        assertFalse(sql.contains("VERIFICATION_PENDING"));
    }
}
