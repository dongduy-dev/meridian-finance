package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UclPricingV40MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V40__add_ucl_pricing_and_approved_offer_support.sql"
    );

    @Test
    void definesOnlyUclPricingTermsAndApprovedOfferRepaymentSupport() throws IOException {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");

        assertTrue(sql.contains("product.product_code = 'UNSECURED_CONSUMER_LOAN'"));
        assertTrue(sql.contains("interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL'"));
        assertTrue(sql.contains("flat_monthly_interest_rate = 0.018000"));
        assertTrue(sql.contains("fee_amount = 0.00"));
        assertTrue(sql.contains("repayment_method = 'MONTHLY_INSTALLMENT'"));
        assertTrue(sql.contains("offer_validity_days = 7"));
        assertTrue(sql.contains("(VALUES (3), (6), (9), (12))"));
        assertTrue(sql.contains("DROP CONSTRAINT chk_approved_offers_repayment_method"));
        assertTrue(sql.contains("'ON_SALARY_DATE', 'MONTHLY_INSTALLMENT'"));

        assertFalse(sql.contains("ALTER TABLE loan_contracts"));
        assertFalse(sql.contains("CREATE TABLE loan_contracts"));
        assertFalse(sql.contains("manual_disbursements"));
        assertFalse(sql.contains("repayment_transactions"));
        assertFalse(sql.contains("salary_advance_limit_movements"));
        assertFalse(sql.contains("loan_correction"));
    }
}
