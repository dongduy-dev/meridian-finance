ALTER TABLE loan_product_policies
    ADD COLUMN interest_calculation_method VARCHAR(50),
    ADD COLUMN flat_monthly_interest_rate NUMERIC(9,6),
    ADD COLUMN fee_amount NUMERIC(19,2),
    ADD COLUMN repayment_method VARCHAR(50);

ALTER TABLE loan_product_policies
    ADD CONSTRAINT chk_loan_product_policies_interest_calculation_method
        CHECK (
            interest_calculation_method IS NULL
            OR interest_calculation_method IN ('FLAT_ORIGINAL_PRINCIPAL')
        ),
    ADD CONSTRAINT chk_loan_product_policies_flat_monthly_interest_rate_non_negative
        CHECK (
            flat_monthly_interest_rate IS NULL
            OR flat_monthly_interest_rate >= 0
        ),
    ADD CONSTRAINT chk_loan_product_policies_fee_amount_non_negative
        CHECK (
            fee_amount IS NULL
            OR fee_amount >= 0
        ),
    ADD CONSTRAINT chk_loan_product_policies_fee_amount_whole_vnd
        CHECK (
            fee_amount IS NULL
            OR fee_amount = trunc(fee_amount)
        ),
    ADD CONSTRAINT chk_loan_product_policies_repayment_method
        CHECK (
            repayment_method IS NULL
            OR repayment_method IN ('ON_SALARY_DATE', 'MONTHLY_INSTALLMENT')
        ),
    ADD CONSTRAINT chk_loan_product_policies_offer_validity_days_positive
        CHECK (offer_validity_days > 0);

CREATE TABLE loan_product_policy_terms (
    loan_product_policy_id UUID NOT NULL,
    term_months INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_loan_product_policy_terms
        PRIMARY KEY (loan_product_policy_id, term_months),

    CONSTRAINT fk_loan_product_policy_terms_policy
        FOREIGN KEY (loan_product_policy_id)
        REFERENCES loan_product_policies (id),

    CONSTRAINT chk_loan_product_policy_terms_positive
        CHECK (term_months > 0)
);

UPDATE loan_product_policies policy
SET
    interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL',
    flat_monthly_interest_rate = 0.012000,
    fee_amount = 0.00,
    repayment_method = 'ON_SALARY_DATE',
    offer_validity_days = 7,
    updated_at = CURRENT_TIMESTAMP
FROM loan_products product
WHERE policy.loan_product_id = product.id
  AND product.product_code = 'SALARY_ADVANCE'
  AND policy.policy_code = 'DEFAULT_POLICY';

UPDATE loan_product_policies policy
SET
    repayment_method = COALESCE(policy.repayment_method, policy.policy_config ->> 'repaymentMethod'),
    updated_at = CURRENT_TIMESTAMP
FROM loan_products product
WHERE policy.loan_product_id = product.id
  AND product.product_code IN ('UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN')
  AND policy.policy_code = 'DEFAULT_POLICY';

INSERT INTO loan_product_policy_terms (loan_product_policy_id, term_months)
SELECT policy.id, terms.term_months
FROM loan_product_policies policy
JOIN loan_products product
    ON product.id = policy.loan_product_id
CROSS JOIN (VALUES (1), (2), (3)) AS terms(term_months)
WHERE product.product_code = 'SALARY_ADVANCE'
  AND policy.policy_code = 'DEFAULT_POLICY'
ON CONFLICT (loan_product_policy_id, term_months) DO NOTHING;

