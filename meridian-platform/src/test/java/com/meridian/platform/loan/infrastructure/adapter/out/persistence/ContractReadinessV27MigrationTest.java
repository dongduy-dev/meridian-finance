package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractReadinessV27MigrationTest {
    private static String migration() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V27__harden_contract_readiness_integrity.sql"));
    }

    @Test void failsClosedBeforeAddingOneReservationPerApplicationIndex() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("HAVING COUNT(*) > 1"));
        assertTrue(sql.contains("V27 cannot enforce one RESERVED movement per Loan Application"));
        assertTrue(sql.contains("uq_salary_advance_limit_movements_application_reserved"));
        assertTrue(sql.contains("WHERE movement_type = 'RESERVED'"));
        assertTrue(sql.contains("AND loan_application_id IS NOT NULL"));
        assertFalse(sql.contains("ALTER TABLE salary_advance_limit_movements ADD CONSTRAINT"));
    }

    @Test void replacesLifecycleConstraintWithCompleteEvidenceAndChronology() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("DROP CONSTRAINT chk_loan_contracts_lifecycle"));
        assertTrue(sql.contains("acknowledged_at >= prepared_at"));
        assertTrue(sql.contains("confirmed_at >= acknowledged_at"));
        assertTrue(sql.contains("superseded_at >= prepared_at"));
        assertTrue(sql.contains("superseded_at >= acknowledged_at"));
        assertTrue(sql.contains("acknowledgment_request_id IS NULL"));
        assertTrue(sql.contains("acknowledgment_request_id IS NOT NULL"));
    }
}
