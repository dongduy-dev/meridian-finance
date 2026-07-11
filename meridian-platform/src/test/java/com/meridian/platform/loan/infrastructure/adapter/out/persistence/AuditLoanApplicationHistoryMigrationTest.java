package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLoanApplicationHistoryMigrationTest {

    @Test
    void createsAppendOnlyAuditAndLoanApplicationTransitionTables() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V17__create_audit_and_loan_application_history.sql"))
                .replace("\r\n", "\n");

        assertTrue(migration.contains("CREATE TABLE loan_application_status_transitions"));
        assertTrue(migration.contains("CREATE TABLE audit_events"));
        assertTrue(migration.contains("UNIQUE (operation_id, sequence_number)"));
        assertTrue(migration.contains("chk_loan_application_status_transitions_initial_submission"));
        assertTrue(migration.contains("from_status IS NULL AND action = 'APPLICATION_SUBMITTED' AND to_status = 'SUBMITTED'"));
        assertTrue(migration.contains("chk_loan_application_status_transitions_action"));
        assertTrue(migration.contains("jsonb_typeof(payload) = 'object'"));
        assertTrue(migration.contains("trg_loan_application_status_transitions_append_only"));
        assertTrue(migration.contains("trg_audit_events_append_only"));
        assertFalse(migration.contains("idx_loan_application_status_transitions_operation_sequence"));
        assertFalse(migration.contains("idx_audit_events_operation_sequence"));
    }
}
