CREATE SEQUENCE loan_application_number_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE loan_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    customer_id UUID NOT NULL,
    loan_product_id UUID NOT NULL,

    application_number VARCHAR(50) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    product_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,

    requested_amount NUMERIC(19,2) NOT NULL,
    requested_term_months INTEGER NOT NULL,
    submitted_at TIMESTAMP NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_applications_loan_product
        FOREIGN KEY (loan_product_id)
        REFERENCES loan_products (id),

    CONSTRAINT uq_loan_applications_application_number
        UNIQUE (application_number),

    CONSTRAINT chk_loan_applications_product_code
        CHECK (product_code IN (
            'SALARY_ADVANCE',
            'UNSECURED_CONSUMER_LOAN',
            'COLLATERAL_LOAN'
        )),

    CONSTRAINT chk_loan_applications_product_type
        CHECK (product_type IN (
            'SALARY_BASED',
            'UNSECURED',
            'SECURED'
        )),

    CONSTRAINT chk_loan_applications_status
        CHECK (status IN (
            'DRAFT',
            'SUBMITTED',
            'VERIFICATION_PENDING',
            'VERIFICATION_FAILED',
            'DOCUMENTS_PENDING',
            'UNDER_REVIEW',
            'RETURNED_FOR_REVISION',
            'RETURNED_TO_REVIEW',
            'APPROVAL_PENDING',
            'APPROVED',
            'REJECTED',
            'CUSTOMER_ACCEPTANCE_PENDING',
            'CUSTOMER_DECLINED',
            'CONTRACT_PENDING',
            'DISBURSEMENT_PENDING',
            'DISBURSED',
            'CANCELLED',
            'EXPIRED'
        )),

    CONSTRAINT chk_loan_applications_requested_amount_positive
        CHECK (requested_amount > 0),

    CONSTRAINT chk_loan_applications_requested_term_positive
        CHECK (requested_term_months > 0)
);

CREATE UNIQUE INDEX uq_loan_applications_customer_product_active
    ON loan_applications (customer_id, product_code)
    WHERE status IN (
        'SUBMITTED',
        'VERIFICATION_PENDING',
        'DOCUMENTS_PENDING',
        'UNDER_REVIEW',
        'RETURNED_TO_REVIEW',
        'APPROVAL_PENDING',
        'APPROVED',
        'CUSTOMER_ACCEPTANCE_PENDING',
        'CONTRACT_PENDING',
        'DISBURSEMENT_PENDING'
    );

CREATE INDEX idx_loan_applications_customer_id
    ON loan_applications (customer_id);

CREATE INDEX idx_loan_applications_product_status
    ON loan_applications (product_code, status);

CREATE INDEX idx_loan_applications_submitted_at
    ON loan_applications (submitted_at);

CREATE TABLE salary_advance_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    customer_id UUID NOT NULL,
    customer_partner_employee_link_id UUID NOT NULL,

    total_limit NUMERIC(19,2) NOT NULL,
    used_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    reserved_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    available_amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    last_refreshed_at TIMESTAMP NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_salary_advance_limits_customer_link
        UNIQUE (customer_id, customer_partner_employee_link_id),

    CONSTRAINT chk_salary_advance_limits_status
        CHECK (status IN (
            'ACTIVE',
            'SUSPENDED',
            'DISABLED',
            'STALE'
        )),

    CONSTRAINT chk_salary_advance_limits_total_limit_non_negative
        CHECK (total_limit >= 0),

    CONSTRAINT chk_salary_advance_limits_used_amount_non_negative
        CHECK (used_amount >= 0),

    CONSTRAINT chk_salary_advance_limits_reserved_amount_non_negative
        CHECK (reserved_amount >= 0),

    CONSTRAINT chk_salary_advance_limits_available_amount_non_negative
        CHECK (available_amount >= 0),

    CONSTRAINT chk_salary_advance_limits_available_consistent
        CHECK (available_amount = total_limit - used_amount - reserved_amount)
);

