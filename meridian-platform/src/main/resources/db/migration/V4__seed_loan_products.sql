INSERT INTO loan_products
(
    product_code,
    product_type,
    name,
    active,
    min_amount,
    max_amount
)
VALUES
    (
        'SALARY_ADVANCE',
        'SALARY_BASED',
        'Salary Advance',
        TRUE,
        500000,
        10000000
    ),
    (
        'UNSECURED_CONSUMER_LOAN',
        'UNSECURED',
        'Unsecured Consumer Loan',
        TRUE,
        1000000,
        50000000
    ),
    (
        'COLLATERAL_LOAN',
        'SECURED',
        'Collateral Loan',
        TRUE,
        5000000,
        200000000
    )
ON CONFLICT (product_code) DO NOTHING; -- reset/re-run during development, it avoids duplicate product errors



INSERT INTO loan_product_policies
(
    loan_product_id,
    policy_code,
    policy_config,
    offer_validity_days,
    active
)
SELECT
    id,
    'DEFAULT_POLICY',
    '{
      "requiresEmployeeLink": true,
      "requiresActiveLimit": true,
      "limitReservationOnSubmit": true,
      "repaymentMethod": "ON_SALARY_DATE"
    }'::jsonb,
    7,
    TRUE
FROM loan_products
WHERE product_code = 'SALARY_ADVANCE'
ON CONFLICT (loan_product_id, policy_code) DO NOTHING;


INSERT INTO loan_product_policies
(
    loan_product_id,
    policy_code,
    policy_config,
    offer_validity_days,
    active
)
SELECT
    id,
    'DEFAULT_POLICY',
    '{
      "requiresIncomeDocuments": true,
      "requiresEmploymentDocuments": true,
      "manualReviewRequired": true,
      "repaymentMethod": "MONTHLY_INSTALLMENT"
    }'::jsonb,
    7,
    TRUE
FROM loan_products
WHERE product_code = 'UNSECURED_CONSUMER_LOAN'
ON CONFLICT (loan_product_id, policy_code) DO NOTHING;


INSERT INTO loan_product_policies
(
    loan_product_id,
    policy_code,
    policy_config,
    offer_validity_days,
    active
)
SELECT
    id,
    'DEFAULT_POLICY',
    '{
      "requiresCollateral": true,
      "requiresOwnershipDocument": true,
      "manualCollateralReviewRequired": true,
      "repaymentMethod": "MONTHLY_INSTALLMENT"
    }'::jsonb,
    7,
    TRUE
FROM loan_products
WHERE product_code = 'COLLATERAL_LOAN'
ON CONFLICT (loan_product_id, policy_code) DO NOTHING;