CREATE TABLE approved_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    source_loan_product_policy_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,

    approved_principal NUMERIC(19,2) NOT NULL,
    approved_term_months INTEGER NOT NULL,
    interest_calculation_method VARCHAR(50) NOT NULL,
    flat_monthly_interest_rate NUMERIC(9,6) NOT NULL,
    total_interest NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL,
    total_repayment_amount NUMERIC(19,2) NOT NULL,
    repayment_method VARCHAR(50) NOT NULL,

    generated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    declined_at TIMESTAMP,
    expired_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_approved_offers_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),

    CONSTRAINT fk_approved_offers_source_policy
        FOREIGN KEY (source_loan_product_policy_id)
        REFERENCES loan_product_policies (id),

    CONSTRAINT uq_approved_offers_application
        UNIQUE (loan_application_id),

    CONSTRAINT chk_approved_offers_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),

    CONSTRAINT chk_approved_offers_principal_positive
        CHECK (approved_principal > 0),

    CONSTRAINT chk_approved_offers_term_positive
        CHECK (approved_term_months > 0),

    CONSTRAINT chk_approved_offers_interest_method
        CHECK (interest_calculation_method IN ('FLAT_ORIGINAL_PRINCIPAL')),

    CONSTRAINT chk_approved_offers_interest_rate_non_negative
        CHECK (flat_monthly_interest_rate >= 0),

    CONSTRAINT chk_approved_offers_money_non_negative
        CHECK (
            total_interest >= 0
            AND fee_amount >= 0
            AND total_repayment_amount >= 0
        ),

    CONSTRAINT chk_approved_offers_whole_vnd
        CHECK (
            approved_principal = trunc(approved_principal)
            AND total_interest = trunc(total_interest)
            AND fee_amount = trunc(fee_amount)
            AND total_repayment_amount = trunc(total_repayment_amount)
        ),

    CONSTRAINT chk_approved_offers_repayment_method
        CHECK (repayment_method IN ('ON_SALARY_DATE')),

    CONSTRAINT chk_approved_offers_total_repayment
        CHECK (total_repayment_amount = approved_principal + total_interest + fee_amount),

    CONSTRAINT chk_approved_offers_expiry_after_generation
        CHECK (expires_at > generated_at),

    CONSTRAINT chk_approved_offers_status_timestamps
        CHECK (
            (
                status = 'PENDING'
                AND accepted_at IS NULL
                AND declined_at IS NULL
                AND expired_at IS NULL
            )
            OR (
                status = 'ACCEPTED'
                AND accepted_at IS NOT NULL
                AND declined_at IS NULL
                AND expired_at IS NULL
            )
            OR (
                status = 'DECLINED'
                AND accepted_at IS NULL
                AND declined_at IS NOT NULL
                AND expired_at IS NULL
            )
            OR (
                status = 'EXPIRED'
                AND accepted_at IS NULL
                AND declined_at IS NULL
                AND expired_at IS NOT NULL
            )
        )
);

CREATE INDEX idx_approved_offers_source_policy_id
    ON approved_offers (source_loan_product_policy_id);

CREATE INDEX idx_approved_offers_status_expires_at
    ON approved_offers (status, expires_at, id);

CREATE TABLE approved_offer_repayment_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approved_offer_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,
    principal_due NUMERIC(19,2) NOT NULL,
    interest_due NUMERIC(19,2) NOT NULL,
    fee_due NUMERIC(19,2) NOT NULL,
    total_due NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_approved_offer_repayment_items_offer
        FOREIGN KEY (approved_offer_id)
        REFERENCES approved_offers (id),

    CONSTRAINT uq_approved_offer_repayment_items_offer_installment
        UNIQUE (approved_offer_id, installment_number),

    CONSTRAINT chk_approved_offer_repayment_items_installment_positive
        CHECK (installment_number > 0),

    CONSTRAINT chk_approved_offer_repayment_items_money_non_negative
        CHECK (
            principal_due >= 0
            AND interest_due >= 0
            AND fee_due >= 0
            AND total_due >= 0
        ),

    CONSTRAINT chk_approved_offer_repayment_items_whole_vnd
        CHECK (
            principal_due = trunc(principal_due)
            AND interest_due = trunc(interest_due)
            AND fee_due = trunc(fee_due)
            AND total_due = trunc(total_due)
        ),

    CONSTRAINT chk_approved_offer_repayment_items_total_due
        CHECK (total_due = principal_due + interest_due + fee_due)
);

CREATE INDEX idx_approved_offer_repayment_items_offer_id
    ON approved_offer_repayment_items (approved_offer_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements
        WHERE movement_type = 'RESERVATION_RELEASED'
          AND loan_application_id IS NOT NULL
        GROUP BY loan_application_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate Salary Advance reservation release movements already exist.';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_salary_advance_limit_movements_application_release
    ON salary_advance_limit_movements (loan_application_id)
    WHERE movement_type = 'RESERVATION_RELEASED'
      AND loan_application_id IS NOT NULL;

INSERT INTO permissions (id, code, description)
VALUES
    (
        '00000000-0000-0000-0000-000000000222',
        'loan:offer:respond:own',
        'Accept or decline own customer approved loan offers'
    )
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
    ON permission.code = 'loan:offer:respond:own'
WHERE role.code = 'CUSTOMER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
