package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UclContractV41MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V41__allow_monthly_installment_loan_contracts.sql"
    );

    @Test
    void widensOnlyTheExistingContractTermsConstraint() throws IOException {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");

        assertTrue(sql.contains("DROP CONSTRAINT chk_loan_contracts_terms"));
        assertTrue(sql.contains("ADD CONSTRAINT chk_loan_contracts_terms CHECK"));
        assertTrue(sql.contains("approved_principal > 0 AND approved_term_months > 0"));
        assertTrue(sql.contains("total_repayment_amount = approved_principal + total_interest + fee_amount"));
        assertTrue(sql.contains("approved_principal = trunc(approved_principal)"));
        assertTrue(sql.contains("interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL'"));
        assertTrue(sql.contains("repayment_method IN ('ON_SALARY_DATE', 'MONTHLY_INSTALLMENT')"));

        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("approved_offers"));
        assertFalse(sql.contains("salary_advance"));
        assertFalse(sql.contains("repayment_transactions"));
    }
}
