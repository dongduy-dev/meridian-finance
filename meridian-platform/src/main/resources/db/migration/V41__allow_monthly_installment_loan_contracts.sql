ALTER TABLE loan_contracts
    DROP CONSTRAINT chk_loan_contracts_terms;

ALTER TABLE loan_contracts
    ADD CONSTRAINT chk_loan_contracts_terms CHECK (
        approved_principal > 0 AND approved_term_months > 0
        AND flat_monthly_interest_rate >= 0 AND total_interest >= 0 AND fee_amount >= 0
        AND total_repayment_amount = approved_principal + total_interest + fee_amount
        AND approved_principal = trunc(approved_principal)
        AND total_interest = trunc(total_interest)
        AND fee_amount = trunc(fee_amount)
        AND total_repayment_amount = trunc(total_repayment_amount)
        AND interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL'
        AND repayment_method IN ('ON_SALARY_DATE', 'MONTHLY_INSTALLMENT')
    );
