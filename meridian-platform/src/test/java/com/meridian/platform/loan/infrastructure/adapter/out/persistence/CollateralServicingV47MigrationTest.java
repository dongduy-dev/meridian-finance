package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollateralServicingV47MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V47__enable_collateral_servicing_reconciliation.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of(
            "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
    );

    @Test
    void replacesOnlyTheThreeProductAwareServicingFunctions() throws IOException {
        String sql = normalized(MIGRATION);

        assertEquals(3, occurrences(sql, "CREATE OR REPLACE FUNCTION"));
        assertTrue(sql.contains("validate_repayment_servicing_reconciliation()"));
        assertTrue(sql.contains("validate_repayment_operation_outcome_evidence("));
        assertTrue(sql.contains("validate_loan_account_closure_evidence()"));
        assertTrue(sql.contains("application_product_code = 'SALARY_ADVANCE'"));
        assertTrue(sql.contains(
                "application_product_code = 'UNSECURED_CONSUMER_LOAN'"
        ));
        assertTrue(sql.contains("application_product_code = 'COLLATERAL_LOAN'"));
        assertTrue(sql.contains("Loan product repayment is not supported"));
        assertTrue(sql.contains("Loan product closure is not supported"));
        assertTrue(sql.contains("installed_trigger_count <> 9"));
        assertTrue(sql.contains(
                "V47 preflight failed: incompatible Collateral servicing evidence exists"
        ));

        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("ALTER TABLE"));
        assertFalse(sql.contains("CREATE TRIGGER"));
        assertFalse(sql.contains("CREATE CONSTRAINT TRIGGER"));
    }

    @Test
    void currentSchemaPreservesTheExactV47Migration() throws IOException {
        String migration = normalized(MIGRATION).strip();
        String snapshot = normalized(CURRENT_SCHEMA);
        String marker = "-- Extend the common repayment, outcome, and closure reconciliation";
        int start = snapshot.lastIndexOf(marker);

        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V50"));
        assertTrue(start >= 0);
        assertEquals(migration, snapshot.substring(start).strip());
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
