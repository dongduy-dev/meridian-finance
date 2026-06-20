CREATE TABLE partner_companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    salary_advance_policy_limit NUMERIC(19,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_partner_companies_company_code
        UNIQUE (company_code),

    CONSTRAINT chk_partner_companies_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),

    CONSTRAINT chk_partner_companies_salary_advance_policy_limit
        CHECK (salary_advance_policy_limit >= 0)
);

CREATE INDEX idx_partner_companies_status
    ON partner_companies (status);