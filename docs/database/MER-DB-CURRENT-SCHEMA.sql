-- Meridian current physical schema snapshot.
-- Documentation only. Flyway migrations under meridian-platform/src/main/resources/db/migration
-- remain the executable database history.
-- Snapshot source: migrations V1 through V13.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    status TEXT,
    completion_attempts INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx
    ON event_publication USING hash(serialized_event);

CREATE INDEX event_publication_by_completion_date_idx
    ON event_publication (completion_date);

CREATE TABLE loan_products (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    product_code VARCHAR(50) NOT NULL,
    product_type VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    min_amount NUMERIC(19, 2) NOT NULL,
    max_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_loan_products PRIMARY KEY (id),
    CONSTRAINT uq_loan_products_product_code UNIQUE (product_code),
    CONSTRAINT chk_loan_products_min_amount_non_negative CHECK (min_amount >= 0),
    CONSTRAINT chk_loan_products_max_amount_at_least_min_amount CHECK (max_amount >= min_amount)
);

CREATE TABLE loan_product_policies (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    loan_product_id UUID NOT NULL,
    policy_code VARCHAR(100) NOT NULL,
    policy_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    offer_validity_days INTEGER NOT NULL DEFAULT 7,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_loan_product_policies PRIMARY KEY (id),
    CONSTRAINT fk_loan_product_policies_loan_product
        FOREIGN KEY (loan_product_id)
        REFERENCES loan_products (id),
    CONSTRAINT uq_loan_product_policies_product_policy
        UNIQUE (loan_product_id, policy_code)
);

CREATE INDEX idx_loan_product_policies_loan_product_id
    ON loan_product_policies (loan_product_id);

CREATE TABLE partner_companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    salary_advance_policy_limit NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_partner_companies_company_code UNIQUE (company_code),
    CONSTRAINT chk_partner_companies_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_partner_companies_salary_advance_policy_limit CHECK (salary_advance_policy_limit >= 0)
);

CREATE INDEX idx_partner_companies_status
    ON partner_companies (status);

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
        CHECK (invalid_row_count >= 0),
    CONSTRAINT uq_partner_employee_import_batches_id_partner_company
        UNIQUE (id, partner_company_id)
);

CREATE INDEX idx_partner_employee_import_batches_partner_company_id
    ON partner_employee_import_batches (partner_company_id);

CREATE INDEX idx_partner_employee_import_batches_effective_month
    ON partner_employee_import_batches (effective_month);

CREATE INDEX idx_partner_employee_import_batches_company_status_month_desc
    ON partner_employee_import_batches (partner_company_id, status, effective_month DESC);

CREATE TABLE partner_employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_company_id UUID NOT NULL,
    import_batch_id UUID NOT NULL,
    employee_code VARCHAR(50) NOT NULL,
    identity_reference VARCHAR(100) NOT NULL,
    salary_amount NUMERIC(19, 2) NOT NULL,
    salary_advance_limit NUMERIC(19, 2) NOT NULL,
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
    CONSTRAINT fk_partner_employees_import_batch_partner_company
        FOREIGN KEY (import_batch_id, partner_company_id)
        REFERENCES partner_employee_import_batches (id, partner_company_id),
    CONSTRAINT chk_partner_employees_employment_status
        CHECK (employment_status IN ('ACTIVE', 'INACTIVE', 'TERMINATED', 'SUSPENDED')),
    CONSTRAINT chk_partner_employees_salary_amount CHECK (salary_amount >= 0),
    CONSTRAINT chk_partner_employees_salary_advance_limit CHECK (salary_advance_limit >= 0),
    CONSTRAINT uq_partner_employees_company_batch_employee_code
        UNIQUE (partner_company_id, import_batch_id, employee_code),
    CONSTRAINT uq_partner_employees_id_partner_company
        UNIQUE (id, partner_company_id)
);

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

CREATE INDEX idx_partner_employees_company_batch_identity_employee_code
    ON partner_employees (partner_company_id, import_batch_id, identity_reference, employee_code);

