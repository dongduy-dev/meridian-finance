package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UclServicingV42MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V42__make_servicing_reconciliation_product_aware.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of(
            "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
    );

    @Test
    void redefinesOnlyCurrentProductAwareServicingReconciliation() throws IOException {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");

        assertTrue(sql.contains("CREATE OR REPLACE FUNCTION "
                + "validate_repayment_servicing_reconciliation()"));
        assertTrue(sql.contains("CREATE OR REPLACE FUNCTION "
                + "validate_repayment_operation_outcome_evidence("));
        assertTrue(sql.contains("CREATE OR REPLACE FUNCTION "
                + "validate_loan_account_closure_evidence()"));
        assertTrue(sql.contains("application_product_code = 'SALARY_ADVANCE'"));
        assertTrue(sql.contains("application_product_code = "
                + "'UNSECURED_CONSUMER_LOAN'"));
        assertTrue(sql.contains("outcome_row.principal_released <> 0"));
        assertTrue(sql.contains("Loan product repayment is not supported"));
        assertTrue(sql.contains("Loan product closure is not supported"));

        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("ALTER TABLE"));
        assertFalse(sql.contains("CREATE TRIGGER"));
        assertFalse(sql.contains("unsecured_consumer_loan_limit"));
        assertFalse(sql.contains("unsecured_consumer_loan_movement"));
    }

    @Test
    void currentSchemaPreservesExactV42FunctionDefinitions() throws IOException {
        String migration = Files.readString(MIGRATION)
                .replace("\r\n", "\n").strip();
        String snapshot = Files.readString(CURRENT_SCHEMA)
                .replace("\r\n", "\n");
        String marker = "-- Make the current repayment, outcome, and closure reconciliation";
        int v42Start = snapshot.lastIndexOf(marker);
        int v43Start = snapshot.indexOf(
                "-- UCL verification cycles, correction integrity, and product-aware cancellation evidence",
                v42Start
        );

        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V44"));
        assertTrue(v42Start >= 0);
        assertTrue(v43Start > v42Start);
        assertEquals(migration, snapshot.substring(v42Start, v43Start).strip());
    }
}