CREATE INDEX idx_salary_advance_limits_customer_id
    ON salary_advance_limits (customer_id);

CREATE INDEX idx_salary_advance_limits_link_id
    ON salary_advance_limits (customer_partner_employee_link_id);

CREATE INDEX idx_salary_advance_limits_status
    ON salary_advance_limits (status);

CREATE TABLE salary_advance_limit_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    salary_advance_limit_id UUID NOT NULL,
    loan_application_id UUID,
    loan_account_id UUID,

    movement_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_salary_advance_limit_movements_limit
        FOREIGN KEY (salary_advance_limit_id)
        REFERENCES salary_advance_limits (id),

    CONSTRAINT fk_salary_advance_limit_movements_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),

    CONSTRAINT chk_salary_advance_limit_movements_type
        CHECK (movement_type IN (
            'INITIALIZED',
            'REFRESHED',
            'RESERVED',
            'RESERVATION_RELEASED',
            'DISBURSED_TO_USED',
            'REPAID_RELEASED',
            'SUSPENDED',
            'DISABLED',
            'MANUAL_ADJUSTMENT'
        )),

    CONSTRAINT chk_salary_advance_limit_movements_amount_non_negative
        CHECK (amount >= 0)
);

CREATE INDEX idx_salary_advance_limit_movements_limit_id
    ON salary_advance_limit_movements (salary_advance_limit_id);

CREATE INDEX idx_salary_advance_limit_movements_application_id
    ON salary_advance_limit_movements (loan_application_id);

CREATE INDEX idx_salary_advance_limit_movements_type_occurred_at
    ON salary_advance_limit_movements (movement_type, occurred_at);

CREATE TABLE salary_advance_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    loan_application_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    customer_partner_employee_link_id UUID NOT NULL,
    salary_advance_limit_id UUID NOT NULL,

    partner_company_id UUID NOT NULL,
    partner_employee_id UUID NOT NULL,
    source_import_batch_id UUID NOT NULL,

    product_verification_result VARCHAR(50) NOT NULL,

    total_limit_snapshot NUMERIC(19,2) NOT NULL,
    used_amount_snapshot NUMERIC(19,2) NOT NULL,
    reserved_amount_snapshot NUMERIC(19,2) NOT NULL,
    available_limit_snapshot NUMERIC(19,2) NOT NULL,

    verified_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_salary_advance_verifications_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),

    CONSTRAINT fk_salary_advance_verifications_limit
        FOREIGN KEY (salary_advance_limit_id)
        REFERENCES salary_advance_limits (id),

    CONSTRAINT uq_salary_advance_verifications_application
        UNIQUE (loan_application_id),

    CONSTRAINT chk_salary_advance_verifications_result
        CHECK (product_verification_result IN (
            'VERIFIED',
            'FAILED',
            'PENDING_MANUAL_REVIEW',
            'REQUIRES_MORE_INFORMATION'
        )),

    CONSTRAINT chk_salary_advance_verifications_total_limit_non_negative
        CHECK (total_limit_snapshot >= 0),

    CONSTRAINT chk_salary_advance_verifications_used_amount_non_negative
        CHECK (used_amount_snapshot >= 0),

    CONSTRAINT chk_salary_advance_verifications_reserved_amount_non_negative
        CHECK (reserved_amount_snapshot >= 0),

    CONSTRAINT chk_salary_advance_verifications_available_limit_non_negative
        CHECK (available_limit_snapshot >= 0)
);

CREATE INDEX idx_salary_advance_verifications_customer_id
    ON salary_advance_verifications (customer_id);

CREATE INDEX idx_salary_advance_verifications_link_id
    ON salary_advance_verifications (customer_partner_employee_link_id);

CREATE INDEX idx_salary_advance_verifications_partner_company_id
    ON salary_advance_verifications (partner_company_id);