CREATE INDEX idx_partner_employees_company_batch_active
    ON partner_employees (partner_company_id, import_batch_id, active);

CREATE TABLE customer_partner_employee_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    partner_company_id UUID NOT NULL,
    partner_employee_id UUID NOT NULL,
    source_import_batch_id UUID NOT NULL,
    verification_outcome VARCHAR(50) NOT NULL,
    link_status VARCHAR(50) NOT NULL,
    verified_identity_ref VARCHAR(100) NOT NULL,
    verified_employee_code VARCHAR(50) NOT NULL,
    last_verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_partner_employee_links_partner_company
        FOREIGN KEY (partner_company_id)
        REFERENCES partner_companies (id),
    CONSTRAINT fk_customer_partner_employee_links_partner_employee_company
        FOREIGN KEY (partner_employee_id, partner_company_id)
        REFERENCES partner_employees (id, partner_company_id),
    CONSTRAINT fk_customer_partner_employee_links_import_batch_company
        FOREIGN KEY (source_import_batch_id, partner_company_id)
        REFERENCES partner_employee_import_batches (id, partner_company_id),
    CONSTRAINT chk_customer_partner_employee_links_verification_outcome
        CHECK (verification_outcome IN (
            'MATCHED_ACTIVE',
            'MATCHED_INACTIVE',
            'NOT_FOUND',
            'MULTIPLE_MATCHES',
            'PENDING_MANUAL_REVIEW',
            'MANUAL_REVIEW_APPROVED',
            'MANUAL_REVIEW_REJECTED'
        )),
    CONSTRAINT chk_customer_partner_employee_links_link_status
        CHECK (link_status IN (
            'PENDING_VERIFICATION',
            'VERIFIED',
            'PENDING_MANUAL_REVIEW',
            'SUSPENDED',
            'DISABLED'
        ))
);

CREATE UNIQUE INDEX uq_customer_partner_employee_links_current_verified
    ON customer_partner_employee_links (customer_id, partner_company_id)
    WHERE link_status = 'VERIFIED';

CREATE INDEX idx_customer_partner_employee_links_customer_company_status
    ON customer_partner_employee_links (customer_id, partner_company_id, link_status);

CREATE INDEX idx_customer_partner_employee_links_partner_company_id
    ON customer_partner_employee_links (partner_company_id);

CREATE INDEX idx_customer_partner_employee_links_partner_employee_id
    ON customer_partner_employee_links (partner_employee_id);

CREATE INDEX idx_customer_partner_employee_links_link_status
    ON customer_partner_employee_links (link_status);

CREATE INDEX idx_customer_partner_employee_links_last_verified_at
    ON customer_partner_employee_links (last_verified_at);

CREATE INDEX idx_customer_partner_employee_links_last_refreshed_at
    ON customer_partner_employee_links (last_refreshed_at);

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
    requested_amount NUMERIC(19, 2) NOT NULL,
    requested_term_months INTEGER NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_applications_loan_product
        FOREIGN KEY (loan_product_id)
        REFERENCES loan_products (id),
    CONSTRAINT uq_loan_applications_application_number UNIQUE (application_number),
    CONSTRAINT chk_loan_applications_product_code
        CHECK (product_code IN ('SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN')),
    CONSTRAINT chk_loan_applications_product_type
        CHECK (product_type IN ('SALARY_BASED', 'UNSECURED', 'SECURED')),
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
    CONSTRAINT chk_loan_applications_requested_amount_positive CHECK (requested_amount > 0),
    CONSTRAINT chk_loan_applications_requested_term_positive CHECK (requested_term_months > 0)
);

