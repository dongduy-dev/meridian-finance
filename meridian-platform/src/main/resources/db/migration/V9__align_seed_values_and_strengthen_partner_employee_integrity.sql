UPDATE loan_products
SET
    min_amount = 500000.00,
    max_amount = 20000000.00,
    updated_at = CURRENT_TIMESTAMP
WHERE product_code = 'SALARY_ADVANCE';

UPDATE loan_products
SET
    min_amount = 2000000.00,
    max_amount = 50000000.00,
    updated_at = CURRENT_TIMESTAMP
WHERE product_code = 'UNSECURED_CONSUMER_LOAN';

UPDATE loan_products
SET
    min_amount = 5000000.00,
    max_amount = 100000000.00,
    updated_at = CURRENT_TIMESTAMP
WHERE product_code = 'COLLATERAL_LOAN';

DO $$
BEGIN
    IF (
        SELECT COUNT(*)
        FROM loan_products
        WHERE
            (product_code = 'SALARY_ADVANCE'
                AND min_amount = 500000.00
                AND max_amount = 20000000.00)
            OR (product_code = 'UNSECURED_CONSUMER_LOAN'
                AND min_amount = 2000000.00
                AND max_amount = 50000000.00)
            OR (product_code = 'COLLATERAL_LOAN'
                AND min_amount = 5000000.00
                AND max_amount = 100000000.00)
    ) <> 3 THEN
        RAISE EXCEPTION
            'Loan product seed values were not aligned to expected min/max amounts.';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM partner_employees pe
        JOIN partner_employee_import_batches peib
            ON peib.id = pe.import_batch_id
        WHERE peib.partner_company_id <> pe.partner_company_id
    ) THEN
        RAISE EXCEPTION
            'Cannot add partner employee batch/company consistency constraint: mismatched partner_company_id found.';
    END IF;
END $$;

ALTER TABLE partner_employee_import_batches
    ADD CONSTRAINT uq_partner_employee_import_batches_id_partner_company
    UNIQUE (id, partner_company_id);

ALTER TABLE partner_employees
    ADD CONSTRAINT fk_partner_employees_import_batch_partner_company
    FOREIGN KEY (import_batch_id, partner_company_id)
    REFERENCES partner_employee_import_batches (id, partner_company_id);

CREATE INDEX idx_partner_employee_import_batches_company_status_month_desc
    ON partner_employee_import_batches (partner_company_id, status, effective_month DESC);

CREATE INDEX idx_partner_employees_company_batch_identity_employee_code
    ON partner_employees (partner_company_id, import_batch_id, identity_reference, employee_code);

CREATE INDEX idx_partner_employees_company_batch_active
    ON partner_employees (partner_company_id, import_batch_id, active);

ALTER TABLE loan_products
    ADD CONSTRAINT chk_loan_products_min_amount_non_negative
    CHECK (min_amount >= 0);

ALTER TABLE loan_products
    ADD CONSTRAINT chk_loan_products_max_amount_at_least_min_amount
    CHECK (max_amount >= min_amount);
