package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffCorrectionV24MigrationTest {

    @Test
    void migrationAddsOnlyApprovedStaffPermissionsAndScopeAuthority() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V24__enable_staff_and_mixed_correction_workflows.sql")) {
            if (stream == null) {
                throw new IOException("V24 migration resource was not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("'loan:correction:staff'"));
        assertTrue(sql.contains("'document:upload:staff'"));
        assertTrue(sql.contains("'document:waive'"));
        assertTrue(sql.contains("role.code = 'LOAN_OFFICER'"));
        assertTrue(sql.contains("role.code = 'BACK_OFFICE_ADMIN'"));
        assertTrue(sql.contains("responsible_party IN ('CUSTOMER', 'STAFF')"));
        assertTrue(sql.contains("idx_loan_correction_tasks_staff_queue"));
        assertFalse(sql.contains("EITHER"));
        assertFalse(sql.contains("REQUESTED_AMOUNT"));
        assertFalse(sql.contains("REQUESTED_TERM"));
    }
}