CREATE UNIQUE INDEX uq_loan_applications_customer_product_active
    ON loan_applications (customer_id, product_code)
    WHERE status IN (
        'SUBMITTED',
        'VERIFICATION_PENDING',
        'DOCUMENTS_PENDING',
        'UNDER_REVIEW',
        'RETURNED_FOR_REVISION',
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
    total_limit NUMERIC(19, 2) NOT NULL,
    used_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    reserved_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    available_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    last_refreshed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_salary_advance_limits_customer_link
        UNIQUE (customer_id, customer_partner_employee_link_id),
    CONSTRAINT chk_salary_advance_limits_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED', 'STALE')),
    CONSTRAINT chk_salary_advance_limits_total_limit_non_negative CHECK (total_limit >= 0),
    CONSTRAINT chk_salary_advance_limits_used_amount_non_negative CHECK (used_amount >= 0),
    CONSTRAINT chk_salary_advance_limits_reserved_amount_non_negative CHECK (reserved_amount >= 0),
    CONSTRAINT chk_salary_advance_limits_available_amount_non_negative CHECK (available_amount >= 0),
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
    amount NUMERIC(19, 2) NOT NULL,
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
    CONSTRAINT chk_salary_advance_limit_movements_amount_non_negative CHECK (amount >= 0)
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
    employee_verification_outcome VARCHAR(50) NOT NULL,
    product_verification_result VARCHAR(50) NOT NULL,
    total_limit_snapshot NUMERIC(19, 2) NOT NULL,
    used_amount_snapshot NUMERIC(19, 2) NOT NULL,
    reserved_amount_snapshot NUMERIC(19, 2) NOT NULL,
    available_limit_snapshot NUMERIC(19, 2) NOT NULL,
    verified_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_salary_advance_verifications_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_salary_advance_verifications_limit
        FOREIGN KEY (salary_advance_limit_id)
        REFERENCES salary_advance_limits (id),
    CONSTRAINT uq_salary_advance_verifications_application UNIQUE (loan_application_id),
    CONSTRAINT chk_salary_advance_verifications_employee_outcome
        CHECK (employee_verification_outcome IN (
            'MATCHED_ACTIVE',
            'MATCHED_INACTIVE',
            'NOT_FOUND',
            'MULTIPLE_MATCHES',
            'PENDING_MANUAL_REVIEW',
            'MANUAL_REVIEW_APPROVED',
            'MANUAL_REVIEW_REJECTED'
        )),
    CONSTRAINT chk_salary_advance_verifications_result
        CHECK (product_verification_result IN (
            'VERIFIED',
            'FAILED',
            'PENDING_MANUAL_REVIEW',
            'REQUIRES_MORE_INFORMATION'
        )),
    CONSTRAINT chk_salary_advance_verifications_total_limit_non_negative CHECK (total_limit_snapshot >= 0),
    CONSTRAINT chk_salary_advance_verifications_used_amount_non_negative CHECK (used_amount_snapshot >= 0),
    CONSTRAINT chk_salary_advance_verifications_reserved_amount_non_negative CHECK (reserved_amount_snapshot >= 0),
    CONSTRAINT chk_salary_advance_verifications_available_limit_non_negative CHECK (available_limit_snapshot >= 0)
);

CREATE INDEX idx_salary_advance_verifications_customer_id
    ON salary_advance_verifications (customer_id);

CREATE INDEX idx_salary_advance_verifications_link_id
    ON salary_advance_verifications (customer_partner_employee_link_id);

CREATE INDEX idx_salary_advance_verifications_partner_company_id
    ON salary_advance_verifications (partner_company_id);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    normalized_email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    customer_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_users_user_type CHECK (user_type IN ('CUSTOMER', 'STAFF')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT chk_users_customer_mapping
        CHECK (
            (user_type = 'CUSTOMER' AND customer_id IS NOT NULL)
            OR (user_type = 'STAFF' AND customer_id IS NULL)
        )
);

CREATE INDEX idx_users_status
    ON users (status);

CREATE INDEX idx_users_customer_id
    ON users (customer_id);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(120) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_permissions_code UNIQUE (code)
);

CREATE TABLE role_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_role_assignments_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),
    CONSTRAINT fk_role_assignments_role
        FOREIGN KEY (role_id)
        REFERENCES roles (id),
    CONSTRAINT uq_role_assignments_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_role_assignments_user_id
    ON role_assignments (user_id);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id)
        REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id)
        REFERENCES permissions (id)
);

CREATE INDEX idx_role_permissions_permission_id
    ON role_permissions (permission_id);
