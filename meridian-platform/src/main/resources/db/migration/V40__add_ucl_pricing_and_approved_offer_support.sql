UPDATE loan_product_policies policy
SET
    interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL',
    flat_monthly_interest_rate = 0.018000,
    fee_amount = 0.00,
    repayment_method = 'MONTHLY_INSTALLMENT',
    offer_validity_days = 7,
    updated_at = CURRENT_TIMESTAMP
FROM loan_products product
WHERE policy.loan_product_id = product.id
  AND product.product_code = 'UNSECURED_CONSUMER_LOAN'
  AND policy.policy_code = 'DEFAULT_POLICY';

INSERT INTO loan_product_policy_terms (loan_product_policy_id, term_months)
SELECT policy.id, terms.term_months
FROM loan_product_policies policy
JOIN loan_products product
    ON product.id = policy.loan_product_id
CROSS JOIN (VALUES (3), (6), (9), (12)) AS terms(term_months)
WHERE product.product_code = 'UNSECURED_CONSUMER_LOAN'
  AND policy.policy_code = 'DEFAULT_POLICY'
ON CONFLICT (loan_product_policy_id, term_months) DO NOTHING;

ALTER TABLE approved_offers
    DROP CONSTRAINT chk_approved_offers_repayment_method,
    ADD CONSTRAINT chk_approved_offers_repayment_method
        CHECK (repayment_method IN ('ON_SALARY_DATE', 'MONTHLY_INSTALLMENT'));
