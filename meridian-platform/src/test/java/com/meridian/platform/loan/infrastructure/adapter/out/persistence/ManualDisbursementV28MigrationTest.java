package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualDisbursementV28MigrationTest {

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V28__create_manual_disbursement_and_loan_account_activation_foundation.sql"
        ));
    }

    @Test
    void createsGenericFoundationWithRepositoryMoneyAndTimestampConventions() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("CREATE TABLE loan_accounts"));
        assertTrue(sql.contains("CREATE TABLE manual_disbursements"));
        assertTrue(sql.contains("CREATE TABLE repayment_schedules"));
        assertTrue(sql.contains("CREATE TABLE repayment_schedule_items"));
        assertTrue(sql.contains("NUMERIC(19,2)"));
        assertTrue(sql.contains("TIMESTAMP NOT NULL"));
        assertFalse(sql.contains("NUMERIC(19,0)"));
        assertFalse(sql.contains("TIMESTAMPTZ"));
        assertFalse(sql.contains("salary_advance_limit_id UUID"));
        assertFalse(sql.contains("employee_link_id"));
        assertFalse(sql.contains("partner_company_id"));
    }

    @Test
    void enforcesFinalVersionOneWholeVndAndImmutableEvidence() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("schedule_type = 'FINAL' AND version = 1"));
        assertTrue(sql.contains("= trunc(approved_principal)"));
        assertTrue(sql.contains("= trunc(disbursed_amount)"));
        assertTrue(sql.contains("= trunc(principal_due)"));
        assertTrue(sql.contains("reject_immutable_history_row_mutation"));
        assertTrue(sql.contains("enforce_loan_account_mutation_boundary"));
        assertTrue(sql.contains("validate_repayment_schedule_reconciliation"));
        assertTrue(sql.contains("validate_loan_activation_foundation"));
        assertTrue(sql.contains(
                "schedule_row.first_due_date <> disbursement_row.first_repayment_date"
        ));
        assertTrue(sql.contains(
                "schedule_row.first_due_date <= disbursement_row.disbursement_value_date"
        ));
        assertFalse(sql.contains("make_date("));
        assertFalse(sql.contains("date_trunc('month'"));
    }

    @Test
    void strengthensConversionReferencesAndTransitionAction() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("fk_salary_advance_limit_movements_loan_account"));
        assertTrue(sql.contains("chk_salary_advance_limit_movements_disbursed_to_used"));
        assertTrue(sql.contains("uq_salary_advance_limit_movements_application_disbursed_to_used"));
        assertTrue(sql.contains("validate_salary_advance_conversion"));
        assertTrue(sql.contains("OLD.movement_type = 'DISBURSED_TO_USED'"));
        assertTrue(sql.contains(
                "TG_OP = 'UPDATE' AND NEW.movement_type = 'DISBURSED_TO_USED'"
        ));
        assertTrue(sql.contains("CONFIRM_MANUAL_DISBURSEMENT"));
        assertFalse(sql.contains("SalaryAdvanceLimitStatus"));
        assertFalse(sql.contains("status = 'ACTIVE'") && sql.contains("validate_salary_advance_conversion"));
    }

    @Test
    void failsClosedOnIncompatibleLegacyActivationAndExposureData() throws Exception {
        String sql = migration();

        assertTrue(sql.contains("existing DISBURSED Loan Application"));
        assertTrue(sql.contains("pre-existing Salary Advance movement LoanAccount references"));
        assertTrue(sql.contains("pre-existing Salary Advance conversion or repayment-release movements"));
        assertTrue(sql.contains("pre-existing used Salary Advance exposure"));
        assertTrue(sql.contains("existing Salary Advance reservation evidence"));
        assertTrue(sql.contains("existing DISBURSEMENT_PENDING Loan Application"));
    }
}
