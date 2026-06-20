CREATE TABLE partner_employee_import_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    partner_company_id UUID NOT NULL,

    effective_month VARCHAR(7) NOT NULL,
    status VARCHAR(30) NOT NULL,

    valid_row_count INTEGER NOT NULL DEFAULT 0,
    invalid_row_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_partner_employee_import_batches_partner_company
        FOREIGN KEY (partner_company_id)
        REFERENCES partner_companies (id),

    CONSTRAINT chk_partner_employee_import_batches_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),

    CONSTRAINT chk_partner_employee_import_batches_effective_month
        CHECK (effective_month ~ '^[0-9]{4}-[0-9]{2}$'),

    CONSTRAINT chk_partner_employee_import_batches_valid_row_count
        CHECK (valid_row_count >= 0),

    CONSTRAINT chk_partner_employee_import_batches_invalid_row_count
        CHECK (invalid_row_count >= 0)
);

CREATE TABLE partner_employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    partner_company_id UUID NOT NULL,
    import_batch_id UUID NOT NULL,

    employee_code VARCHAR(50) NOT NULL,
    identity_reference VARCHAR(100) NOT NULL,

    salary_amount NUMERIC(19,2) NOT NULL,
    salary_advance_limit NUMERIC(19,2) NOT NULL,

    employment_status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_partner_employees_partner_company
        FOREIGN KEY (partner_company_id)
        REFERENCES partner_companies (id),

    CONSTRAINT fk_partner_employees_import_batch
        FOREIGN KEY (import_batch_id)
        REFERENCES partner_employee_import_batches (id),

    CONSTRAINT chk_partner_employees_employment_status
        CHECK (employment_status IN ('ACTIVE', 'INACTIVE', 'TERMINATED', 'SUSPENDED')),

    CONSTRAINT chk_partner_employees_salary_amount
        CHECK (salary_amount >= 0),

    CONSTRAINT chk_partner_employees_salary_advance_limit
        CHECK (salary_advance_limit >= 0),

    CONSTRAINT uq_partner_employees_company_batch_employee_code
        UNIQUE (partner_company_id, import_batch_id, employee_code)
);

CREATE INDEX idx_partner_employee_import_batches_partner_company_id
    ON partner_employee_import_batches (partner_company_id);

CREATE INDEX idx_partner_employee_import_batches_effective_month
    ON partner_employee_import_batches (effective_month);

CREATE INDEX idx_partner_employees_partner_company_id
    ON partner_employees (partner_company_id);

CREATE INDEX idx_partner_employees_import_batch_id
    ON partner_employees (import_batch_id);

CREATE INDEX idx_partner_employees_identity_reference
    ON partner_employees (identity_reference);

CREATE INDEX idx_partner_employees_employee_code
    ON partner_employees (employee_code);

CREATE INDEX idx_partner_employees_active
    ON partner_employees (active);