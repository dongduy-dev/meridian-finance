package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalaryAdvanceApprovedOfferMigrationTest {

    @Test
    void v16ContainsOfferPolicyOfferTablesAndReleaseUniqueness() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V16__create_salary_advance_approved_offers.sql"
        ));

        assertTrue(migration.contains("interest_calculation_method"));
        assertTrue(migration.contains("flat_monthly_interest_rate"));
        assertTrue(migration.contains("CREATE TABLE loan_product_policy_terms"));
        assertTrue(migration.contains("CREATE TABLE approved_offers"));
        assertTrue(migration.contains("CREATE TABLE approved_offer_repayment_items"));
        assertTrue(migration.contains("uq_salary_advance_limit_movements_application_release"));
        assertTrue(migration.contains("loan:offer:respond:own"));
    }

    @Test
    void v16LeavesUndefinedUclAndCollateralPricingNull() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V16__create_salary_advance_approved_offers.sql"
        ));

        int otherProductUpdate = migration.indexOf("UPDATE loan_product_policies policy\nSET\n    repayment_method");
        String otherProductBlock = migration.substring(
                otherProductUpdate,
                migration.indexOf("INSERT INTO loan_product_policy_terms")
        );

        assertTrue(otherProductBlock.contains("repayment_method"));
        assertFalse(otherProductBlock.contains("flat_monthly_interest_rate ="));
        assertFalse(otherProductBlock.contains("interest_calculation_method ="));
        assertFalse(otherProductBlock.contains("fee_amount ="));
    }
}
