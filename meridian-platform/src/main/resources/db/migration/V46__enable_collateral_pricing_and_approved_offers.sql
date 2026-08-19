DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM loan_product_policies policy
        JOIN loan_products product ON product.id = policy.loan_product_id
        WHERE product.product_code = 'COLLATERAL_LOAN'
          AND policy.policy_code = 'DEFAULT_POLICY'
          AND policy.active = TRUE
    ) THEN
        RAISE EXCEPTION 'V46 preflight failed: active Collateral Loan default policy is missing';
    END IF;
END;
$$;

UPDATE loan_product_policies policy
SET
    interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL',
    flat_monthly_interest_rate = 0.015000,
    fee_amount = 0.00,
    repayment_method = 'MONTHLY_INSTALLMENT',
    offer_validity_days = 7,
    updated_at = CURRENT_TIMESTAMP
FROM loan_products product
WHERE policy.loan_product_id = product.id
  AND product.product_code = 'COLLATERAL_LOAN'
  AND policy.policy_code = 'DEFAULT_POLICY'
  AND policy.active = TRUE;

DELETE FROM loan_product_policy_terms term
USING loan_product_policies policy, loan_products product
WHERE term.loan_product_policy_id = policy.id
  AND policy.loan_product_id = product.id
  AND product.product_code = 'COLLATERAL_LOAN'
  AND policy.policy_code = 'DEFAULT_POLICY'
  AND term.term_months NOT IN (6, 12, 18, 24);

INSERT INTO loan_product_policy_terms (loan_product_policy_id, term_months)
SELECT policy.id, terms.term_months
FROM loan_product_policies policy
JOIN loan_products product ON product.id = policy.loan_product_id
CROSS JOIN (VALUES (6), (12), (18), (24)) AS terms(term_months)
WHERE product.product_code = 'COLLATERAL_LOAN'
  AND policy.policy_code = 'DEFAULT_POLICY'
  AND policy.active = TRUE
ON CONFLICT (loan_product_policy_id, term_months) DO NOTHING;
