-- Meridian current physical schema snapshot.
-- Documentation only. Flyway migrations under meridian-platform/src/main/resources/db/migration
-- remain the executable database history.
-- Snapshot source: migrations V1 through V37.

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
    interest_calculation_method VARCHAR(50),
    flat_monthly_interest_rate NUMERIC(9, 6),
    fee_amount NUMERIC(19, 2),
    repayment_method VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_loan_product_policies PRIMARY KEY (id),
    CONSTRAINT fk_loan_product_policies_loan_product
        FOREIGN KEY (loan_product_id)
        REFERENCES loan_products (id),
    CONSTRAINT uq_loan_product_policies_product_policy
        UNIQUE (loan_product_id, policy_code),
    CONSTRAINT chk_loan_product_policies_interest_calculation_method
        CHECK (interest_calculation_method IS NULL OR interest_calculation_method IN ('FLAT_ORIGINAL_PRINCIPAL')),
    CONSTRAINT chk_loan_product_policies_flat_monthly_interest_rate_non_negative
        CHECK (flat_monthly_interest_rate IS NULL OR flat_monthly_interest_rate >= 0),
    CONSTRAINT chk_loan_product_policies_fee_amount_non_negative
        CHECK (fee_amount IS NULL OR fee_amount >= 0),
    CONSTRAINT chk_loan_product_policies_fee_amount_whole_vnd
        CHECK (fee_amount IS NULL OR fee_amount = trunc(fee_amount)),
    CONSTRAINT chk_loan_product_policies_repayment_method
        CHECK (repayment_method IS NULL OR repayment_method IN ('ON_SALARY_DATE', 'MONTHLY_INSTALLMENT')),
    CONSTRAINT chk_loan_product_policies_offer_validity_days_positive
        CHECK (offer_validity_days > 0)
);

CREATE INDEX idx_loan_product_policies_loan_product_id
    ON loan_product_policies (loan_product_id);
CREATE TABLE loan_product_policy_terms (
    loan_product_policy_id UUID NOT NULL,
    term_months INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_loan_product_policy_terms PRIMARY KEY (loan_product_policy_id, term_months),
    CONSTRAINT fk_loan_product_policy_terms_policy
        FOREIGN KEY (loan_product_policy_id)
        REFERENCES loan_product_policies (id),
    CONSTRAINT chk_loan_product_policy_terms_positive CHECK (term_months > 0)
);

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

CREATE SEQUENCE customer_number_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    profile_completion_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_customers_customer_number UNIQUE (customer_number),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT chk_customers_verification_status CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_customers_profile_completion_status CHECK (profile_completion_status IN ('INCOMPLETE', 'COMPLETE'))
);

CREATE TABLE customer_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    identity_reference_ciphertext TEXT NOT NULL,
    identity_reference_fingerprint VARCHAR(128) NOT NULL,
    identity_reference_last_four VARCHAR(4) NOT NULL,
    phone_number VARCHAR(50) NOT NULL,
    residential_address VARCHAR(500) NOT NULL,
    employment_status VARCHAR(50) NOT NULL,
    employer_name VARCHAR(200),
    terms_consent_accepted BOOLEAN NOT NULL,
    data_processing_consent_accepted BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_profiles_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),
    CONSTRAINT uq_customer_profiles_customer_id UNIQUE (customer_id),
    CONSTRAINT uq_customer_profiles_identity_reference_fingerprint UNIQUE (identity_reference_fingerprint),
    CONSTRAINT chk_customer_profiles_full_name CHECK (btrim(full_name) <> ''),
    CONSTRAINT chk_customer_profiles_identity_reference_ciphertext CHECK (btrim(identity_reference_ciphertext) <> ''),
    CONSTRAINT chk_customer_profiles_identity_reference_fingerprint CHECK (btrim(identity_reference_fingerprint) <> ''),
    CONSTRAINT chk_customer_profiles_identity_reference_last_four CHECK (length(btrim(identity_reference_last_four)) BETWEEN 1 AND 4),
    CONSTRAINT chk_customer_profiles_phone_number CHECK (btrim(phone_number) <> ''),
    CONSTRAINT chk_customer_profiles_residential_address CHECK (btrim(residential_address) <> ''),
    CONSTRAINT chk_customer_profiles_employment_status CHECK (btrim(employment_status) <> '')
);

CREATE TABLE customer_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    bank_code VARCHAR(50) NOT NULL,
    bank_name_snapshot VARCHAR(150) NOT NULL,
    account_holder_name VARCHAR(200) NOT NULL,
    account_number_ciphertext TEXT NOT NULL,
    account_number_fingerprint VARCHAR(128) NOT NULL,
    account_number_last_four VARCHAR(4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    primary_account BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at TIMESTAMP,

    CONSTRAINT fk_customer_bank_accounts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),
    CONSTRAINT chk_customer_bank_accounts_bank_code CHECK (btrim(bank_code) <> ''),
    CONSTRAINT chk_customer_bank_accounts_bank_name_snapshot CHECK (btrim(bank_name_snapshot) <> ''),
    CONSTRAINT chk_customer_bank_accounts_account_holder_name CHECK (btrim(account_holder_name) <> ''),
    CONSTRAINT chk_customer_bank_accounts_number_ciphertext CHECK (btrim(account_number_ciphertext) <> ''),
    CONSTRAINT chk_customer_bank_accounts_number_fingerprint CHECK (btrim(account_number_fingerprint) <> ''),
    CONSTRAINT chk_customer_bank_accounts_number_last_four CHECK (length(btrim(account_number_last_four)) BETWEEN 1 AND 4),
    CONSTRAINT chk_customer_bank_accounts_status CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    CONSTRAINT chk_customer_bank_accounts_primary_active CHECK (status = 'ACTIVE' OR primary_account = FALSE),
    CONSTRAINT chk_customer_bank_accounts_deactivated_at CHECK (
        (status = 'ACTIVE' AND deactivated_at IS NULL)
        OR (status = 'DEACTIVATED' AND deactivated_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_customer_bank_accounts_primary_active
    ON customer_bank_accounts (customer_id)
    WHERE status = 'ACTIVE' AND primary_account = TRUE;

CREATE UNIQUE INDEX uq_customer_bank_accounts_active_fingerprint
    ON customer_bank_accounts (customer_id, account_number_fingerprint)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_customer_bank_accounts_customer_status
    ON customer_bank_accounts (customer_id, status);

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

    CONSTRAINT fk_customer_partner_employee_links_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),
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

    CONSTRAINT fk_loan_applications_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),
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
    CONSTRAINT chk_loan_applications_requested_amount_whole_vnd
        CHECK (requested_amount = trunc(requested_amount)),
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

    CONSTRAINT fk_salary_advance_limits_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),
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
CREATE UNIQUE INDEX uq_salary_advance_limit_movements_application_release
    ON salary_advance_limit_movements (loan_application_id)
    WHERE movement_type = 'RESERVATION_RELEASED'
      AND loan_application_id IS NOT NULL;
CREATE UNIQUE INDEX uq_salary_advance_limit_movements_application_reserved
    ON salary_advance_limit_movements (loan_application_id)
    WHERE movement_type = 'RESERVED'
      AND loan_application_id IS NOT NULL;


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

    CONSTRAINT fk_salary_advance_verifications_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),
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
CREATE TABLE approved_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    source_loan_product_policy_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    approved_principal NUMERIC(19, 2) NOT NULL,
    approved_term_months INTEGER NOT NULL,
    interest_calculation_method VARCHAR(50) NOT NULL,
    flat_monthly_interest_rate NUMERIC(9, 6) NOT NULL,
    total_interest NUMERIC(19, 2) NOT NULL,
    fee_amount NUMERIC(19, 2) NOT NULL,
    total_repayment_amount NUMERIC(19, 2) NOT NULL,
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
    CONSTRAINT uq_approved_offers_application UNIQUE (loan_application_id),
    CONSTRAINT chk_approved_offers_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT chk_approved_offers_principal_positive CHECK (approved_principal > 0),
    CONSTRAINT chk_approved_offers_term_positive CHECK (approved_term_months > 0),
    CONSTRAINT chk_approved_offers_interest_method CHECK (interest_calculation_method IN ('FLAT_ORIGINAL_PRINCIPAL')),
    CONSTRAINT chk_approved_offers_interest_rate_non_negative CHECK (flat_monthly_interest_rate >= 0),
    CONSTRAINT chk_approved_offers_money_non_negative
        CHECK (total_interest >= 0 AND fee_amount >= 0 AND total_repayment_amount >= 0),
    CONSTRAINT chk_approved_offers_whole_vnd
        CHECK (
            approved_principal = trunc(approved_principal)
            AND total_interest = trunc(total_interest)
            AND fee_amount = trunc(fee_amount)
            AND total_repayment_amount = trunc(total_repayment_amount)
        ),
    CONSTRAINT chk_approved_offers_repayment_method CHECK (repayment_method IN ('ON_SALARY_DATE')),
    CONSTRAINT chk_approved_offers_total_repayment
        CHECK (total_repayment_amount = approved_principal + total_interest + fee_amount),
    CONSTRAINT chk_approved_offers_expiry_after_generation CHECK (expires_at > generated_at),
    CONSTRAINT chk_approved_offers_status_timestamps
        CHECK (
            (status = 'PENDING' AND accepted_at IS NULL AND declined_at IS NULL AND expired_at IS NULL)
            OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND declined_at IS NULL AND expired_at IS NULL)
            OR (status = 'DECLINED' AND accepted_at IS NULL AND declined_at IS NOT NULL AND expired_at IS NULL)
            OR (status = 'EXPIRED' AND accepted_at IS NULL AND declined_at IS NULL AND expired_at IS NOT NULL)
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
    principal_due NUMERIC(19, 2) NOT NULL,
    interest_due NUMERIC(19, 2) NOT NULL,
    fee_due NUMERIC(19, 2) NOT NULL,
    total_due NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_approved_offer_repayment_items_offer
        FOREIGN KEY (approved_offer_id)
        REFERENCES approved_offers (id),
    CONSTRAINT uq_approved_offer_repayment_items_offer_installment
        UNIQUE (approved_offer_id, installment_number),
    CONSTRAINT chk_approved_offer_repayment_items_installment_positive CHECK (installment_number > 0),
    CONSTRAINT chk_approved_offer_repayment_items_money_non_negative
        CHECK (principal_due >= 0 AND interest_due >= 0 AND fee_due >= 0 AND total_due >= 0),
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
        ),
    CONSTRAINT fk_users_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id)
);

CREATE INDEX idx_users_status
    ON users (status);

CREATE UNIQUE INDEX uq_users_customer_id_present
    ON users (customer_id)
    WHERE customer_id IS NOT NULL;

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
CREATE TABLE review_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    loan_officer_user_id UUID NOT NULL,
    recommendation VARCHAR(50) NOT NULL,
    reason TEXT,
    internal_notes TEXT,
    submitted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_recommendations_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_review_recommendations_loan_officer
        FOREIGN KEY (loan_officer_user_id)
        REFERENCES users (id),
    CONSTRAINT chk_review_recommendations_recommendation
        CHECK (recommendation IN (
            'RECOMMEND_APPROVAL',
            'RECOMMEND_REJECTION',
            'RETURN_TO_CUSTOMER_REVISION',
            'REQUEST_STAFF_CORRECTION'
        )),
    CONSTRAINT chk_review_recommendations_reason_required
        CHECK (
            recommendation = 'RECOMMEND_APPROVAL'
            OR (reason IS NOT NULL AND btrim(reason) <> '')
        )
);

CREATE INDEX idx_review_recommendations_application_id
    ON review_recommendations (loan_application_id);

CREATE INDEX idx_review_recommendations_loan_officer_id
    ON review_recommendations (loan_officer_user_id);

CREATE INDEX idx_review_recommendations_submitted_at
    ON review_recommendations (submitted_at);

CREATE TABLE approval_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    review_recommendation_id UUID NOT NULL,
    approver_user_id UUID NOT NULL,
    decision VARCHAR(50) NOT NULL,
    reason TEXT,
    internal_notes TEXT,
    decided_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_approval_decisions_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_approval_decisions_recommendation
        FOREIGN KEY (review_recommendation_id)
        REFERENCES review_recommendations (id),
    CONSTRAINT fk_approval_decisions_approver
        FOREIGN KEY (approver_user_id)
        REFERENCES users (id),
    CONSTRAINT chk_approval_decisions_decision
        CHECK (decision IN (
            'APPROVE',
            'REJECT',
            'RETURN_TO_LOAN_OFFICER_REVIEW',
            'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
        )),
    CONSTRAINT chk_approval_decisions_reason_required
        CHECK (
            decision = 'APPROVE'
            OR (reason IS NOT NULL AND btrim(reason) <> '')
        )
);

CREATE INDEX idx_approval_decisions_application_id
    ON approval_decisions (loan_application_id);

CREATE INDEX idx_approval_decisions_recommendation_id
    ON approval_decisions (review_recommendation_id);

CREATE INDEX idx_approval_decisions_approver_id
    ON approval_decisions (approver_user_id);

CREATE INDEX idx_approval_decisions_decided_at
    ON approval_decisions (decided_at);

-- V17: Audit events and Loan Application lifecycle history.
CREATE TABLE loan_application_status_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    sequence_number SMALLINT NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    action VARCHAR(80) NOT NULL,
    reason TEXT,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    occurred_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_application_status_transitions_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_application_status_transitions_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_loan_application_status_transitions_application_sequence
        UNIQUE (loan_application_id, sequence_number),
    CONSTRAINT chk_loan_application_status_transitions_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_loan_application_status_transitions_from_status
        CHECK (
            from_status IS NULL
            OR from_status IN (
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
            )
        ),
    CONSTRAINT chk_loan_application_status_transitions_to_status
        CHECK (
            to_status IN (
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
            )
        ),
    CONSTRAINT chk_loan_application_status_transitions_action
        CHECK (
            action IN (
                'SUBMIT_APPLICATION',
                'START_REVIEW',
                'RECOMMEND_APPROVAL',
                'RECOMMEND_REJECTION',
                'RETURN_TO_CUSTOMER_REVISION',
                'REQUEST_STAFF_CORRECTION',
                'APPROVE',
                'REJECT',
                'RETURN_TO_LOAN_OFFICER_REVIEW',
                'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
                'GENERATE_APPROVED_OFFER',
                'ACCEPT_APPROVED_OFFER',
                'DECLINE_APPROVED_OFFER',
                'EXPIRE_APPROVED_OFFER'
            )
        ),
    CONSTRAINT chk_loan_application_status_transitions_initial
        CHECK (
            (
                from_status IS NULL
                AND action = 'SUBMIT_APPLICATION'
                AND to_status = 'SUBMITTED'
                AND sequence_number = 1
            )
            OR from_status IS NOT NULL
        ),
    CONSTRAINT chk_loan_application_status_transitions_status_change
        CHECK (from_status IS NULL OR from_status <> to_status),
    CONSTRAINT chk_loan_application_status_transitions_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_loan_application_status_transitions_actor
        CHECK (
            (actor_type = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        )
);

CREATE INDEX idx_loan_application_status_transitions_operation
    ON loan_application_status_transitions (operation_id);

CREATE INDEX idx_loan_application_status_transitions_actor_occurred
    ON loan_application_status_transitions (actor_user_id, occurred_at);

CREATE INDEX idx_loan_application_status_transitions_action_occurred
    ON loan_application_status_transitions (action, occurred_at);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL,
    sequence_number SMALLINT NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_events_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_audit_events_operation_sequence
        UNIQUE (operation_id, sequence_number),
    CONSTRAINT chk_audit_events_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_audit_events_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_audit_events_actor
        CHECK (
            (actor_type = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        ),
    CONSTRAINT chk_audit_events_entity_type
        CHECK (
            entity_type IN (
                'CUSTOMER',
                'CUSTOMER_BANK_ACCOUNT',
                'LOAN_APPLICATION',
                'SALARY_ADVANCE_LIMIT_MOVEMENT',
                'REVIEW_RECOMMENDATION',
                'APPROVAL_DECISION',
                'APPROVED_OFFER'
            )
        ),
    CONSTRAINT chk_audit_events_action
        CHECK (
            action IN (
                'CUSTOMER_PROFILE_CREATED',
                'CUSTOMER_PROFILE_UPDATED',
                'CUSTOMER_PROFILE_COMPLETED',
                'CUSTOMER_BANK_ACCOUNT_ADDED',
                'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
                'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
                'SALARY_ADVANCE_APPLICATION_SUBMITTED',
                'SALARY_ADVANCE_LIMIT_INITIALIZED',
                'SALARY_ADVANCE_LIMIT_REFRESHED',
                'SALARY_ADVANCE_LIMIT_RESERVED',
                'LOAN_REVIEW_STARTED',
                'REVIEW_RECOMMENDATION_RECORDED',
                'APPROVAL_DECISION_RECORDED',
                'APPROVED_OFFER_GENERATED',
                'APPROVED_OFFER_ACCEPTED',
                'APPROVED_OFFER_DECLINED',
                'OFFER_EXPIRED',
                'RESERVATION_RELEASED'
            )
        ),
    CONSTRAINT chk_audit_events_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_audit_events_payload_size
        CHECK (octet_length(payload::text) <= 2048)
);

CREATE INDEX idx_audit_events_entity_occurred
    ON audit_events (entity_type, entity_id, occurred_at);

CREATE INDEX idx_audit_events_actor_occurred
    ON audit_events (actor_user_id, occurred_at);

CREATE INDEX idx_audit_events_action_occurred
    ON audit_events (action, occurred_at);

CREATE OR REPLACE FUNCTION reject_immutable_history_row_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Immutable history rows cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_application_status_transitions_immutable
    BEFORE UPDATE OR DELETE ON loan_application_status_transitions
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_audit_events_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_history_row_mutation();

-- Incremental final-state clauses retained below to mirror the merged Flyway history.

-- V21 harden Salary Advance critical path
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM loan_applications
        WHERE requested_amount <> trunc(requested_amount)
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce whole-VND loan application amounts because existing requested_amount values contain non-zero fractional VND';
    END IF;
END $$;

ALTER TABLE loan_applications
    ADD CONSTRAINT chk_loan_applications_requested_amount_whole_vnd
        CHECK (requested_amount = trunc(requested_amount));

-- V22 document checklist and review foundation
CREATE TABLE document_checklists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    stage VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_document_checklists_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT uq_document_checklists_application_stage
        UNIQUE (loan_application_id, stage),
    CONSTRAINT chk_document_checklists_stage
        CHECK (stage IN ('SUBMISSION'))
);

INSERT INTO document_checklists (id, loan_application_id, stage, created_at)
SELECT gen_random_uuid(), id, 'SUBMISSION', submitted_at
FROM loan_applications
ON CONFLICT (loan_application_id, stage) DO NOTHING;

CREATE TABLE document_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    requirement_status VARCHAR(30) NOT NULL,
    current_review_decision_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_document_checklist_items_checklist
        FOREIGN KEY (checklist_id)
        REFERENCES document_checklists (id),
    CONSTRAINT uq_document_checklist_items_checklist_type
        UNIQUE (checklist_id, document_type),
    CONSTRAINT uq_document_checklist_items_id_checklist
        UNIQUE (id, checklist_id),
    CONSTRAINT chk_document_checklist_items_type
        CHECK (document_type IN ('RECENT_PAYSLIP')),
    CONSTRAINT chk_document_checklist_items_requirement
        CHECK (requirement_status IN ('REQUIRED', 'OPTIONAL', 'NOT_REQUIRED'))
);

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_item_id UUID NOT NULL,
    current_version_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_documents_checklist_item
        FOREIGN KEY (checklist_item_id)
        REFERENCES document_checklist_items (id),
    CONSTRAINT uq_documents_checklist_item
        UNIQUE (checklist_item_id),
    CONSTRAINT uq_documents_id_checklist_item
        UNIQUE (id, checklist_item_id)
);

CREATE TABLE document_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    upload_request_id UUID NOT NULL,
    baseline_document_version_id UUID,
    original_filename VARCHAR(255) NOT NULL,
    declared_mime_type VARCHAR(100) NOT NULL,
    detected_mime_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    uploader_actor_type VARCHAR(20) NOT NULL,
    uploader_user_id UUID NOT NULL,
    uploaded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_document_versions_document
        FOREIGN KEY (document_id)
        REFERENCES documents (id),
    CONSTRAINT fk_document_versions_baseline
        FOREIGN KEY (baseline_document_version_id)
        REFERENCES document_versions (id),
    CONSTRAINT fk_document_versions_uploader
        FOREIGN KEY (uploader_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_document_versions_document_sequence
        UNIQUE (document_id, version_number),
    CONSTRAINT uq_document_versions_upload_request
        UNIQUE (upload_request_id),
    CONSTRAINT uq_document_versions_storage_key
        UNIQUE (storage_key),
    CONSTRAINT uq_document_versions_id_document
        UNIQUE (id, document_id),
    CONSTRAINT chk_document_versions_sequence
        CHECK (version_number > 0),
    CONSTRAINT chk_document_versions_byte_size
        CHECK (byte_size > 0 AND byte_size <= 10485760),
    CONSTRAINT chk_document_versions_declared_mime
        CHECK (declared_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_document_versions_detected_mime
        CHECK (detected_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_document_versions_mime_match
        CHECK (declared_mime_type = detected_mime_type),
    CONSTRAINT chk_document_versions_sha256
        CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_document_versions_uploader_actor
        CHECK (uploader_actor_type IN ('CUSTOMER', 'STAFF')),
    CONSTRAINT chk_document_versions_filename_safe
        CHECK (
            btrim(original_filename) <> ''
            AND original_filename !~ '[\\/\x00-\x1F\x7F]'
            AND original_filename NOT LIKE '%..%'
        )
);

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_current_version
        FOREIGN KEY (current_version_id, id)
        REFERENCES document_versions (id, document_id);

CREATE TABLE document_review_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_item_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    review_request_id UUID NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    waiver_reason_code VARCHAR(80),
    restricted_staff_notes VARCHAR(2000),
    reviewer_user_id UUID NOT NULL,
    decided_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_document_review_decisions_item
        FOREIGN KEY (checklist_item_id)
        REFERENCES document_checklist_items (id),
    CONSTRAINT fk_document_review_decisions_version
        FOREIGN KEY (document_version_id)
        REFERENCES document_versions (id),
    CONSTRAINT fk_document_review_decisions_reviewer
        FOREIGN KEY (reviewer_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_document_review_decisions_request
        UNIQUE (review_request_id),
    CONSTRAINT uq_document_review_decisions_id_item
        UNIQUE (id, checklist_item_id),
    CONSTRAINT chk_document_review_decisions_outcome
        CHECK (outcome IN ('ACCEPT_DOCUMENT', 'WAIVE_DOCUMENT', 'REQUEST_REPLACEMENT')),
    CONSTRAINT chk_document_review_decisions_waiver
        CHECK (
            (
                outcome = 'WAIVE_DOCUMENT'
                AND waiver_reason_code IN (
                    'EVIDENCE_SATISFIED_BY_VERIFIED_SOURCE',
                    'DOCUMENT_NOT_APPLICABLE'
                )
            )
            OR (
                outcome <> 'WAIVE_DOCUMENT'
                AND waiver_reason_code IS NULL
            )
        )
);

ALTER TABLE document_checklist_items
    ADD CONSTRAINT fk_document_checklist_items_current_review
        FOREIGN KEY (current_review_decision_id, id)
        REFERENCES document_review_decisions (id, checklist_item_id);

CREATE INDEX idx_document_checklists_application
    ON document_checklists (loan_application_id, stage);

CREATE INDEX idx_document_checklist_items_review_queue
    ON document_checklist_items (requirement_status, current_review_decision_id, created_at);

CREATE INDEX idx_documents_current_version
    ON documents (current_version_id);

CREATE INDEX idx_document_versions_document_uploaded
    ON document_versions (document_id, uploaded_at DESC);

CREATE INDEX idx_document_review_decisions_item_decided
    ON document_review_decisions (checklist_item_id, decided_at DESC);

CREATE TRIGGER trg_document_versions_immutable
    BEFORE UPDATE OR DELETE ON document_versions
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_document_review_decisions_immutable
    BEFORE UPDATE OR DELETE ON document_review_decisions
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_history_row_mutation();

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action,
    DROP CONSTRAINT chk_loan_application_status_transitions_initial;

ALTER TABLE loan_application_status_transitions
    ADD CONSTRAINT chk_loan_application_status_transitions_action
        CHECK (
            action IN (
                'SUBMIT_APPLICATION',
                'COMPLETE_DOCUMENT_UPLOADS',
                'START_REVIEW',
                'RECOMMEND_APPROVAL',
                'RECOMMEND_REJECTION',
                'RETURN_TO_CUSTOMER_REVISION',
                'REQUEST_STAFF_CORRECTION',
                'APPROVE',
                'REJECT',
                'RETURN_TO_LOAN_OFFICER_REVIEW',
                'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
                'GENERATE_APPROVED_OFFER',
                'ACCEPT_APPROVED_OFFER',
                'DECLINE_APPROVED_OFFER',
                'EXPIRE_APPROVED_OFFER'
            )
        ),
    ADD CONSTRAINT chk_loan_application_status_transitions_initial
        CHECK (
            (
                from_status IS NULL
                AND action = 'SUBMIT_APPLICATION'
                AND to_status IN ('SUBMITTED', 'DOCUMENTS_PENDING')
                AND sequence_number = 1
            )
            OR from_status IS NOT NULL
        );

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_entity_type,
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type
        CHECK (
            entity_type IN (
                'CUSTOMER',
                'CUSTOMER_BANK_ACCOUNT',
                'LOAN_APPLICATION',
                'SALARY_ADVANCE_LIMIT_MOVEMENT',
                'REVIEW_RECOMMENDATION',
                'APPROVAL_DECISION',
                'APPROVED_OFFER',
                'DOCUMENT_CHECKLIST',
                'DOCUMENT_CHECKLIST_ITEM',
                'DOCUMENT_VERSION',
                'DOCUMENT_REVIEW_DECISION'
            )
        ),
    ADD CONSTRAINT chk_audit_events_action
        CHECK (
            action IN (
                'CUSTOMER_PROFILE_CREATED',
                'CUSTOMER_PROFILE_UPDATED',
                'CUSTOMER_PROFILE_COMPLETED',
                'CUSTOMER_BANK_ACCOUNT_ADDED',
                'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
                'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
                'SALARY_ADVANCE_APPLICATION_SUBMITTED',
                'SALARY_ADVANCE_LIMIT_INITIALIZED',
                'SALARY_ADVANCE_LIMIT_REFRESHED',
                'SALARY_ADVANCE_LIMIT_RESERVED',
                'LOAN_REVIEW_STARTED',
                'REVIEW_RECOMMENDATION_RECORDED',
                'APPROVAL_DECISION_RECORDED',
                'APPROVED_OFFER_GENERATED',
                'APPROVED_OFFER_ACCEPTED',
                'APPROVED_OFFER_DECLINED',
                'OFFER_EXPIRED',
                'RESERVATION_RELEASED',
                'DOCUMENT_CHECKLIST_CREATED',
                'DOCUMENT_VERSION_UPLOADED',
                'DOCUMENT_REVIEW_ACCEPTED',
                'DOCUMENT_WAIVED',
                'DOCUMENT_REPLACEMENT_REQUESTED',
                'DOCUMENT_UPLOADS_COMPLETED'
            )
        );

-- V23 customer correction and review cycles
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM document_review_decisions
        WHERE outcome = 'REQUEST_REPLACEMENT'
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill historical REQUEST_REPLACEMENT instructions deterministically';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM approval_decisions decision
        JOIN review_recommendations recommendation
          ON recommendation.id = decision.review_recommendation_id
        WHERE decision.loan_application_id <> recommendation.loan_application_id
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill review cycles: approval decision application does not match recommendation';
    END IF;

    IF EXISTS (
        SELECT review_recommendation_id
        FROM approval_decisions
        GROUP BY review_recommendation_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill review cycles: recommendation has multiple approval decisions';
    END IF;

    IF EXISTS (
        SELECT loan_application_id
        FROM review_recommendations
        GROUP BY loan_application_id
        HAVING COUNT(*) FILTER (
            WHERE NOT EXISTS (
                SELECT 1 FROM approval_decisions decision
                WHERE decision.review_recommendation_id = review_recommendations.id
            )
        ) > 1
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill review cycles: application has multiple unresolved recommendations';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_applications
        WHERE status = 'RETURNED_FOR_REVISION'
    ) OR EXISTS (
        SELECT 1
        FROM review_recommendations
        WHERE recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
    ) OR EXISTS (
        SELECT 1
        FROM approval_decisions
        WHERE decision = 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
    ) THEN
        RAISE EXCEPTION
            'V23 cannot backfill legacy revision actions without correction tasks and audience-specific instructions';
    END IF;
END
$$;

CREATE TABLE loan_application_review_cycles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    cycle_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_review_cycles_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT uq_loan_review_cycles_application_number
        UNIQUE (loan_application_id, cycle_number),
    CONSTRAINT uq_loan_review_cycles_id_application
        UNIQUE (id, loan_application_id),
    CONSTRAINT chk_loan_review_cycles_number
        CHECK (cycle_number > 0),
    CONSTRAINT chk_loan_review_cycles_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'SUPERSEDED', 'CORRECTION_REQUIRED', 'CORRECTED')),
    CONSTRAINT chk_loan_review_cycles_end_state
        CHECK (
            (status = 'ACTIVE' AND ended_at IS NULL)
            OR (status <> 'ACTIVE' AND ended_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_loan_review_cycles_active_application
    ON loan_application_review_cycles (loan_application_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_loan_review_cycles_application_status
    ON loan_application_review_cycles (loan_application_id, status, cycle_number DESC);

WITH ordered_recommendations AS (
    SELECT recommendation.id,
           recommendation.loan_application_id,
           recommendation.submitted_at,
           ROW_NUMBER() OVER (
               PARTITION BY recommendation.loan_application_id
               ORDER BY recommendation.submitted_at, recommendation.id
           )::INTEGER AS cycle_number,
           recommendation.recommendation,
           decision.decision,
           decision.decided_at
    FROM review_recommendations recommendation
    LEFT JOIN approval_decisions decision
      ON decision.review_recommendation_id = recommendation.id
)
INSERT INTO loan_application_review_cycles (
    id, loan_application_id, cycle_number, status, started_at, ended_at, created_at, updated_at
)
SELECT gen_random_uuid(),
       ordered.loan_application_id,
       ordered.cycle_number,
       CASE
           WHEN ordered.recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
               OR ordered.decision = 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
               THEN 'CORRECTION_REQUIRED'
           WHEN ordered.decision IN ('APPROVE', 'REJECT') THEN 'COMPLETED'
           WHEN ordered.decision = 'RETURN_TO_LOAN_OFFICER_REVIEW' THEN 'SUPERSEDED'
           ELSE 'ACTIVE'
       END,
       ordered.submitted_at,
       CASE
           WHEN ordered.recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
               THEN ordered.submitted_at
           WHEN ordered.decision IS NOT NULL THEN ordered.decided_at
           ELSE NULL
       END,
       ordered.submitted_at,
       COALESCE(ordered.decided_at, ordered.submitted_at)
FROM ordered_recommendations ordered;

INSERT INTO loan_application_review_cycles (
    id, loan_application_id, cycle_number, status, started_at, ended_at, created_at, updated_at
)
SELECT gen_random_uuid(),
       application.id,
       1,
       'ACTIVE',
       COALESCE(
           (
               SELECT transition.occurred_at
               FROM loan_application_status_transitions transition
               WHERE transition.loan_application_id = application.id
                 AND transition.to_status = 'UNDER_REVIEW'
               ORDER BY transition.sequence_number DESC
               LIMIT 1
           ),
           application.submitted_at
       ),
       NULL,
       application.submitted_at,
       application.updated_at
FROM loan_applications application
WHERE application.status = 'UNDER_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM loan_application_review_cycles cycle
      WHERE cycle.loan_application_id = application.id
  );

INSERT INTO loan_application_review_cycles (
    id, loan_application_id, cycle_number, status, started_at, ended_at, created_at, updated_at
)
SELECT gen_random_uuid(),
       application.id,
       COALESCE(MAX(existing.cycle_number), 0) + 1,
       'ACTIVE',
       COALESCE(
           (
               SELECT decision.decided_at
               FROM approval_decisions decision
               WHERE decision.loan_application_id = application.id
                 AND decision.decision = 'RETURN_TO_LOAN_OFFICER_REVIEW'
               ORDER BY decision.decided_at DESC, decision.id DESC
               LIMIT 1
           ),
           application.updated_at
       ),
       NULL,
       application.updated_at,
       application.updated_at
FROM loan_applications application
LEFT JOIN loan_application_review_cycles existing
  ON existing.loan_application_id = application.id
WHERE application.status = 'RETURNED_TO_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM loan_application_review_cycles active_cycle
      WHERE active_cycle.loan_application_id = application.id
        AND active_cycle.status = 'ACTIVE'
  )
GROUP BY application.id, application.updated_at;

ALTER TABLE review_recommendations
    ADD COLUMN review_cycle_id UUID,
    ADD COLUMN reason_code VARCHAR(80);

WITH ordered_recommendations AS (
    SELECT recommendation.id,
           recommendation.loan_application_id,
           ROW_NUMBER() OVER (
               PARTITION BY recommendation.loan_application_id
               ORDER BY recommendation.submitted_at, recommendation.id
           )::INTEGER AS cycle_number
    FROM review_recommendations recommendation
)
UPDATE review_recommendations recommendation
SET review_cycle_id = cycle.id
FROM ordered_recommendations ordered
JOIN loan_application_review_cycles cycle
  ON cycle.loan_application_id = ordered.loan_application_id
 AND cycle.cycle_number = ordered.cycle_number
WHERE recommendation.id = ordered.id;

ALTER TABLE review_recommendations
    ALTER COLUMN review_cycle_id SET NOT NULL,
    ADD CONSTRAINT fk_review_recommendations_cycle_application
        FOREIGN KEY (review_cycle_id, loan_application_id)
        REFERENCES loan_application_review_cycles (id, loan_application_id),
    ADD CONSTRAINT uq_review_recommendations_cycle UNIQUE (review_cycle_id),
    DROP CONSTRAINT chk_review_recommendations_reason_required,
    ADD CONSTRAINT chk_review_recommendations_reason_contract
        CHECK (
            (
                recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
                AND reason IS NULL
                AND reason_code IS NOT NULL
            )
            OR (
                recommendation NOT IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
                AND reason_code IS NULL
                AND (
                    recommendation = 'RECOMMEND_APPROVAL'
                    OR (reason IS NOT NULL AND btrim(reason) <> '')
                )
            )
        );

ALTER TABLE approval_decisions
    ADD COLUMN reason_code VARCHAR(80),
    DROP CONSTRAINT chk_approval_decisions_reason_required,
    ADD CONSTRAINT uq_approval_decisions_recommendation UNIQUE (review_recommendation_id),
    ADD CONSTRAINT chk_approval_decisions_reason_contract
        CHECK (
            (
                decision = 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
                AND reason IS NULL
                AND reason_code IS NOT NULL
            )
            OR (
                decision <> 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
                AND reason_code IS NULL
                AND (decision = 'APPROVE' OR (reason IS NOT NULL AND btrim(reason) <> ''))
            )
        );

CREATE TABLE loan_correction_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    source_review_cycle_id UUID,
    source_action VARCHAR(60) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    created_by_user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    resubmission_request_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ready_at TIMESTAMP WITHOUT TIME ZONE,
    resubmitted_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_loan_correction_requests_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_correction_requests_cycle_application
        FOREIGN KEY (source_review_cycle_id, loan_application_id)
        REFERENCES loan_application_review_cycles (id, loan_application_id),
    CONSTRAINT fk_loan_correction_requests_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_correction_requests_resubmission
        UNIQUE (resubmission_request_id),
    CONSTRAINT chk_loan_correction_requests_source_action
        CHECK (source_action IN (
            'RETURN_TO_CUSTOMER_REVISION',
            'REQUEST_STAFF_CORRECTION',
            'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
            'REQUEST_REPLACEMENT'
        )),
    CONSTRAINT chk_loan_correction_requests_reason
        CHECK (reason_code IN (
            'SUPPORTING_DOCUMENT_REQUIRED',
            'RECENT_PAYSLIP_REQUIRED',
            'DOCUMENT_REPLACEMENT_REQUIRED',
            'DOCUMENT_REVIEW_REQUIRED'
        )),
    CONSTRAINT chk_loan_correction_requests_status
        CHECK (status IN ('OPEN', 'READY_FOR_RESUBMISSION', 'RESUBMITTED')),
    CONSTRAINT chk_loan_correction_requests_timestamps
        CHECK (
            (status = 'OPEN' AND ready_at IS NULL AND resubmitted_at IS NULL AND resubmission_request_id IS NULL)
            OR (status = 'READY_FOR_RESUBMISSION' AND ready_at IS NOT NULL AND resubmitted_at IS NULL AND resubmission_request_id IS NULL)
            OR (status = 'RESUBMITTED' AND ready_at IS NOT NULL AND resubmitted_at IS NOT NULL AND resubmission_request_id IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_loan_correction_requests_active_application
    ON loan_correction_requests (loan_application_id)
    WHERE status IN ('OPEN', 'READY_FOR_RESUBMISSION');

CREATE INDEX idx_loan_correction_requests_application_status
    ON loan_correction_requests (loan_application_id, status, created_at DESC);

CREATE TABLE loan_correction_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correction_request_id UUID NOT NULL,
    task_sequence INTEGER NOT NULL,
    responsible_party VARCHAR(20) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    document_type VARCHAR(50),
    create_checklist_item BOOLEAN NOT NULL,
    checklist_item_id UUID,
    baseline_document_version_id UUID,
    customer_instruction VARCHAR(500),
    staff_instruction VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    completed_by_user_id UUID,
    completion_request_id UUID,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_loan_correction_tasks_request
        FOREIGN KEY (correction_request_id) REFERENCES loan_correction_requests (id),
    CONSTRAINT fk_loan_correction_tasks_checklist_item
        FOREIGN KEY (checklist_item_id) REFERENCES document_checklist_items (id),
    CONSTRAINT fk_loan_correction_tasks_baseline_version
        FOREIGN KEY (baseline_document_version_id) REFERENCES document_versions (id),
    CONSTRAINT fk_loan_correction_tasks_completed_by
        FOREIGN KEY (completed_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_correction_tasks_request_sequence
        UNIQUE (correction_request_id, task_sequence),
    CONSTRAINT uq_loan_correction_tasks_completion_request
        UNIQUE (completion_request_id),
    CONSTRAINT uq_loan_correction_tasks_tuple
        UNIQUE NULLS NOT DISTINCT (
            correction_request_id,
            responsible_party,
            scope,
            document_type,
            checklist_item_id,
            baseline_document_version_id
        ),
    CONSTRAINT chk_loan_correction_tasks_sequence CHECK (task_sequence > 0),
    CONSTRAINT chk_loan_correction_tasks_responsibility CHECK (responsible_party IN ('CUSTOMER', 'STAFF')),
    CONSTRAINT chk_loan_correction_tasks_scope CHECK (scope IN (
        'SUPPORTING_DOCUMENT_UPLOAD', 'DOCUMENT_REPLACEMENT', 'DOCUMENT_REVIEW'
    )),
    CONSTRAINT chk_loan_correction_tasks_document_type
        CHECK (document_type IS NULL OR document_type = 'RECENT_PAYSLIP'),
    CONSTRAINT chk_loan_correction_tasks_instruction
        CHECK (
            (responsible_party = 'CUSTOMER' AND customer_instruction IS NOT NULL AND btrim(customer_instruction) <> '' AND staff_instruction IS NULL)
            OR (responsible_party = 'STAFF' AND staff_instruction IS NOT NULL AND btrim(staff_instruction) <> '' AND customer_instruction IS NULL)
        ),
    CONSTRAINT chk_loan_correction_tasks_scope_fields
        CHECK (
            (
                scope = 'SUPPORTING_DOCUMENT_UPLOAD'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party = 'CUSTOMER'
                AND create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NULL
            )
            OR (
                scope = 'DOCUMENT_REPLACEMENT'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party = 'CUSTOMER'
                AND NOT create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NOT NULL
            )
            OR (
                scope = 'DOCUMENT_REVIEW'
                AND responsible_party = 'STAFF'
                AND NOT create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NOT NULL
            )
        ),
    CONSTRAINT chk_loan_correction_tasks_status CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT chk_loan_correction_tasks_completion
        CHECK (
            (status = 'OPEN' AND completed_by_user_id IS NULL AND completion_request_id IS NULL AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND completed_by_user_id IS NOT NULL AND completion_request_id IS NOT NULL AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_loan_correction_tasks_customer_queue
    ON loan_correction_tasks (responsible_party, status, created_at, id);

CREATE INDEX idx_loan_correction_tasks_request_order
    ON loan_correction_tasks (correction_request_id, task_sequence, id);

CREATE INDEX idx_document_review_queue_current
    ON document_checklist_items (requirement_status, current_review_decision_id, updated_at, id)
    WHERE requirement_status = 'REQUIRED';

ALTER TABLE salary_advance_verifications
    DROP CONSTRAINT uq_salary_advance_verifications_application,
    ADD COLUMN verification_sequence INTEGER,
    ADD COLUMN correction_request_id UUID,
    ADD CONSTRAINT fk_salary_advance_verifications_correction_request
        FOREIGN KEY (correction_request_id) REFERENCES loan_correction_requests (id);

UPDATE salary_advance_verifications SET verification_sequence = 1;

ALTER TABLE salary_advance_verifications
    ALTER COLUMN verification_sequence SET NOT NULL,
    ADD CONSTRAINT chk_salary_advance_verifications_sequence CHECK (verification_sequence > 0),
    ADD CONSTRAINT uq_salary_advance_verifications_application_sequence
        UNIQUE (loan_application_id, verification_sequence),
    ADD CONSTRAINT uq_salary_advance_verifications_correction_request
        UNIQUE (correction_request_id);

INSERT INTO permissions (id, code, description)
VALUES ('00000000-0000-0000-0000-000000000235', 'loan:correction:own', 'Complete and resubmit own customer corrections'),
       ('00000000-0000-0000-0000-000000000236', 'document:upload:own', 'Upload own correction documents')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('loan:correction:own', 'document:upload:own')
WHERE role.code = 'CUSTOMER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action;

ALTER TABLE loan_application_status_transitions
    ADD CONSTRAINT chk_loan_application_status_transitions_action
        CHECK (action IN (
            'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
            'RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION',
            'REQUEST_STAFF_CORRECTION', 'APPROVE', 'REJECT',
            'RETURN_TO_LOAN_OFFICER_REVIEW', 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
            'RESUBMIT_CORRECTION', 'GENERATE_APPROVED_OFFER', 'ACCEPT_APPROVED_OFFER',
            'DECLINE_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER'
        ));

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_entity_type,
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type
        CHECK (entity_type IN (
            'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
            'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION', 'APPROVAL_DECISION',
            'APPROVED_OFFER', 'DOCUMENT_CHECKLIST', 'DOCUMENT_CHECKLIST_ITEM',
            'DOCUMENT_VERSION', 'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
            'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK', 'SALARY_ADVANCE_VERIFICATION'
        )),
    ADD CONSTRAINT chk_audit_events_action
        CHECK (action IN (
            'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
            'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
            'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
            'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
            'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
            'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
            'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
            'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
            'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED',
            'DOCUMENT_VERSION_UPLOADED', 'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED',
            'DOCUMENT_REPLACEMENT_REQUESTED', 'DOCUMENT_UPLOADS_COMPLETED',
            'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
            'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED',
            'CORRECTION_RESUBMITTED', 'SALARY_ADVANCE_REVALIDATED'
        ));

ALTER TABLE document_review_decisions
    ADD COLUMN correction_reason_code VARCHAR(80),
    ADD COLUMN customer_instruction VARCHAR(500);

ALTER TABLE document_review_decisions
    ADD CONSTRAINT chk_document_review_decisions_correction_contract CHECK (
        (
            outcome = 'REQUEST_REPLACEMENT'
            AND correction_reason_code = 'DOCUMENT_REPLACEMENT_REQUIRED'
            AND customer_instruction IS NOT NULL
            AND char_length(btrim(customer_instruction)) BETWEEN 1 AND 500
        )
        OR (
            outcome <> 'REQUEST_REPLACEMENT'
            AND correction_reason_code IS NULL
            AND customer_instruction IS NULL
        )
    );

-- V24 staff and mixed correction workflows
ALTER TABLE loan_correction_tasks
    DROP CONSTRAINT chk_loan_correction_tasks_scope_fields;

ALTER TABLE loan_correction_tasks
    ADD CONSTRAINT chk_loan_correction_tasks_scope_fields
        CHECK (
            (
                scope = 'SUPPORTING_DOCUMENT_UPLOAD'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party IN ('CUSTOMER', 'STAFF')
                AND create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NULL
            )
            OR (
                scope = 'DOCUMENT_REPLACEMENT'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party = 'CUSTOMER'
                AND NOT create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NOT NULL
            )
            OR (
                scope = 'DOCUMENT_REVIEW'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party = 'STAFF'
                AND NOT create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NOT NULL
            )
        );

CREATE INDEX idx_loan_correction_tasks_staff_queue
    ON loan_correction_tasks (status, created_at, id)
    WHERE responsible_party = 'STAFF';

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000237', 'loan:correction:staff',
     'View, complete, and resubmit authorized staff corrections'),
    ('00000000-0000-0000-0000-000000000238', 'document:upload:staff',
     'Upload documents for explicitly authorized staff correction tasks'),
    ('00000000-0000-0000-0000-000000000239', 'document:waive',
     'Waive a document using an approved controlled reason')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('loan:correction:staff', 'document:waive')
WHERE role.code = 'LOAN_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'document:upload:staff'
WHERE role.code = 'BACK_OFFICE_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- V25 immutable operational contract foundation
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM loan_applications WHERE status IN ('DISBURSEMENT_PENDING', 'DISBURSED')) THEN
        RAISE EXCEPTION 'Contract foundation requires explicit reconciliation of existing post-contract applications.';
    END IF;
END $$;

ALTER TABLE loan_applications
    ADD CONSTRAINT uq_loan_applications_id_customer UNIQUE (id, customer_id);

ALTER TABLE approved_offers
    ADD CONSTRAINT uq_approved_offers_id_application UNIQUE (id, loan_application_id);

ALTER TABLE customer_bank_accounts
    ADD CONSTRAINT uq_customer_bank_accounts_id_customer UNIQUE (id, customer_id);

CREATE TABLE loan_contracts (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    approved_offer_id UUID NOT NULL,
    contract_reference VARCHAR(80) NOT NULL,
    contract_version INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,

    approved_principal NUMERIC(19,2) NOT NULL,
    approved_term_months INTEGER NOT NULL,
    interest_calculation_method VARCHAR(50) NOT NULL,
    flat_monthly_interest_rate NUMERIC(9,6) NOT NULL,
    total_interest NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL,
    total_repayment_amount NUMERIC(19,2) NOT NULL,
    repayment_method VARCHAR(50) NOT NULL,

    customer_id UUID NOT NULL,
    source_bank_account_id UUID NOT NULL,
    bank_code VARCHAR(50) NOT NULL,
    bank_name_snapshot VARCHAR(150) NOT NULL,
    account_holder_name VARCHAR(200) NOT NULL,
    account_number_last_four VARCHAR(4) NOT NULL,
    primary_at_capture BOOLEAN NOT NULL,
    active_at_capture BOOLEAN NOT NULL,
    account_captured_at TIMESTAMP NOT NULL,
    protection_scheme VARCHAR(40) NOT NULL,
    protection_key_id VARCHAR(80) NOT NULL,
    protection_nonce BYTEA NOT NULL,
    protected_account_number BYTEA NOT NULL,
    protection_aad_version VARCHAR(80) NOT NULL,

    preparation_request_id UUID NOT NULL,
    expected_previous_contract_version INTEGER,
    supersession_reason VARCHAR(80),
    prepared_by_user_id UUID NOT NULL,
    prepared_at TIMESTAMP NOT NULL,

    acknowledgment_request_id UUID,
    acknowledged_by_user_id UUID,
    acknowledged_at TIMESTAMP,
    confirmation_request_id UUID,
    confirmed_by_user_id UUID,
    confirmed_at TIMESTAMP,

    supersedes_contract_id UUID,
    superseded_by_user_id UUID,
    superseded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_contracts_application FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_contracts_application_customer FOREIGN KEY (loan_application_id, customer_id)
        REFERENCES loan_applications (id, customer_id),
    CONSTRAINT fk_loan_contracts_offer_application FOREIGN KEY (approved_offer_id, loan_application_id)
        REFERENCES approved_offers (id, loan_application_id),
    CONSTRAINT fk_loan_contracts_source_account_customer FOREIGN KEY (source_bank_account_id, customer_id)
        REFERENCES customer_bank_accounts (id, customer_id),
    CONSTRAINT fk_loan_contracts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_loan_contracts_supersedes FOREIGN KEY (supersedes_contract_id) REFERENCES loan_contracts (id),
    CONSTRAINT fk_loan_contracts_prepared_by FOREIGN KEY (prepared_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_loan_contracts_acknowledged_by FOREIGN KEY (acknowledged_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_loan_contracts_confirmed_by FOREIGN KEY (confirmed_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_loan_contracts_superseded_by FOREIGN KEY (superseded_by_user_id) REFERENCES users (id),

    CONSTRAINT uq_loan_contracts_reference UNIQUE (contract_reference),
    CONSTRAINT uq_loan_contracts_application_version UNIQUE (loan_application_id, contract_version),
    CONSTRAINT uq_loan_contracts_preparation_request UNIQUE (preparation_request_id),
    CONSTRAINT uq_loan_contracts_acknowledgment_request UNIQUE (acknowledgment_request_id),
    CONSTRAINT uq_loan_contracts_confirmation_request UNIQUE (confirmation_request_id),

    CONSTRAINT chk_loan_contracts_version_positive CHECK (contract_version > 0),
    CONSTRAINT chk_loan_contracts_status CHECK (status IN (
        'PREPARED', 'ACKNOWLEDGED', 'READY_FOR_DISBURSEMENT', 'SUPERSEDED'
    )),
    CONSTRAINT chk_loan_contracts_terms CHECK (
        approved_principal > 0 AND approved_term_months > 0
        AND flat_monthly_interest_rate >= 0 AND total_interest >= 0 AND fee_amount >= 0
        AND total_repayment_amount = approved_principal + total_interest + fee_amount
        AND approved_principal = trunc(approved_principal)
        AND total_interest = trunc(total_interest)
        AND fee_amount = trunc(fee_amount)
        AND total_repayment_amount = trunc(total_repayment_amount)
        AND interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL'
        AND repayment_method = 'ON_SALARY_DATE'
    ),
    CONSTRAINT chk_loan_contracts_safe_account_snapshot CHECK (
        btrim(bank_code) <> '' AND btrim(bank_name_snapshot) <> ''
        AND btrim(account_holder_name) <> ''
        AND length(btrim(account_number_last_four)) BETWEEN 1 AND 4
        AND primary_at_capture AND active_at_capture
        AND btrim(protection_scheme) <> '' AND btrim(protection_key_id) <> ''
        AND octet_length(protection_nonce) > 0 AND octet_length(protected_account_number) > 0
        AND btrim(protection_aad_version) <> ''
    ),
    CONSTRAINT chk_loan_contracts_version_origin CHECK (
        (contract_version = 1 AND expected_previous_contract_version IS NULL
            AND supersession_reason IS NULL AND supersedes_contract_id IS NULL)
        OR (contract_version > 1 AND expected_previous_contract_version = contract_version - 1
            AND supersession_reason = 'DISBURSEMENT_ACCOUNT_REFRESH' AND supersedes_contract_id IS NOT NULL)
    ),
    CONSTRAINT chk_loan_contracts_lifecycle CHECK (
        (status = 'PREPARED'
            AND acknowledgment_request_id IS NULL
            AND acknowledged_by_user_id IS NULL
            AND acknowledged_at IS NULL
            AND confirmation_request_id IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL
            AND superseded_by_user_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'ACKNOWLEDGED'
            AND acknowledgment_request_id IS NOT NULL
            AND acknowledged_by_user_id IS NOT NULL
            AND acknowledged_at IS NOT NULL
            AND acknowledged_at >= prepared_at
            AND confirmation_request_id IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL
            AND superseded_by_user_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'READY_FOR_DISBURSEMENT'
            AND acknowledgment_request_id IS NOT NULL
            AND acknowledged_by_user_id IS NOT NULL
            AND acknowledged_at IS NOT NULL
            AND acknowledged_at >= prepared_at
            AND confirmation_request_id IS NOT NULL
            AND confirmed_by_user_id IS NOT NULL
            AND confirmed_at IS NOT NULL
            AND confirmed_at >= acknowledged_at
            AND superseded_by_user_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'SUPERSEDED'
            AND (
                (acknowledgment_request_id IS NULL
                    AND acknowledged_by_user_id IS NULL
                    AND acknowledged_at IS NULL)
                OR
                (acknowledgment_request_id IS NOT NULL
                    AND acknowledged_by_user_id IS NOT NULL
                    AND acknowledged_at IS NOT NULL
                    AND acknowledged_at >= prepared_at)
            )
            AND confirmation_request_id IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL
            AND superseded_by_user_id IS NOT NULL
            AND superseded_at IS NOT NULL
            AND superseded_at >= prepared_at
            AND (acknowledged_at IS NULL OR superseded_at >= acknowledged_at))
    )
);

CREATE UNIQUE INDEX uq_loan_contracts_current_application
    ON loan_contracts (loan_application_id) WHERE status <> 'SUPERSEDED';
CREATE INDEX idx_loan_contracts_application_version_desc
    ON loan_contracts (loan_application_id, contract_version DESC);
CREATE INDEX idx_loan_contracts_source_account
    ON loan_contracts (source_bank_account_id);

CREATE TABLE loan_contract_repayment_items (
    id UUID PRIMARY KEY,
    loan_contract_id UUID NOT NULL,
    source_approved_offer_repayment_item_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,
    principal_due NUMERIC(19,2) NOT NULL,
    interest_due NUMERIC(19,2) NOT NULL,
    fee_due NUMERIC(19,2) NOT NULL,
    total_due NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_contract_repayment_items_contract FOREIGN KEY (loan_contract_id)
        REFERENCES loan_contracts (id),
    CONSTRAINT fk_loan_contract_repayment_items_offer_item FOREIGN KEY (source_approved_offer_repayment_item_id)
        REFERENCES approved_offer_repayment_items (id),
    CONSTRAINT uq_loan_contract_repayment_items_installment UNIQUE (loan_contract_id, installment_number),
    CONSTRAINT uq_loan_contract_repayment_items_source UNIQUE (loan_contract_id, source_approved_offer_repayment_item_id),
    CONSTRAINT chk_loan_contract_repayment_items_valid CHECK (
        installment_number > 0 AND principal_due >= 0 AND interest_due >= 0
        AND fee_due >= 0 AND total_due = principal_due + interest_due + fee_due
        AND principal_due = trunc(principal_due) AND interest_due = trunc(interest_due)
        AND fee_due = trunc(fee_due) AND total_due = trunc(total_due)
    )
);
CREATE INDEX idx_loan_contract_repayment_items_contract ON loan_contract_repayment_items (loan_contract_id);

CREATE OR REPLACE FUNCTION enforce_loan_contract_immutability()
RETURNS trigger AS $$
BEGIN
    IF ROW(
        NEW.id, NEW.loan_application_id, NEW.approved_offer_id, NEW.contract_reference, NEW.contract_version,
        NEW.approved_principal, NEW.approved_term_months, NEW.interest_calculation_method,
        NEW.flat_monthly_interest_rate, NEW.total_interest, NEW.fee_amount, NEW.total_repayment_amount,
        NEW.repayment_method, NEW.customer_id, NEW.source_bank_account_id, NEW.bank_code,
        NEW.bank_name_snapshot, NEW.account_holder_name, NEW.account_number_last_four,
        NEW.primary_at_capture, NEW.active_at_capture, NEW.account_captured_at, NEW.protection_scheme,
        NEW.protection_key_id, NEW.protection_nonce, NEW.protected_account_number, NEW.protection_aad_version,
        NEW.preparation_request_id, NEW.expected_previous_contract_version, NEW.supersession_reason,
        NEW.prepared_by_user_id, NEW.prepared_at, NEW.supersedes_contract_id, NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id, OLD.loan_application_id, OLD.approved_offer_id, OLD.contract_reference, OLD.contract_version,
        OLD.approved_principal, OLD.approved_term_months, OLD.interest_calculation_method,
        OLD.flat_monthly_interest_rate, OLD.total_interest, OLD.fee_amount, OLD.total_repayment_amount,
        OLD.repayment_method, OLD.customer_id, OLD.source_bank_account_id, OLD.bank_code,
        OLD.bank_name_snapshot, OLD.account_holder_name, OLD.account_number_last_four,
        OLD.primary_at_capture, OLD.active_at_capture, OLD.account_captured_at, OLD.protection_scheme,
        OLD.protection_key_id, OLD.protection_nonce, OLD.protected_account_number, OLD.protection_aad_version,
        OLD.preparation_request_id, OLD.expected_previous_contract_version, OLD.supersession_reason,
        OLD.prepared_by_user_id, OLD.prepared_at, OLD.supersedes_contract_id, OLD.created_at
    ) THEN
        RAISE EXCEPTION 'Immutable loan contract snapshot fields cannot be changed';
    END IF;
    IF OLD.status = 'ACKNOWLEDGED' AND ROW(
        NEW.acknowledgment_request_id, NEW.acknowledged_by_user_id, NEW.acknowledged_at
    ) IS DISTINCT FROM ROW(
        OLD.acknowledgment_request_id, OLD.acknowledged_by_user_id, OLD.acknowledged_at
    ) THEN
        RAISE EXCEPTION 'Contract acknowledgment evidence is immutable';
    END IF;
    IF NOT (
        (OLD.status = 'PREPARED' AND NEW.status IN ('ACKNOWLEDGED', 'SUPERSEDED'))
        OR (OLD.status = 'ACKNOWLEDGED' AND NEW.status IN ('READY_FOR_DISBURSEMENT', 'SUPERSEDED'))
    ) THEN
        RAISE EXCEPTION 'Illegal loan contract lifecycle transition';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_contracts_immutable
    BEFORE UPDATE ON loan_contracts FOR EACH ROW EXECUTE FUNCTION enforce_loan_contract_immutability();
CREATE TRIGGER trg_loan_contracts_no_delete
    BEFORE DELETE ON loan_contracts FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();
CREATE TRIGGER trg_loan_contract_repayment_items_immutable
    BEFORE UPDATE OR DELETE ON loan_contract_repayment_items
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE OR REPLACE FUNCTION validate_loan_contract_repayment_reconciliation()
RETURNS trigger AS $$
DECLARE
    target_contract_id UUID;
    contract_row loan_contracts%ROWTYPE;
    item_count BIGINT;
    principal_sum NUMERIC(19,2);
    interest_sum NUMERIC(19,2);
    fee_sum NUMERIC(19,2);
    total_sum NUMERIC(19,2);
BEGIN
    IF TG_TABLE_NAME = 'loan_contracts' THEN
        target_contract_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_contract_id := COALESCE(NEW.loan_contract_id, OLD.loan_contract_id);
    END IF;
    SELECT * INTO contract_row FROM loan_contracts WHERE id = target_contract_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT COUNT(*), COALESCE(SUM(principal_due), 0), COALESCE(SUM(interest_due), 0),
           COALESCE(SUM(fee_due), 0), COALESCE(SUM(total_due), 0)
    INTO item_count, principal_sum, interest_sum, fee_sum, total_sum
    FROM loan_contract_repayment_items WHERE loan_contract_id = target_contract_id;
    IF item_count <> contract_row.approved_term_months
        OR principal_sum <> contract_row.approved_principal
        OR interest_sum <> contract_row.total_interest
        OR fee_sum <> contract_row.fee_amount
        OR total_sum <> contract_row.total_repayment_amount THEN
        RAISE EXCEPTION 'Loan contract repayment items do not reconcile';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_contract_repayment_reconcile_contract
    AFTER INSERT OR UPDATE ON loan_contracts DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_repayment_reconciliation();
CREATE CONSTRAINT TRIGGER trg_loan_contract_repayment_reconcile_items
    AFTER INSERT OR UPDATE OR DELETE ON loan_contract_repayment_items DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_repayment_reconciliation();

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action;
ALTER TABLE loan_application_status_transitions
    ADD CONSTRAINT chk_loan_application_status_transitions_action CHECK (action IN (
        'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
        'RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION',
        'REQUEST_STAFF_CORRECTION', 'APPROVE', 'REJECT', 'RETURN_TO_LOAN_OFFICER_REVIEW',
        'REQUEST_CUSTOMER_OR_STAFF_CORRECTION', 'RESUBMIT_CORRECTION', 'GENERATE_APPROVED_OFFER',
        'ACCEPT_APPROVED_OFFER', 'DECLINE_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER',
        'CONFIRM_DISBURSEMENT_READINESS'
    ));

ALTER TABLE audit_events DROP CONSTRAINT chk_audit_events_entity_type, DROP CONSTRAINT chk_audit_events_action;
ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION', 'SALARY_ADVANCE_LIMIT_MOVEMENT',
        'REVIEW_RECOMMENDATION', 'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION', 'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK', 'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT'
    )),
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED', 'REVIEW_RECOMMENDATION_RECORDED',
        'APPROVAL_DECISION_RECORDED', 'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED', 'LOAN_CONTRACT_READINESS_CONFIRMED'
    ));
ALTER TABLE loan_contracts
    DROP CONSTRAINT fk_loan_contracts_supersedes,
    ADD CONSTRAINT uq_loan_contracts_id_application_version
        UNIQUE (id, loan_application_id, contract_version),
    ADD CONSTRAINT fk_loan_contracts_supersedes_version
        FOREIGN KEY (supersedes_contract_id, loan_application_id, expected_previous_contract_version)
        REFERENCES loan_contracts (id, loan_application_id, contract_version);

CREATE OR REPLACE FUNCTION validate_loan_contract_source_snapshot()
RETURNS trigger AS $$
DECLARE
    target_contract_id UUID;
    contract_row loan_contracts%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME = 'loan_contracts' THEN
        target_contract_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_contract_id := COALESCE(NEW.loan_contract_id, OLD.loan_contract_id);
    END IF;
    SELECT * INTO contract_row FROM loan_contracts WHERE id = target_contract_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM approved_offers offer
        WHERE offer.id = contract_row.approved_offer_id
          AND offer.loan_application_id = contract_row.loan_application_id
          AND offer.status = 'ACCEPTED'
          AND offer.approved_principal = contract_row.approved_principal
          AND offer.approved_term_months = contract_row.approved_term_months
          AND offer.interest_calculation_method = contract_row.interest_calculation_method
          AND offer.flat_monthly_interest_rate = contract_row.flat_monthly_interest_rate
          AND offer.total_interest = contract_row.total_interest
          AND offer.fee_amount = contract_row.fee_amount
          AND offer.total_repayment_amount = contract_row.total_repayment_amount
          AND offer.repayment_method = contract_row.repayment_method
    ) THEN
        RAISE EXCEPTION 'Loan contract terms do not match the accepted approved offer';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM loan_contract_repayment_items item
        LEFT JOIN approved_offer_repayment_items source ON source.id = item.source_approved_offer_repayment_item_id
        WHERE item.loan_contract_id = target_contract_id
          AND (source.id IS NULL OR source.approved_offer_id <> contract_row.approved_offer_id
            OR source.installment_number <> item.installment_number
            OR source.principal_due <> item.principal_due OR source.interest_due <> item.interest_due
            OR source.fee_due <> item.fee_due OR source.total_due <> item.total_due)
    ) THEN
        RAISE EXCEPTION 'Loan contract repayment snapshot does not match the accepted approved offer';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_contract_source_contract
    AFTER INSERT OR UPDATE ON loan_contracts DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_source_snapshot();
CREATE CONSTRAINT TRIGGER trg_loan_contract_source_items
    AFTER INSERT OR UPDATE OR DELETE ON loan_contract_repayment_items DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_source_snapshot();

CREATE OR REPLACE FUNCTION validate_loan_contract_application_lifecycle()
RETURNS trigger AS $$
DECLARE
    target_application_id UUID;
    application_status VARCHAR(50);
    ready_contract_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'loan_applications' THEN
        target_application_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    END IF;
    SELECT status INTO application_status FROM loan_applications WHERE id = target_application_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT COUNT(*) INTO ready_contract_count FROM loan_contracts
    WHERE loan_application_id = target_application_id AND status = 'READY_FOR_DISBURSEMENT';
    IF application_status = 'DISBURSEMENT_PENDING' AND ready_contract_count <> 1 THEN
        RAISE EXCEPTION 'Disbursement-pending application requires one ready current contract';
    END IF;
    IF ready_contract_count > 0 AND application_status <> 'DISBURSEMENT_PENDING' THEN
        RAISE EXCEPTION 'Ready contract requires disbursement-pending application';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_contract_application_consistency_contract
    AFTER INSERT OR UPDATE ON loan_contracts DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_application_lifecycle();
CREATE CONSTRAINT TRIGGER trg_loan_contract_application_consistency_application
    AFTER INSERT OR UPDATE ON loan_applications DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_application_lifecycle();

-- V26 contract-readiness permissions
INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000240', 'loan:contract:acknowledge:own',
     'Acknowledge the current operational loan contract'),
    ('00000000-0000-0000-0000-000000000241', 'loan:contract:prepare',
     'Prepare or regenerate an operational loan contract'),
    ('00000000-0000-0000-0000-000000000242', 'loan:contract:read',
     'Read operational loan contracts and readiness'),
    ('00000000-0000-0000-0000-000000000243', 'loan:disbursement:prepare',
     'Confirm contract readiness before disbursement')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'loan:contract:acknowledge:own'
WHERE role.code = 'CUSTOMER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN (
    'loan:contract:prepare',
    'loan:contract:read',
    'loan:disbursement:prepare'
)
WHERE role.code = 'ACCOUNTING_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- V28 manual disbursement and LoanAccount activation physical foundation
ALTER TABLE loan_contracts
    ADD CONSTRAINT uq_loan_contracts_id_application_customer
        UNIQUE (id, loan_application_id, customer_id);

CREATE TABLE loan_accounts (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_contract_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    account_number VARCHAR(35) NOT NULL,
    status VARCHAR(30) NOT NULL,

    approved_principal NUMERIC(19,2) NOT NULL,
    approved_term_months INTEGER NOT NULL,
    total_interest NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL,
    total_repayment_amount NUMERIC(19,2) NOT NULL,

    activated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_accounts_application_customer
        FOREIGN KEY (loan_application_id, customer_id)
        REFERENCES loan_applications (id, customer_id),
    CONSTRAINT fk_loan_accounts_contract_application_customer
        FOREIGN KEY (loan_contract_id, loan_application_id, customer_id)
        REFERENCES loan_contracts (id, loan_application_id, customer_id),
    CONSTRAINT fk_loan_accounts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),

    CONSTRAINT uq_loan_accounts_application UNIQUE (loan_application_id),
    CONSTRAINT uq_loan_accounts_contract UNIQUE (loan_contract_id),
    CONSTRAINT uq_loan_accounts_account_number UNIQUE (account_number),
    CONSTRAINT uq_loan_accounts_id_application_contract
        UNIQUE (id, loan_application_id, loan_contract_id),

    CONSTRAINT chk_loan_accounts_account_number CHECK (
        account_number ~ '^LA-[0-9A-F]{32}$'
        AND account_number = 'LA-' || upper(replace(id::text, '-', ''))
    ),
    CONSTRAINT chk_loan_accounts_status CHECK (
        status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED')
    ),
    CONSTRAINT chk_loan_accounts_terms CHECK (
        approved_principal > 0
        AND approved_term_months > 0
        AND total_interest >= 0
        AND fee_amount >= 0
        AND total_repayment_amount = approved_principal + total_interest + fee_amount
        AND approved_principal = trunc(approved_principal)
        AND total_interest = trunc(total_interest)
        AND fee_amount = trunc(fee_amount)
        AND total_repayment_amount = trunc(total_repayment_amount)
    )
);

CREATE INDEX idx_loan_accounts_customer_id
    ON loan_accounts (customer_id);

CREATE TABLE manual_disbursements (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_contract_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    request_id UUID NOT NULL,
    expected_contract_version INTEGER NOT NULL,
    external_transfer_reference VARCHAR(64) NOT NULL,
    disbursed_amount NUMERIC(19,2) NOT NULL,
    disbursement_value_date DATE NOT NULL,
    first_repayment_date DATE NOT NULL,
    confirmed_by_user_id UUID NOT NULL,
    confirmed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_manual_disbursements_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_manual_disbursements_contract_version
        FOREIGN KEY (loan_contract_id, loan_application_id, expected_contract_version)
        REFERENCES loan_contracts (id, loan_application_id, contract_version),
    CONSTRAINT fk_manual_disbursements_account_application_contract
        FOREIGN KEY (loan_account_id, loan_application_id, loan_contract_id)
        REFERENCES loan_accounts (id, loan_application_id, loan_contract_id),
    CONSTRAINT fk_manual_disbursements_confirmed_by
        FOREIGN KEY (confirmed_by_user_id)
        REFERENCES users (id),

    CONSTRAINT uq_manual_disbursements_application UNIQUE (loan_application_id),
    CONSTRAINT uq_manual_disbursements_contract UNIQUE (loan_contract_id),
    CONSTRAINT uq_manual_disbursements_account UNIQUE (loan_account_id),
    CONSTRAINT uq_manual_disbursements_request UNIQUE (request_id),
    CONSTRAINT uq_manual_disbursements_transfer_reference UNIQUE (external_transfer_reference),

    CONSTRAINT chk_manual_disbursements_contract_version
        CHECK (expected_contract_version > 0),
    CONSTRAINT chk_manual_disbursements_transfer_reference CHECK (
        external_transfer_reference = btrim(external_transfer_reference)
        AND external_transfer_reference ~ '^[A-Z0-9][A-Z0-9._:/-]{0,63}$'
    ),
    CONSTRAINT chk_manual_disbursements_amount CHECK (
        disbursed_amount > 0
        AND disbursed_amount = trunc(disbursed_amount)
    ),
    CONSTRAINT chk_manual_disbursements_repayment_dates CHECK (
        first_repayment_date > disbursement_value_date
        AND first_repayment_date <= (disbursement_value_date + INTERVAL '1 month')::date
    )
);

CREATE TABLE repayment_schedules (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_contract_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    schedule_type VARCHAR(20) NOT NULL,
    version INTEGER NOT NULL,
    approved_term_months INTEGER NOT NULL,
    approved_principal NUMERIC(19,2) NOT NULL,
    total_interest NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL,
    total_repayment_amount NUMERIC(19,2) NOT NULL,
    first_due_date DATE NOT NULL,
    last_due_date DATE NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_schedules_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_repayment_schedules_contract
        FOREIGN KEY (loan_contract_id)
        REFERENCES loan_contracts (id),
    CONSTRAINT fk_repayment_schedules_account_application_contract
        FOREIGN KEY (loan_account_id, loan_application_id, loan_contract_id)
        REFERENCES loan_accounts (id, loan_application_id, loan_contract_id),

    CONSTRAINT uq_repayment_schedules_application UNIQUE (loan_application_id),
    CONSTRAINT uq_repayment_schedules_contract UNIQUE (loan_contract_id),
    CONSTRAINT uq_repayment_schedules_account UNIQUE (loan_account_id),

    CONSTRAINT chk_repayment_schedules_type_version CHECK (
        schedule_type = 'FINAL' AND version = 1
    ),
    CONSTRAINT chk_repayment_schedules_terms CHECK (
        approved_term_months > 0
        AND approved_principal > 0
        AND total_interest >= 0
        AND fee_amount >= 0
        AND total_repayment_amount = approved_principal + total_interest + fee_amount
        AND approved_principal = trunc(approved_principal)
        AND total_interest = trunc(total_interest)
        AND fee_amount = trunc(fee_amount)
        AND total_repayment_amount = trunc(total_repayment_amount)
        AND last_due_date >= first_due_date
    )
);

CREATE TABLE repayment_schedule_items (
    id UUID PRIMARY KEY,
    repayment_schedule_id UUID NOT NULL,
    source_loan_contract_repayment_item_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_due NUMERIC(19,2) NOT NULL,
    interest_due NUMERIC(19,2) NOT NULL,
    fee_due NUMERIC(19,2) NOT NULL,
    total_due NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_schedule_items_schedule
        FOREIGN KEY (repayment_schedule_id)
        REFERENCES repayment_schedules (id),
    CONSTRAINT fk_repayment_schedule_items_contract_item
        FOREIGN KEY (source_loan_contract_repayment_item_id)
        REFERENCES loan_contract_repayment_items (id),

    CONSTRAINT uq_repayment_schedule_items_schedule_installment
        UNIQUE (repayment_schedule_id, installment_number),
    CONSTRAINT uq_repayment_schedule_items_source_contract_item
        UNIQUE (source_loan_contract_repayment_item_id),

    CONSTRAINT chk_repayment_schedule_items_valid CHECK (
        installment_number > 0
        AND principal_due >= 0
        AND interest_due >= 0
        AND fee_due >= 0
        AND total_due >= 0
        AND total_due = principal_due + interest_due + fee_due
        AND principal_due = trunc(principal_due)
        AND interest_due = trunc(interest_due)
        AND fee_due = trunc(fee_due)
        AND total_due = trunc(total_due)
    )
);

CREATE INDEX idx_repayment_schedule_items_schedule
    ON repayment_schedule_items (repayment_schedule_id);

ALTER TABLE salary_advance_limit_movements
    ADD CONSTRAINT fk_salary_advance_limit_movements_loan_account
        FOREIGN KEY (loan_account_id)
        REFERENCES loan_accounts (id),
    ADD CONSTRAINT chk_salary_advance_limit_movements_disbursed_to_used CHECK (
        movement_type <> 'DISBURSED_TO_USED'
        OR (
            loan_application_id IS NOT NULL
            AND loan_account_id IS NOT NULL
            AND amount > 0
            AND amount = trunc(amount)
        )
    );

CREATE UNIQUE INDEX uq_salary_advance_limit_movements_application_disbursed_to_used
    ON salary_advance_limit_movements (loan_application_id)
    WHERE movement_type = 'DISBURSED_TO_USED'
      AND loan_application_id IS NOT NULL;

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action;

ALTER TABLE loan_application_status_transitions
    ADD CONSTRAINT chk_loan_application_status_transitions_action CHECK (action IN (
        'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
        'RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION',
        'REQUEST_STAFF_CORRECTION', 'APPROVE', 'REJECT', 'RETURN_TO_LOAN_OFFICER_REVIEW',
        'REQUEST_CUSTOMER_OR_STAFF_CORRECTION', 'RESUBMIT_CORRECTION', 'GENERATE_APPROVED_OFFER',
        'ACCEPT_APPROVED_OFFER', 'DECLINE_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER',
        'CONFIRM_DISBURSEMENT_READINESS', 'CONFIRM_MANUAL_DISBURSEMENT'
    ));

CREATE OR REPLACE FUNCTION require_active_loan_account_insert()
RETURNS trigger AS $$
BEGIN
    IF NEW.status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'New Loan Account must be ACTIVE';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_accounts_active_insert
    BEFORE INSERT ON loan_accounts
    FOR EACH ROW EXECUTE FUNCTION require_active_loan_account_insert();

CREATE OR REPLACE FUNCTION enforce_loan_account_mutation_boundary()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Loan Account rows cannot be deleted';
    END IF;

    IF ROW(
        NEW.id,
        NEW.loan_application_id,
        NEW.loan_contract_id,
        NEW.customer_id,
        NEW.account_number,
        NEW.approved_principal,
        NEW.approved_term_months,
        NEW.total_interest,
        NEW.fee_amount,
        NEW.total_repayment_amount,
        NEW.activated_at,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id,
        OLD.loan_application_id,
        OLD.loan_contract_id,
        OLD.customer_id,
        OLD.account_number,
        OLD.approved_principal,
        OLD.approved_term_months,
        OLD.total_interest,
        OLD.fee_amount,
        OLD.total_repayment_amount,
        OLD.activated_at,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION 'Loan Account immutable source and financial fields cannot be changed';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_accounts_immutable
    BEFORE UPDATE OR DELETE ON loan_accounts
    FOR EACH ROW EXECUTE FUNCTION enforce_loan_account_mutation_boundary();

CREATE TRIGGER trg_manual_disbursements_immutable
    BEFORE UPDATE OR DELETE ON manual_disbursements
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_schedules_immutable
    BEFORE UPDATE OR DELETE ON repayment_schedules
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_schedule_items_immutable
    BEFORE UPDATE OR DELETE ON repayment_schedule_items
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE OR REPLACE FUNCTION validate_repayment_schedule_reconciliation()
RETURNS trigger AS $$
DECLARE
    target_schedule_id UUID;
    schedule_row repayment_schedules%ROWTYPE;
    item_count BIGINT;
    source_count BIGINT;
    minimum_installment INTEGER;
    maximum_installment INTEGER;
    first_item_due_date DATE;
    last_item_due_date DATE;
    principal_sum NUMERIC(19,2);
    interest_sum NUMERIC(19,2);
    fee_sum NUMERIC(19,2);
    total_sum NUMERIC(19,2);
    non_increasing_dates BIGINT;
    source_mismatches BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'repayment_schedules' THEN
        target_schedule_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_schedule_id := COALESCE(NEW.repayment_schedule_id, OLD.repayment_schedule_id);
    END IF;

    SELECT *
    INTO schedule_row
    FROM repayment_schedules
    WHERE id = target_schedule_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT
        COUNT(*),
        COUNT(DISTINCT source_loan_contract_repayment_item_id),
        MIN(installment_number),
        MAX(installment_number),
        MIN(due_date) FILTER (WHERE installment_number = 1),
        MAX(due_date) FILTER (WHERE installment_number = schedule_row.approved_term_months),
        COALESCE(SUM(principal_due), 0),
        COALESCE(SUM(interest_due), 0),
        COALESCE(SUM(fee_due), 0),
        COALESCE(SUM(total_due), 0)
    INTO
        item_count,
        source_count,
        minimum_installment,
        maximum_installment,
        first_item_due_date,
        last_item_due_date,
        principal_sum,
        interest_sum,
        fee_sum,
        total_sum
    FROM repayment_schedule_items
    WHERE repayment_schedule_id = target_schedule_id;

    SELECT COUNT(*)
    INTO non_increasing_dates
    FROM (
        SELECT
            due_date,
            lag(due_date) OVER (ORDER BY installment_number) AS previous_due_date
        FROM repayment_schedule_items
        WHERE repayment_schedule_id = target_schedule_id
    ) ordered_items
    WHERE previous_due_date IS NOT NULL
      AND due_date <= previous_due_date;

    SELECT COUNT(*)
    INTO source_mismatches
    FROM repayment_schedule_items item
    LEFT JOIN loan_contract_repayment_items source_item
        ON source_item.id = item.source_loan_contract_repayment_item_id
    WHERE item.repayment_schedule_id = target_schedule_id
      AND (
          source_item.id IS NULL
          OR source_item.loan_contract_id <> schedule_row.loan_contract_id
          OR source_item.installment_number <> item.installment_number
          OR source_item.principal_due <> item.principal_due
          OR source_item.interest_due <> item.interest_due
          OR source_item.fee_due <> item.fee_due
          OR source_item.total_due <> item.total_due
      );

    IF item_count <> schedule_row.approved_term_months
        OR source_count <> item_count
        OR minimum_installment <> 1
        OR maximum_installment <> schedule_row.approved_term_months
        OR first_item_due_date <> schedule_row.first_due_date
        OR last_item_due_date <> schedule_row.last_due_date
        OR non_increasing_dates <> 0
        OR source_mismatches <> 0
        OR principal_sum <> schedule_row.approved_principal
        OR interest_sum <> schedule_row.total_interest
        OR fee_sum <> schedule_row.fee_amount
        OR total_sum <> schedule_row.total_repayment_amount THEN
        RAISE EXCEPTION 'Final repayment schedule does not reconcile to its source contract';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_repayment_schedule_reconcile_schedule
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedules
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_schedule_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_schedule_reconcile_items
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedule_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_schedule_reconciliation();

CREATE OR REPLACE FUNCTION validate_loan_activation_foundation()
RETURNS trigger AS $$
DECLARE
    target_application_id UUID;
    account_row loan_accounts%ROWTYPE;
    disbursement_row manual_disbursements%ROWTYPE;
    schedule_row repayment_schedules%ROWTYPE;
    account_count BIGINT;
    disbursement_count BIGINT;
    schedule_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'loan_accounts' THEN
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    ELSIF TG_TABLE_NAME = 'manual_disbursements' THEN
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    ELSIF TG_TABLE_NAME = 'repayment_schedules' THEN
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    ELSE
        SELECT loan_application_id
        INTO target_application_id
        FROM repayment_schedules
        WHERE id = COALESCE(NEW.repayment_schedule_id, OLD.repayment_schedule_id);
    END IF;

    IF target_application_id IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*)
    INTO account_count
    FROM loan_accounts
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO disbursement_count
    FROM manual_disbursements
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO schedule_count
    FROM repayment_schedules
    WHERE loan_application_id = target_application_id;

    IF account_count = 0 AND disbursement_count = 0 AND schedule_count = 0 THEN
        RETURN NULL;
    END IF;

    IF account_count <> 1 OR disbursement_count <> 1 OR schedule_count <> 1 THEN
        RAISE EXCEPTION 'Loan activation foundation evidence is incomplete';
    END IF;

    SELECT *
    INTO account_row
    FROM loan_accounts
    WHERE loan_application_id = target_application_id;

    SELECT *
    INTO disbursement_row
    FROM manual_disbursements
    WHERE loan_application_id = target_application_id;

    SELECT *
    INTO schedule_row
    FROM repayment_schedules
    WHERE loan_application_id = target_application_id;

    IF NOT EXISTS (
        SELECT 1
        FROM loan_contracts contract_row
        WHERE contract_row.id = account_row.loan_contract_id
          AND contract_row.loan_application_id = account_row.loan_application_id
          AND contract_row.customer_id = account_row.customer_id
          AND contract_row.status = 'READY_FOR_DISBURSEMENT'
          AND contract_row.approved_principal = account_row.approved_principal
          AND contract_row.approved_term_months = account_row.approved_term_months
          AND contract_row.total_interest = account_row.total_interest
          AND contract_row.fee_amount = account_row.fee_amount
          AND contract_row.total_repayment_amount = account_row.total_repayment_amount
    ) THEN
        RAISE EXCEPTION 'Loan Account does not match its ready contract';
    END IF;

    IF disbursement_row.loan_contract_id <> account_row.loan_contract_id
        OR disbursement_row.loan_account_id <> account_row.id
        OR disbursement_row.disbursed_amount <> account_row.approved_principal THEN
        RAISE EXCEPTION 'Manual disbursement evidence does not match the Loan Account';
    END IF;

    IF schedule_row.loan_contract_id <> account_row.loan_contract_id
        OR schedule_row.loan_account_id <> account_row.id
        OR schedule_row.approved_term_months <> account_row.approved_term_months
        OR schedule_row.approved_principal <> account_row.approved_principal
        OR schedule_row.total_interest <> account_row.total_interest
        OR schedule_row.fee_amount <> account_row.fee_amount
        OR schedule_row.total_repayment_amount <> account_row.total_repayment_amount
        OR schedule_row.first_due_date <> disbursement_row.first_repayment_date
        OR schedule_row.first_due_date <= disbursement_row.disbursement_value_date THEN
        RAISE EXCEPTION 'Final repayment schedule header does not match the Loan Account';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_account
    AFTER INSERT OR UPDATE OR DELETE ON loan_accounts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_disbursement
    AFTER INSERT OR UPDATE OR DELETE ON manual_disbursements
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_schedule
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedules
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_schedule_item
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedule_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE OR REPLACE FUNCTION reject_disbursed_to_used_movement_mutation()
RETURNS trigger AS $$
BEGIN
    IF OLD.movement_type = 'DISBURSED_TO_USED' THEN
        RAISE EXCEPTION 'Disbursed-to-used Salary Advance movement is immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.movement_type = 'DISBURSED_TO_USED' THEN
        RAISE EXCEPTION 'Disbursed-to-used Salary Advance movement must be inserted as new evidence';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_salary_advance_disbursed_to_used_immutable
    BEFORE UPDATE OR DELETE ON salary_advance_limit_movements
    FOR EACH ROW EXECUTE FUNCTION reject_disbursed_to_used_movement_mutation();

CREATE OR REPLACE FUNCTION validate_salary_advance_conversion()
RETURNS trigger AS $$
DECLARE
    target_limit_id UUID;
    conversion_count BIGINT;
    expected_reserved NUMERIC(19,2);
    expected_used NUMERIC(19,2);
    limit_row salary_advance_limits%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME = 'salary_advance_limits' THEN
        target_limit_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_limit_id := COALESCE(NEW.salary_advance_limit_id, OLD.salary_advance_limit_id);
    END IF;

    SELECT COUNT(*)
    INTO conversion_count
    FROM salary_advance_limit_movements
    WHERE salary_advance_limit_id = target_limit_id
      AND movement_type = 'DISBURSED_TO_USED';

    IF conversion_count = 0 THEN
        RETURN NULL;
    END IF;

    SELECT *
    INTO limit_row
    FROM salary_advance_limits
    WHERE id = target_limit_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements conversion
        JOIN loan_accounts account_row
            ON account_row.id = conversion.loan_account_id
        WHERE conversion.salary_advance_limit_id = target_limit_id
          AND conversion.movement_type = 'DISBURSED_TO_USED'
          AND (
              account_row.loan_application_id <> conversion.loan_application_id
              OR account_row.customer_id <> limit_row.customer_id
              OR account_row.approved_principal <> conversion.amount
              OR (
                  SELECT COUNT(*)
                  FROM salary_advance_limit_movements reservation
                  WHERE reservation.loan_application_id = conversion.loan_application_id
                    AND reservation.salary_advance_limit_id = conversion.salary_advance_limit_id
                    AND reservation.movement_type = 'RESERVED'
                    AND reservation.amount = conversion.amount
              ) <> 1
              OR EXISTS (
                  SELECT 1
                  FROM salary_advance_limit_movements release
                  WHERE release.loan_application_id = conversion.loan_application_id
                    AND release.movement_type = 'RESERVATION_RELEASED'
              )
          )
    ) THEN
        RAISE EXCEPTION 'Salary Advance disbursed-to-used movement does not match reservation evidence';
    END IF;

    SELECT
        COALESCE(SUM(
            CASE
                WHEN movement_type = 'RESERVED' THEN amount
                WHEN movement_type IN ('RESERVATION_RELEASED', 'DISBURSED_TO_USED') THEN -amount
                ELSE 0
            END
        ), 0),
        COALESCE(SUM(
            CASE
                WHEN movement_type = 'DISBURSED_TO_USED' THEN amount
                WHEN movement_type = 'REPAID_RELEASED' THEN -amount
                ELSE 0
            END
        ), 0)
    INTO expected_reserved, expected_used
    FROM salary_advance_limit_movements
    WHERE salary_advance_limit_id = target_limit_id;

    IF limit_row.reserved_amount <> expected_reserved
        OR limit_row.used_amount <> expected_used
        OR limit_row.available_amount
            <> limit_row.total_limit - limit_row.used_amount - limit_row.reserved_amount THEN
        RAISE EXCEPTION 'Salary Advance limit does not reconcile after exposure conversion';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_salary_advance_conversion_movement
    AFTER INSERT OR UPDATE OR DELETE ON salary_advance_limit_movements
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_salary_advance_conversion();

CREATE CONSTRAINT TRIGGER trg_salary_advance_conversion_limit
    AFTER UPDATE ON salary_advance_limits
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_salary_advance_conversion();

CREATE OR REPLACE FUNCTION validate_loan_contract_application_lifecycle()
RETURNS trigger AS $$
DECLARE
    target_application_id UUID;
    application_status VARCHAR(50);
    ready_contract_count BIGINT;
    loan_account_count BIGINT;
    manual_disbursement_count BIGINT;
    final_schedule_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'loan_applications' THEN
        target_application_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    END IF;

    SELECT status
    INTO application_status
    FROM loan_applications
    WHERE id = target_application_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*)
    INTO ready_contract_count
    FROM loan_contracts
    WHERE loan_application_id = target_application_id
      AND status = 'READY_FOR_DISBURSEMENT';

    SELECT COUNT(*)
    INTO loan_account_count
    FROM loan_accounts
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO manual_disbursement_count
    FROM manual_disbursements
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO final_schedule_count
    FROM repayment_schedules
    WHERE loan_application_id = target_application_id
      AND schedule_type = 'FINAL'
      AND version = 1;

    IF application_status = 'DISBURSEMENT_PENDING'
        AND ready_contract_count <> 1 THEN
        RAISE EXCEPTION 'Disbursement-pending application requires one ready current contract';
    END IF;

    IF application_status = 'DISBURSED'
        AND (
            ready_contract_count <> 1
            OR loan_account_count <> 1
            OR manual_disbursement_count <> 1
            OR final_schedule_count <> 1
        ) THEN
        RAISE EXCEPTION 'Disbursed application requires complete activation evidence';
    END IF;

    IF ready_contract_count > 0
        AND application_status NOT IN ('DISBURSEMENT_PENDING', 'DISBURSED') THEN
        RAISE EXCEPTION 'Ready contract requires a disbursement-stage application';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- V29 manual disbursement audit action

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED', 'REVIEW_RECOMMENDATION_RECORDED',
        'APPROVAL_DECISION_RECORDED', 'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED', 'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED'
    ));

-- V30 immutable Loan Application product identity

ALTER TABLE loan_products
    ADD CONSTRAINT uq_loan_products_identity_tuple
        UNIQUE (id, product_code, product_type);

ALTER TABLE loan_applications
    ADD CONSTRAINT fk_loan_applications_product_identity
        FOREIGN KEY (loan_product_id, product_code, product_type)
        REFERENCES loan_products (id, product_code, product_type)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION reject_loan_application_product_identity_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.loan_product_id IS DISTINCT FROM OLD.loan_product_id
            OR NEW.product_code IS DISTINCT FROM OLD.product_code
            OR NEW.product_type IS DISTINCT FROM OLD.product_type THEN
        RAISE EXCEPTION 'Loan Application product identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_loan_applications_product_identity_immutable
BEFORE UPDATE OF loan_product_id, product_code, product_type ON loan_applications
FOR EACH ROW
EXECUTE FUNCTION reject_loan_application_product_identity_mutation();

-- V31 destination-reveal audit action

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED', 'REVIEW_RECOMMENDATION_RECORDED',
        'APPROVAL_DECISION_RECORDED', 'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED', 'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'
    ));

-- V32 repayment servicing audit actions

DO $$
DECLARE
    expected_actions CONSTANT TEXT[] := ARRAY[
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED',
        'DOCUMENT_VERSION_UPLOADED', 'DOCUMENT_REVIEW_ACCEPTED',
        'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED',
        'REVIEW_CYCLE_STATE_CHANGED', 'CORRECTION_REQUEST_CREATED',
        'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED',
        'LOAN_CONTRACT_SUPERSEDED', 'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED', 'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'
    ]::TEXT[];
    actual_actions TEXT[];
    matching_constraint_count INTEGER;
    constraint_expression TEXT;
    expected_constraint_expression TEXT;
    expected_action_sql TEXT;
    normalized_constraint_expression TEXT;
    normalized_expected_expression TEXT;
BEGIN
    SELECT count(*)
    INTO matching_constraint_count
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_action'
      AND constraint_row.contype = 'c';

    IF matching_constraint_count <> 1 THEN
        RAISE EXCEPTION
            'V32 preflight failed: expected V31 audit action constraint is missing or incompatible';
    END IF;

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_action'
      AND constraint_row.contype = 'c';

    SELECT array_agg(matched.value[1] ORDER BY matched.value[1])
    INTO actual_actions
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    CROSS JOIN LATERAL regexp_matches(
        pg_get_constraintdef(constraint_row.oid),
        '''([A-Z][A-Z0-9_]*)''',
        'g'
    ) AS matched(value)
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_action'
      AND constraint_row.contype = 'c';

    IF actual_actions IS NULL
            OR cardinality(actual_actions) <> cardinality(expected_actions)
            OR EXISTS (
                SELECT expected.action
                FROM unnest(expected_actions) AS expected(action)
                EXCEPT
                SELECT actual.action
                FROM unnest(actual_actions) AS actual(action)
            )
            OR EXISTS (
                SELECT actual.action
                FROM unnest(actual_actions) AS actual(action)
                EXCEPT
                SELECT expected.action
                FROM unnest(expected_actions) AS expected(action)
            ) THEN
        RAISE EXCEPTION
            'V32 preflight failed: audit action constraint does not match expected V31 state';
    END IF;

    SELECT string_agg(quote_literal(expected.action), ', ' ORDER BY expected.ordinality)
    INTO expected_action_sql
    FROM unnest(expected_actions) WITH ORDINALITY AS expected(action, ordinality);

    EXECUTE format(
        'CREATE TEMP TABLE v32_expected_audit_action_constraint ('
        'action VARCHAR(100) NOT NULL, '
        'CONSTRAINT chk_v32_expected_audit_action CHECK (action IN (%s))'
        ') ON COMMIT DROP',
        expected_action_sql
    );

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO expected_constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v32_expected_audit_action_constraint'
      AND constraint_row.conname = 'chk_v32_expected_audit_action'
      AND constraint_row.contype = 'c';

    normalized_constraint_expression := regexp_replace(
        regexp_replace(
            constraint_expression,
            '''[A-Z][A-Z0-9_]*''',
            '''__ACTION__''',
            'g'
        ),
        '[[:space:]]+',
        '',
        'g'
    );
    normalized_expected_expression := regexp_replace(
        regexp_replace(
            expected_constraint_expression,
            '''[A-Z][A-Z0-9_]*''',
            '''__ACTION__''',
            'g'
        ),
        '[[:space:]]+',
        '',
        'g'
    );

    normalized_constraint_expression := replace(
        regexp_replace(
            translate(normalized_constraint_expression, '()', ''),
            '''__ACTION__''::charactervarying(::text)?',
            '''__ACTION__''',
            'g'
        ),
        '::text[]',
        ''
    );
    normalized_expected_expression := replace(
        regexp_replace(
            translate(normalized_expected_expression, '()', ''),
            '''__ACTION__''::charactervarying(::text)?',
            '''__ACTION__''',
            'g'
        ),
        '::text[]',
        ''
    );

    IF normalized_constraint_expression IS DISTINCT FROM normalized_expected_expression THEN
        RAISE EXCEPTION
            'V32 preflight failed: incompatible audit action constraint predicate';
    END IF;

    DROP TABLE v32_expected_audit_action_constraint;
END;
$$;

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED',
        'DOCUMENT_VERSION_UPLOADED', 'DOCUMENT_REVIEW_ACCEPTED',
        'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED',
        'REVIEW_CYCLE_STATE_CHANGED', 'CORRECTION_REQUEST_CREATED',
        'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED',
        'LOAN_CONTRACT_SUPERSEDED', 'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED', 'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED', 'LOAN_ACCOUNT_STATUS_CHANGED'
    ));

-- V33 repayment servicing and exposure-release physical foundation

DO $$
DECLARE
    unexpected_table TEXT;
    incompatible_account_id UUID;
    incompatible_limit_id UUID;
    permission_count INTEGER;
    accounting_grant_count INTEGER;
BEGIN
    SELECT candidate.table_name
    INTO unexpected_table
    FROM unnest(ARRAY[
        'repayment_transactions',
        'repayment_allocations',
        'repayment_installment_progress',
        'loan_account_status_transitions',
        'repayment_installment_status_transitions'
    ]) AS candidate(table_name)
    WHERE to_regclass(current_schema() || '.' || candidate.table_name) IS NOT NULL
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: repayment table % unexpectedly exists',
            unexpected_table
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements
        WHERE movement_type = 'REPAID_RELEASED'
    ) THEN
        RAISE EXCEPTION
            'V33 preflight failed: REPAID_RELEASED movement lacks repayment evidence'
            USING ERRCODE = '23514';
    END IF;

    SELECT account_row.id
    INTO incompatible_account_id
    FROM loan_accounts account_row
    JOIN loan_applications application_row
        ON application_row.id = account_row.loan_application_id
    WHERE application_row.product_code <> 'SALARY_ADVANCE'
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: unsupported-product activated LoanAccount %',
            incompatible_account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT id
    INTO incompatible_account_id
    FROM loan_accounts
    WHERE status IN ('SETTLED', 'CLOSED')
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: settled or closed LoanAccount % has no servicing evidence',
            incompatible_account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT account_row.id
    INTO incompatible_account_id
    FROM loan_accounts account_row
    LEFT JOIN loan_applications application_row
        ON application_row.id = account_row.loan_application_id
    LEFT JOIN loan_contracts contract_row
        ON contract_row.id = account_row.loan_contract_id
       AND contract_row.loan_application_id = account_row.loan_application_id
       AND contract_row.customer_id = account_row.customer_id
    LEFT JOIN manual_disbursements disbursement_row
        ON disbursement_row.loan_account_id = account_row.id
       AND disbursement_row.loan_application_id = account_row.loan_application_id
       AND disbursement_row.loan_contract_id = account_row.loan_contract_id
    LEFT JOIN repayment_schedules schedule_row
        ON schedule_row.loan_account_id = account_row.id
       AND schedule_row.loan_application_id = account_row.loan_application_id
       AND schedule_row.loan_contract_id = account_row.loan_contract_id
    WHERE application_row.id IS NULL
       OR application_row.status <> 'DISBURSED'
       OR application_row.customer_id <> account_row.customer_id
       OR contract_row.id IS NULL
       OR contract_row.status <> 'READY_FOR_DISBURSEMENT'
       OR disbursement_row.id IS NULL
       OR disbursement_row.disbursed_amount <> account_row.approved_principal
       OR schedule_row.id IS NULL
       OR schedule_row.schedule_type <> 'FINAL'
       OR schedule_row.version <> 1
       OR schedule_row.approved_principal <> account_row.approved_principal
       OR schedule_row.total_interest <> account_row.total_interest
       OR schedule_row.fee_amount <> account_row.fee_amount
       OR schedule_row.total_repayment_amount <> account_row.total_repayment_amount
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: LoanAccount % lacks complete activation evidence',
            incompatible_account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT schedule_row.loan_account_id
    INTO incompatible_account_id
    FROM repayment_schedules schedule_row
    LEFT JOIN (
        SELECT
            repayment_schedule_id,
            COUNT(*) AS item_count,
            COALESCE(SUM(principal_due), 0) AS principal_sum,
            COALESCE(SUM(interest_due), 0) AS interest_sum,
            COALESCE(SUM(fee_due), 0) AS fee_sum,
            COALESCE(SUM(total_due), 0) AS total_sum
        FROM repayment_schedule_items
        GROUP BY repayment_schedule_id
    ) item_totals ON item_totals.repayment_schedule_id = schedule_row.id
    WHERE COALESCE(item_totals.item_count, 0) <> schedule_row.approved_term_months
       OR item_totals.principal_sum <> schedule_row.approved_principal
       OR item_totals.interest_sum <> schedule_row.total_interest
       OR item_totals.fee_sum <> schedule_row.fee_amount
       OR item_totals.total_sum <> schedule_row.total_repayment_amount
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: final schedule for LoanAccount % is invalid',
            incompatible_account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT limit_row.id
    INTO incompatible_limit_id
    FROM salary_advance_limits limit_row
    LEFT JOIN (
        SELECT
            salary_advance_limit_id,
            COALESCE(SUM(
                CASE
                    WHEN movement_type = 'RESERVED' THEN amount
                    WHEN movement_type IN (
                        'RESERVATION_RELEASED', 'DISBURSED_TO_USED'
                    ) THEN -amount
                    ELSE 0
                END
            ), 0) AS expected_reserved,
            COALESCE(SUM(
                CASE
                    WHEN movement_type = 'DISBURSED_TO_USED' THEN amount
                    WHEN movement_type = 'REPAID_RELEASED' THEN -amount
                    ELSE 0
                END
            ), 0) AS expected_used
        FROM salary_advance_limit_movements
        GROUP BY salary_advance_limit_id
    ) movement_totals
        ON movement_totals.salary_advance_limit_id = limit_row.id
    WHERE limit_row.reserved_amount
            <> COALESCE(movement_totals.expected_reserved, 0)
       OR limit_row.used_amount <> COALESCE(movement_totals.expected_used, 0)
       OR limit_row.available_amount
            <> limit_row.total_limit
                - limit_row.used_amount
                - limit_row.reserved_amount
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: Salary Advance limit % is inconsistent',
            incompatible_limit_id
            USING ERRCODE = '23514';
    END IF;

    SELECT COUNT(*)
    INTO permission_count
    FROM permissions
    WHERE code = 'repayment:update';

    SELECT COUNT(*)
    INTO accounting_grant_count
    FROM permissions permission_row
    JOIN role_permissions role_permission
        ON role_permission.permission_id = permission_row.id
    JOIN roles role_row
        ON role_row.id = role_permission.role_id
    WHERE permission_row.code = 'repayment:update'
      AND role_row.code = 'ACCOUNTING_OFFICER';

    IF permission_count <> 1 OR accounting_grant_count <> 1 THEN
        RAISE EXCEPTION
            'V33 preflight failed: repayment:update permission or current grant is missing or ambiguous'
            USING ERRCODE = '23514';
    END IF;
END;
$$;

ALTER TABLE loan_accounts
    ADD CONSTRAINT uq_loan_accounts_id_application
        UNIQUE (id, loan_application_id);

ALTER TABLE repayment_schedules
    ADD CONSTRAINT uq_repayment_schedules_id_application_account
        UNIQUE (id, loan_application_id, loan_account_id),
    ADD CONSTRAINT uq_repayment_schedules_id_account
        UNIQUE (id, loan_account_id);

ALTER TABLE repayment_schedule_items
    ADD CONSTRAINT uq_repayment_schedule_items_id_schedule
        UNIQUE (id, repayment_schedule_id);

CREATE INDEX idx_repayment_schedule_items_due_order
    ON repayment_schedule_items (
        repayment_schedule_id,
        due_date,
        installment_number
    );

ALTER TABLE loan_accounts
    ADD COLUMN principal_paid NUMERIC(19,2),
    ADD COLUMN interest_paid NUMERIC(19,2),
    ADD COLUMN fee_paid NUMERIC(19,2),
    ADD COLUMN total_paid NUMERIC(19,2),
    ADD COLUMN principal_outstanding NUMERIC(19,2),
    ADD COLUMN interest_outstanding NUMERIC(19,2),
    ADD COLUMN fee_outstanding NUMERIC(19,2),
    ADD COLUMN total_outstanding NUMERIC(19,2),
    ADD COLUMN last_payment_value_date DATE,
    ADD COLUMN last_payment_recorded_at TIMESTAMP,
    ADD COLUMN servicing_evaluation_date DATE;

CREATE TABLE repayment_transactions (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    repayment_schedule_id UUID NOT NULL,
    request_id UUID NOT NULL,
    external_payment_reference VARCHAR(64) NOT NULL,
    received_amount NUMERIC(19,2) NOT NULL,
    payment_value_date DATE NOT NULL,
    recorded_by_user_id UUID NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_transactions_account_application
        FOREIGN KEY (loan_account_id, loan_application_id)
        REFERENCES loan_accounts (id, loan_application_id),
    CONSTRAINT fk_repayment_transactions_schedule_ownership
        FOREIGN KEY (
            repayment_schedule_id,
            loan_application_id,
            loan_account_id
        )
        REFERENCES repayment_schedules (
            id,
            loan_application_id,
            loan_account_id
        ),
    CONSTRAINT fk_repayment_transactions_recorded_by
        FOREIGN KEY (recorded_by_user_id)
        REFERENCES users (id),

    CONSTRAINT uq_repayment_transactions_request UNIQUE (request_id),
    CONSTRAINT uq_repayment_transactions_reference
        UNIQUE (external_payment_reference),
    CONSTRAINT uq_repayment_transactions_id_schedule
        UNIQUE (id, repayment_schedule_id),

    CONSTRAINT chk_repayment_transactions_reference CHECK (
        external_payment_reference = btrim(external_payment_reference)
        AND external_payment_reference
            ~ '^[A-Z0-9][A-Z0-9._:/-]{0,63}$'
    ),
    CONSTRAINT chk_repayment_transactions_amount CHECK (
        received_amount > 0
        AND received_amount = trunc(received_amount)
    ),
    CONSTRAINT chk_repayment_transactions_dates CHECK (
        payment_value_date <= recorded_at::date
    )
);

CREATE INDEX idx_repayment_transactions_account_recorded
    ON repayment_transactions (loan_account_id, recorded_at, id);

CREATE INDEX idx_repayment_transactions_schedule
    ON repayment_transactions (repayment_schedule_id);

CREATE TABLE repayment_allocations (
    id UUID PRIMARY KEY,
    repayment_transaction_id UUID NOT NULL,
    allocation_sequence INTEGER NOT NULL,
    repayment_schedule_item_id UUID NOT NULL,
    component VARCHAR(20) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_allocations_transaction
        FOREIGN KEY (repayment_transaction_id)
        REFERENCES repayment_transactions (id),
    CONSTRAINT fk_repayment_allocations_schedule_item
        FOREIGN KEY (repayment_schedule_item_id)
        REFERENCES repayment_schedule_items (id),

    CONSTRAINT uq_repayment_allocations_transaction_sequence
        UNIQUE (repayment_transaction_id, allocation_sequence),
    CONSTRAINT uq_repayment_allocations_transaction_item_component
        UNIQUE (
            repayment_transaction_id,
            repayment_schedule_item_id,
            component
        ),

    CONSTRAINT chk_repayment_allocations_sequence
        CHECK (allocation_sequence > 0),
    CONSTRAINT chk_repayment_allocations_component
        CHECK (component IN ('FEE', 'INTEREST', 'PRINCIPAL')),
    CONSTRAINT chk_repayment_allocations_amount CHECK (
        amount > 0
        AND amount = trunc(amount)
    )
);

CREATE INDEX idx_repayment_allocations_schedule_item
    ON repayment_allocations (repayment_schedule_item_id);

CREATE TABLE repayment_installment_progress (
    repayment_schedule_item_id UUID PRIMARY KEY,
    repayment_schedule_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,

    principal_paid NUMERIC(19,2) NOT NULL,
    interest_paid NUMERIC(19,2) NOT NULL,
    fee_paid NUMERIC(19,2) NOT NULL,
    total_paid NUMERIC(19,2) NOT NULL,
    principal_outstanding NUMERIC(19,2) NOT NULL,
    interest_outstanding NUMERIC(19,2) NOT NULL,
    fee_outstanding NUMERIC(19,2) NOT NULL,
    total_outstanding NUMERIC(19,2) NOT NULL,

    status VARCHAR(30) NOT NULL,
    last_payment_value_date DATE,
    last_payment_recorded_at TIMESTAMP,
    servicing_evaluation_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_repayment_progress_item_schedule
        FOREIGN KEY (repayment_schedule_item_id, repayment_schedule_id)
        REFERENCES repayment_schedule_items (id, repayment_schedule_id),
    CONSTRAINT fk_repayment_progress_schedule_account
        FOREIGN KEY (repayment_schedule_id, loan_account_id)
        REFERENCES repayment_schedules (id, loan_account_id),

    CONSTRAINT uq_repayment_progress_schedule_installment
        UNIQUE (repayment_schedule_id, installment_number),

    CONSTRAINT chk_repayment_progress_installment
        CHECK (installment_number > 0),
    CONSTRAINT chk_repayment_progress_amounts CHECK (
        principal_paid >= 0
        AND interest_paid >= 0
        AND fee_paid >= 0
        AND total_paid >= 0
        AND principal_outstanding >= 0
        AND interest_outstanding >= 0
        AND fee_outstanding >= 0
        AND total_outstanding >= 0
        AND principal_paid = trunc(principal_paid)
        AND interest_paid = trunc(interest_paid)
        AND fee_paid = trunc(fee_paid)
        AND total_paid = trunc(total_paid)
        AND principal_outstanding = trunc(principal_outstanding)
        AND interest_outstanding = trunc(interest_outstanding)
        AND fee_outstanding = trunc(fee_outstanding)
        AND total_outstanding = trunc(total_outstanding)
        AND total_paid = principal_paid + interest_paid + fee_paid
        AND total_outstanding
            = principal_outstanding + interest_outstanding + fee_outstanding
    ),
    CONSTRAINT chk_repayment_progress_status CHECK (
        status IN ('NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE')
    ),
    CONSTRAINT chk_repayment_progress_payment_dates CHECK (
        (
            total_paid = 0
            AND last_payment_value_date IS NULL
            AND last_payment_recorded_at IS NULL
        )
        OR (
            total_paid > 0
            AND last_payment_value_date IS NOT NULL
            AND last_payment_recorded_at IS NOT NULL
        )
    )
);

CREATE INDEX idx_repayment_progress_account_installment
    ON repayment_installment_progress (loan_account_id, installment_number);

CREATE INDEX idx_repayment_progress_due_status
    ON repayment_installment_progress (
        servicing_evaluation_date,
        status,
        loan_account_id
    );

CREATE TABLE loan_account_status_transitions (
    id UUID PRIMARY KEY,
    loan_account_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    operation_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    servicing_evaluation_date DATE NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_account_status_history_account
        FOREIGN KEY (loan_account_id)
        REFERENCES loan_accounts (id),
    CONSTRAINT fk_loan_account_status_history_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_loan_account_status_history_sequence
        UNIQUE (loan_account_id, sequence_number),
    CONSTRAINT uq_loan_account_status_history_operation
        UNIQUE (loan_account_id, operation_id),
    CONSTRAINT chk_loan_account_status_history_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_loan_account_status_history_statuses CHECK (
        (from_status IS NULL OR from_status IN ('ACTIVE', 'OVERDUE', 'SETTLED'))
        AND to_status IN ('ACTIVE', 'OVERDUE', 'SETTLED')
        AND from_status IS DISTINCT FROM to_status
    ),
    CONSTRAINT chk_loan_account_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED'
        )
    ),
    CONSTRAINT chk_loan_account_status_history_actor CHECK (
        (actor_type = 'USER' AND actor_user_id IS NOT NULL)
        OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
    ),
    CONSTRAINT chk_loan_account_status_history_initial CHECK (
        (
            sequence_number = 1
            AND from_status IS NULL
            AND action = 'ACTIVATION_INITIALIZED'
        )
        OR (sequence_number > 1 AND from_status IS NOT NULL)
    )
);

CREATE INDEX idx_loan_account_status_history_account_occurred
    ON loan_account_status_transitions (
        loan_account_id,
        occurred_at,
        sequence_number
    );

CREATE TABLE repayment_installment_status_transitions (
    id UUID PRIMARY KEY,
    repayment_schedule_item_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    operation_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    servicing_evaluation_date DATE NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_installment_status_history_item
        FOREIGN KEY (repayment_schedule_item_id)
        REFERENCES repayment_schedule_items (id),
    CONSTRAINT fk_repayment_installment_status_history_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_repayment_installment_status_history_sequence
        UNIQUE (repayment_schedule_item_id, sequence_number),
    CONSTRAINT uq_repayment_installment_status_history_operation
        UNIQUE (repayment_schedule_item_id, operation_id),
    CONSTRAINT chk_repayment_installment_status_history_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_repayment_installment_status_history_statuses CHECK (
        (
            from_status IS NULL
            OR from_status IN (
                'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
            )
        )
        AND to_status IN (
            'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
        )
        AND from_status IS DISTINCT FROM to_status
    ),
    CONSTRAINT chk_repayment_installment_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED'
        )
    ),
    CONSTRAINT chk_repayment_installment_status_history_actor CHECK (
        (actor_type = 'USER' AND actor_user_id IS NOT NULL)
        OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
    ),
    CONSTRAINT chk_repayment_installment_status_history_initial CHECK (
        (
            sequence_number = 1
            AND from_status IS NULL
            AND action = 'ACTIVATION_INITIALIZED'
        )
        OR (sequence_number > 1 AND from_status IS NOT NULL)
    )
);

CREATE INDEX idx_repayment_installment_status_history_item_occurred
    ON repayment_installment_status_transitions (
        repayment_schedule_item_id,
        occurred_at,
        sequence_number
    );

ALTER TABLE salary_advance_limit_movements
    ADD COLUMN repayment_transaction_id UUID,
    ADD CONSTRAINT fk_salary_advance_movements_repayment_transaction
        FOREIGN KEY (repayment_transaction_id)
        REFERENCES repayment_transactions (id),
    ADD CONSTRAINT chk_salary_advance_movements_repayment_release CHECK (
        (
            movement_type = 'REPAID_RELEASED'
            AND repayment_transaction_id IS NOT NULL
            AND loan_application_id IS NOT NULL
            AND loan_account_id IS NOT NULL
            AND amount > 0
            AND amount = trunc(amount)
        )
        OR (
            movement_type <> 'REPAID_RELEASED'
            AND repayment_transaction_id IS NULL
        )
    );

CREATE UNIQUE INDEX uq_salary_advance_movements_repayment_release
    ON salary_advance_limit_movements (repayment_transaction_id)
    WHERE movement_type = 'REPAID_RELEASED';

INSERT INTO repayment_installment_progress (
    repayment_schedule_item_id,
    repayment_schedule_id,
    loan_account_id,
    installment_number,
    principal_paid,
    interest_paid,
    fee_paid,
    total_paid,
    principal_outstanding,
    interest_outstanding,
    fee_outstanding,
    total_outstanding,
    status,
    last_payment_value_date,
    last_payment_recorded_at,
    servicing_evaluation_date,
    created_at,
    updated_at
)
SELECT
    item.id,
    schedule_row.id,
    account_row.id,
    item.installment_number,
    0,
    0,
    0,
    0,
    item.principal_due,
    item.interest_due,
    item.fee_due,
    item.total_due,
    CASE
        WHEN item.due_date < account_row.activated_at::date THEN 'OVERDUE'
        WHEN item.due_date = account_row.activated_at::date THEN 'DUE'
        ELSE 'NOT_DUE'
    END,
    NULL,
    NULL,
    account_row.activated_at::date,
    account_row.activated_at,
    account_row.activated_at
FROM loan_accounts account_row
JOIN repayment_schedules schedule_row
    ON schedule_row.loan_account_id = account_row.id
JOIN repayment_schedule_items item
    ON item.repayment_schedule_id = schedule_row.id;

UPDATE loan_accounts account_row
SET principal_paid = 0,
    interest_paid = 0,
    fee_paid = 0,
    total_paid = 0,
    principal_outstanding = account_row.approved_principal,
    interest_outstanding = account_row.total_interest,
    fee_outstanding = account_row.fee_amount,
    total_outstanding = account_row.total_repayment_amount,
    last_payment_value_date = NULL,
    last_payment_recorded_at = NULL,
    servicing_evaluation_date = account_row.activated_at::date,
    status = CASE
        WHEN EXISTS (
            SELECT 1
            FROM repayment_installment_progress progress
            WHERE progress.loan_account_id = account_row.id
              AND progress.status = 'OVERDUE'
        ) THEN 'OVERDUE'
        ELSE 'ACTIVE'
    END,
    updated_at = account_row.activated_at;

-- Flush the V28 deferred activation event before altering the same table.
-- Restore deferred mode so later atomic servicing writes retain their contract.
SET CONSTRAINTS trg_loan_activation_foundation_account IMMEDIATE;
SET CONSTRAINTS trg_loan_activation_foundation_account DEFERRED;

ALTER TABLE loan_accounts
    ALTER COLUMN principal_paid SET NOT NULL,
    ALTER COLUMN interest_paid SET NOT NULL,
    ALTER COLUMN fee_paid SET NOT NULL,
    ALTER COLUMN total_paid SET NOT NULL,
    ALTER COLUMN principal_outstanding SET NOT NULL,
    ALTER COLUMN interest_outstanding SET NOT NULL,
    ALTER COLUMN fee_outstanding SET NOT NULL,
    ALTER COLUMN total_outstanding SET NOT NULL,
    ALTER COLUMN servicing_evaluation_date SET NOT NULL,
    ADD CONSTRAINT chk_loan_accounts_servicing_amounts CHECK (
        principal_paid >= 0
        AND interest_paid >= 0
        AND fee_paid >= 0
        AND total_paid >= 0
        AND principal_outstanding >= 0
        AND interest_outstanding >= 0
        AND fee_outstanding >= 0
        AND total_outstanding >= 0
        AND principal_paid = trunc(principal_paid)
        AND interest_paid = trunc(interest_paid)
        AND fee_paid = trunc(fee_paid)
        AND total_paid = trunc(total_paid)
        AND principal_outstanding = trunc(principal_outstanding)
        AND interest_outstanding = trunc(interest_outstanding)
        AND fee_outstanding = trunc(fee_outstanding)
        AND total_outstanding = trunc(total_outstanding)
        AND total_paid = principal_paid + interest_paid + fee_paid
        AND total_outstanding
            = principal_outstanding + interest_outstanding + fee_outstanding
        AND principal_paid + principal_outstanding = approved_principal
        AND interest_paid + interest_outstanding = total_interest
        AND fee_paid + fee_outstanding = fee_amount
        AND total_paid + total_outstanding = total_repayment_amount
    ),
    ADD CONSTRAINT chk_loan_accounts_servicing_dates CHECK (
        (
            total_paid = 0
            AND last_payment_value_date IS NULL
            AND last_payment_recorded_at IS NULL
        )
        OR (
            total_paid > 0
            AND last_payment_value_date IS NOT NULL
            AND last_payment_recorded_at IS NOT NULL
        )
    ),
    ADD CONSTRAINT chk_loan_accounts_settlement_balance CHECK (
        (status = 'SETTLED' AND total_outstanding = 0)
        OR (status IN ('ACTIVE', 'OVERDUE') AND total_outstanding > 0)
        OR status = 'CLOSED'
    );

INSERT INTO loan_account_status_transitions (
    id,
    loan_account_id,
    sequence_number,
    operation_id,
    from_status,
    to_status,
    action,
    actor_type,
    actor_user_id,
    servicing_evaluation_date,
    occurred_at,
    created_at
)
SELECT
    md5(account_row.id::text || ':loan-account-initial-status')::uuid,
    account_row.id,
    1,
    md5(account_row.id::text || ':activation-servicing-operation')::uuid,
    NULL,
    account_row.status,
    'ACTIVATION_INITIALIZED',
    'SYSTEM',
    NULL,
    account_row.servicing_evaluation_date,
    account_row.activated_at,
    account_row.activated_at
FROM loan_accounts account_row;

INSERT INTO repayment_installment_status_transitions (
    id,
    repayment_schedule_item_id,
    sequence_number,
    operation_id,
    from_status,
    to_status,
    action,
    actor_type,
    actor_user_id,
    servicing_evaluation_date,
    occurred_at,
    created_at
)
SELECT
    md5(progress.repayment_schedule_item_id::text
        || ':installment-initial-status')::uuid,
    progress.repayment_schedule_item_id,
    1,
    md5(progress.loan_account_id::text
        || ':activation-servicing-operation')::uuid,
    NULL,
    progress.status,
    'ACTIVATION_INITIALIZED',
    'SYSTEM',
    NULL,
    progress.servicing_evaluation_date,
    account_row.activated_at,
    account_row.activated_at
FROM repayment_installment_progress progress
JOIN loan_accounts account_row
    ON account_row.id = progress.loan_account_id;

CREATE TRIGGER trg_repayment_transactions_immutable
    BEFORE UPDATE OR DELETE ON repayment_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_allocations_immutable
    BEFORE UPDATE OR DELETE ON repayment_allocations
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_loan_account_status_transitions_immutable
    BEFORE UPDATE OR DELETE ON loan_account_status_transitions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_installment_status_transitions_immutable
    BEFORE UPDATE OR DELETE ON repayment_installment_status_transitions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE OR REPLACE FUNCTION enforce_repayment_progress_mutation_boundary()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Repayment installment progress rows cannot be deleted';
    END IF;

    IF ROW(
        NEW.repayment_schedule_item_id,
        NEW.repayment_schedule_id,
        NEW.loan_account_id,
        NEW.installment_number,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.repayment_schedule_item_id,
        OLD.repayment_schedule_id,
        OLD.loan_account_id,
        OLD.installment_number,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION
            'Repayment installment progress ownership cannot be changed';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_repayment_progress_mutation_boundary
    BEFORE UPDATE OR DELETE ON repayment_installment_progress
    FOR EACH ROW EXECUTE FUNCTION enforce_repayment_progress_mutation_boundary();

CREATE OR REPLACE FUNCTION reject_disbursed_to_used_movement_mutation()
RETURNS trigger AS $$
BEGIN
    IF OLD.movement_type IN ('DISBURSED_TO_USED', 'REPAID_RELEASED') THEN
        RAISE EXCEPTION
            'Salary Advance conversion and repayment-release movements are immutable';
    END IF;
    IF TG_OP = 'UPDATE'
            AND NEW.movement_type IN ('DISBURSED_TO_USED', 'REPAID_RELEASED') THEN
        RAISE EXCEPTION
            'Salary Advance conversion and repayment-release evidence must be inserted';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_servicing_reconciliation()
RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM repayment_transactions transaction_row
        LEFT JOIN manual_disbursements disbursement_row
            ON disbursement_row.loan_account_id = transaction_row.loan_account_id
        LEFT JOIN (
            SELECT
                repayment_transaction_id,
                COUNT(*) AS allocation_count,
                MIN(allocation_sequence) AS minimum_sequence,
                MAX(allocation_sequence) AS maximum_sequence,
                COALESCE(SUM(amount), 0) AS allocated_amount
            FROM repayment_allocations
            GROUP BY repayment_transaction_id
        ) allocation_totals
            ON allocation_totals.repayment_transaction_id = transaction_row.id
        WHERE disbursement_row.id IS NULL
           OR transaction_row.payment_value_date
                < disbursement_row.disbursement_value_date
           OR transaction_row.payment_value_date > transaction_row.recorded_at::date
           OR COALESCE(allocation_totals.allocation_count, 0) = 0
           OR allocation_totals.minimum_sequence <> 1
           OR allocation_totals.maximum_sequence
                <> allocation_totals.allocation_count
           OR allocation_totals.allocated_amount
                <> transaction_row.received_amount
    ) THEN
        RAISE EXCEPTION
            'Repayment transaction does not reconcile to allocation or date evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_allocations allocation_row
        JOIN repayment_transactions transaction_row
            ON transaction_row.id = allocation_row.repayment_transaction_id
        JOIN repayment_schedule_items item
            ON item.id = allocation_row.repayment_schedule_item_id
        WHERE item.repayment_schedule_id <> transaction_row.repayment_schedule_id
    ) THEN
        RAISE EXCEPTION
            'Repayment allocation does not belong to the transaction final schedule';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_schedule_items item
        LEFT JOIN (
            SELECT
                repayment_schedule_item_id,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'PRINCIPAL'
                ), 0) AS principal_paid,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'INTEREST'
                ), 0) AS interest_paid,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'FEE'
                ), 0) AS fee_paid
            FROM repayment_allocations
            GROUP BY repayment_schedule_item_id
        ) allocated
            ON allocated.repayment_schedule_item_id = item.id
        LEFT JOIN repayment_installment_progress progress
            ON progress.repayment_schedule_item_id = item.id
        WHERE progress.repayment_schedule_item_id IS NULL
           OR COALESCE(allocated.principal_paid, 0) > item.principal_due
           OR COALESCE(allocated.interest_paid, 0) > item.interest_due
           OR COALESCE(allocated.fee_paid, 0) > item.fee_due
           OR progress.principal_paid
                <> COALESCE(allocated.principal_paid, 0)
           OR progress.interest_paid
                <> COALESCE(allocated.interest_paid, 0)
           OR progress.fee_paid <> COALESCE(allocated.fee_paid, 0)
           OR progress.principal_outstanding
                <> item.principal_due - COALESCE(allocated.principal_paid, 0)
           OR progress.interest_outstanding
                <> item.interest_due - COALESCE(allocated.interest_paid, 0)
           OR progress.fee_outstanding
                <> item.fee_due - COALESCE(allocated.fee_paid, 0)
           OR progress.total_paid
                <> COALESCE(allocated.principal_paid, 0)
                    + COALESCE(allocated.interest_paid, 0)
                    + COALESCE(allocated.fee_paid, 0)
           OR progress.total_outstanding
                <> item.total_due
                    - COALESCE(allocated.principal_paid, 0)
                    - COALESCE(allocated.interest_paid, 0)
                    - COALESCE(allocated.fee_paid, 0)
           OR progress.status <> CASE
                WHEN progress.total_outstanding = 0 THEN 'PAID'
                WHEN item.due_date < progress.servicing_evaluation_date
                    THEN 'OVERDUE'
                WHEN progress.total_paid > 0 THEN 'PARTIALLY_PAID'
                WHEN item.due_date = progress.servicing_evaluation_date
                    THEN 'DUE'
                ELSE 'NOT_DUE'
              END
           OR progress.last_payment_value_date IS DISTINCT FROM (
                SELECT MAX(transaction_for_item.payment_value_date)
                FROM repayment_allocations allocation_for_item
                JOIN repayment_transactions transaction_for_item
                    ON transaction_for_item.id
                        = allocation_for_item.repayment_transaction_id
                WHERE allocation_for_item.repayment_schedule_item_id = item.id
              )
           OR progress.last_payment_recorded_at IS DISTINCT FROM (
                SELECT MAX(transaction_for_item.recorded_at)
                FROM repayment_allocations allocation_for_item
                JOIN repayment_transactions transaction_for_item
                    ON transaction_for_item.id
                        = allocation_for_item.repayment_transaction_id
                WHERE allocation_for_item.repayment_schedule_item_id = item.id
              )
    ) THEN
        RAISE EXCEPTION
            'Repayment installment progress does not reconcile to immutable schedule and allocations';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        LEFT JOIN (
            SELECT
                loan_account_id,
                COUNT(*) AS installment_count,
                COALESCE(SUM(principal_paid), 0) AS principal_paid,
                COALESCE(SUM(interest_paid), 0) AS interest_paid,
                COALESCE(SUM(fee_paid), 0) AS fee_paid,
                COALESCE(SUM(total_paid), 0) AS total_paid,
                COALESCE(SUM(principal_outstanding), 0)
                    AS principal_outstanding,
                COALESCE(SUM(interest_outstanding), 0)
                    AS interest_outstanding,
                COALESCE(SUM(fee_outstanding), 0) AS fee_outstanding,
                COALESCE(SUM(total_outstanding), 0) AS total_outstanding,
                COUNT(*) FILTER (WHERE status = 'OVERDUE') AS overdue_count,
                MAX(servicing_evaluation_date) AS maximum_evaluation_date,
                MIN(servicing_evaluation_date) AS minimum_evaluation_date
            FROM repayment_installment_progress
            GROUP BY loan_account_id
        ) progress_totals
            ON progress_totals.loan_account_id = account_row.id
        WHERE COALESCE(progress_totals.installment_count, 0)
                <> account_row.approved_term_months
           OR progress_totals.principal_paid <> account_row.principal_paid
           OR progress_totals.interest_paid <> account_row.interest_paid
           OR progress_totals.fee_paid <> account_row.fee_paid
           OR progress_totals.total_paid <> account_row.total_paid
           OR progress_totals.principal_outstanding
                <> account_row.principal_outstanding
           OR progress_totals.interest_outstanding
                <> account_row.interest_outstanding
           OR progress_totals.fee_outstanding
                <> account_row.fee_outstanding
           OR progress_totals.total_outstanding
                <> account_row.total_outstanding
           OR progress_totals.minimum_evaluation_date
                <> progress_totals.maximum_evaluation_date
           OR account_row.servicing_evaluation_date
                <> progress_totals.maximum_evaluation_date
           OR account_row.status <> CASE
                WHEN account_row.total_outstanding = 0 THEN 'SETTLED'
                WHEN progress_totals.overdue_count > 0 THEN 'OVERDUE'
                ELSE 'ACTIVE'
              END
           OR account_row.last_payment_value_date IS DISTINCT FROM (
                SELECT MAX(transaction_row.payment_value_date)
                FROM repayment_transactions transaction_row
                WHERE transaction_row.loan_account_id = account_row.id
              )
           OR account_row.last_payment_recorded_at IS DISTINCT FROM (
                SELECT MAX(transaction_row.recorded_at)
                FROM repayment_transactions transaction_row
                WHERE transaction_row.loan_account_id = account_row.id
              )
    ) THEN
        RAISE EXCEPTION
            'LoanAccount servicing rollup does not reconcile to installment progress';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_transactions transaction_row
        LEFT JOIN (
            SELECT
                repayment_transaction_id,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'PRINCIPAL'
                ), 0) AS principal_allocated
            FROM repayment_allocations
            GROUP BY repayment_transaction_id
        ) principal
            ON principal.repayment_transaction_id = transaction_row.id
        LEFT JOIN salary_advance_limit_movements release
            ON release.repayment_transaction_id = transaction_row.id
           AND release.movement_type = 'REPAID_RELEASED'
        LEFT JOIN salary_advance_limit_movements conversion
            ON conversion.loan_application_id
                = transaction_row.loan_application_id
           AND conversion.loan_account_id = transaction_row.loan_account_id
           AND conversion.movement_type = 'DISBURSED_TO_USED'
        WHERE (
                COALESCE(principal.principal_allocated, 0) = 0
                AND release.id IS NOT NULL
              )
           OR (
                COALESCE(principal.principal_allocated, 0) > 0
                AND (
                    release.id IS NULL
                    OR release.amount <> principal.principal_allocated
                    OR release.loan_application_id
                        <> transaction_row.loan_application_id
                    OR release.loan_account_id <> transaction_row.loan_account_id
                    OR conversion.id IS NULL
                    OR release.salary_advance_limit_id
                        <> conversion.salary_advance_limit_id
                )
              )
    ) THEN
        RAISE EXCEPTION
            'Salary Advance repayment release does not equal principal allocation';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements movement
        WHERE movement.movement_type = 'REPAID_RELEASED'
          AND NOT EXISTS (
              SELECT 1
              FROM repayment_transactions transaction_row
              WHERE transaction_row.id = movement.repayment_transaction_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements movement
        GROUP BY movement.salary_advance_limit_id
        HAVING COALESCE(SUM(movement.amount) FILTER (
                    WHERE movement.movement_type = 'REPAID_RELEASED'
               ), 0)
            > COALESCE(SUM(movement.amount) FILTER (
                    WHERE movement.movement_type = 'DISBURSED_TO_USED'
              ), 0)
    ) THEN
        RAISE EXCEPTION
            'Salary Advance repayment release exceeds converted principal';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_status_history()
RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        LEFT JOIN (
            SELECT
                loan_account_id,
                COUNT(*) AS transition_count,
                MIN(sequence_number) AS minimum_sequence,
                MAX(sequence_number) AS maximum_sequence,
                MAX(to_status) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_status,
                MAX(servicing_evaluation_date) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_evaluation_date,
                COUNT(*) FILTER (
                    WHERE sequence_number = 1 AND from_status IS NULL
                ) AS initial_count,
                COUNT(*) FILTER (
                    WHERE sequence_number > 1
                      AND from_status IS DISTINCT FROM previous_to_status
                ) AS broken_chain_count
            FROM (
                SELECT
                    transition_row.*,
                    MAX(sequence_number) OVER (
                        PARTITION BY loan_account_id
                    ) AS maximum_for_owner,
                    LAG(to_status) OVER (
                        PARTITION BY loan_account_id
                        ORDER BY sequence_number
                    ) AS previous_to_status
                FROM loan_account_status_transitions transition_row
            ) ordered_transitions
            GROUP BY loan_account_id
        ) history ON history.loan_account_id = account_row.id
        WHERE COALESCE(history.transition_count, 0) = 0
           OR history.minimum_sequence <> 1
           OR history.maximum_sequence <> history.transition_count
           OR history.initial_count <> 1
           OR history.broken_chain_count <> 0
           OR history.latest_status <> account_row.status
           OR history.latest_evaluation_date
                > account_row.servicing_evaluation_date
    ) THEN
        RAISE EXCEPTION
            'LoanAccount status transition history is incomplete or inconsistent';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_installment_progress progress
        LEFT JOIN (
            SELECT
                repayment_schedule_item_id,
                COUNT(*) AS transition_count,
                MIN(sequence_number) AS minimum_sequence,
                MAX(sequence_number) AS maximum_sequence,
                MAX(to_status) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_status,
                MAX(servicing_evaluation_date) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_evaluation_date,
                COUNT(*) FILTER (
                    WHERE sequence_number = 1 AND from_status IS NULL
                ) AS initial_count,
                COUNT(*) FILTER (
                    WHERE sequence_number > 1
                      AND from_status IS DISTINCT FROM previous_to_status
                ) AS broken_chain_count
            FROM (
                SELECT
                    transition_row.*,
                    MAX(sequence_number) OVER (
                        PARTITION BY repayment_schedule_item_id
                    ) AS maximum_for_owner,
                    LAG(to_status) OVER (
                        PARTITION BY repayment_schedule_item_id
                        ORDER BY sequence_number
                    ) AS previous_to_status
                FROM repayment_installment_status_transitions transition_row
            ) ordered_transitions
            GROUP BY repayment_schedule_item_id
        ) history
            ON history.repayment_schedule_item_id
                = progress.repayment_schedule_item_id
        WHERE COALESCE(history.transition_count, 0) = 0
           OR history.minimum_sequence <> 1
           OR history.maximum_sequence <> history.transition_count
           OR history.initial_count <> 1
           OR history.broken_chain_count <> 0
           OR history.latest_status <> progress.status
           OR history.latest_evaluation_date
                > progress.servicing_evaluation_date
    ) THEN
        RAISE EXCEPTION
            'Installment status transition history is incomplete or inconsistent';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_transaction
    AFTER INSERT OR UPDATE OR DELETE ON repayment_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_allocation
    AFTER INSERT OR UPDATE OR DELETE ON repayment_allocations
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_progress
    AFTER INSERT OR UPDATE OR DELETE ON repayment_installment_progress
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_account
    AFTER INSERT OR UPDATE OR DELETE ON loan_accounts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_release
    AFTER INSERT OR UPDATE OR DELETE ON salary_advance_limit_movements
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_history_account
    AFTER INSERT OR UPDATE OR DELETE ON loan_accounts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();

CREATE CONSTRAINT TRIGGER trg_repayment_history_account_transition
    AFTER INSERT OR UPDATE OR DELETE ON loan_account_status_transitions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();

CREATE CONSTRAINT TRIGGER trg_repayment_history_progress
    AFTER INSERT OR UPDATE OR DELETE ON repayment_installment_progress
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();

CREATE CONSTRAINT TRIGGER trg_repayment_history_installment_transition
    AFTER INSERT OR UPDATE OR DELETE
    ON repayment_installment_status_transitions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();

-- V34: durable repayment operation outcome and repayment audit entity support

DO $$
DECLARE
    expected_types CONSTANT TEXT[] := ARRAY[
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION',
        'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT'
    ]::TEXT[];
    actual_types TEXT[];
    matching_constraint_count INTEGER;
    constraint_expression TEXT;
    expected_constraint_expression TEXT;
    expected_type_sql TEXT;
    normalized_constraint_expression TEXT;
    normalized_expected_expression TEXT;
BEGIN
    SELECT count(*)
    INTO matching_constraint_count
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_entity_type'
      AND constraint_row.contype = 'c';

    IF matching_constraint_count <> 1 THEN
        RAISE EXCEPTION
            'V34 preflight failed: expected V33 audit entity constraint is missing or incompatible';
    END IF;

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_entity_type'
      AND constraint_row.contype = 'c';

    SELECT array_agg(matched.value[1] ORDER BY matched.value[1])
    INTO actual_types
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    CROSS JOIN LATERAL regexp_matches(
        pg_get_constraintdef(constraint_row.oid),
        '''([A-Z][A-Z0-9_]*)''', 'g'
    ) AS matched(value)
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_entity_type'
      AND constraint_row.contype = 'c';

    IF actual_types IS NULL
            OR cardinality(actual_types) <> cardinality(expected_types)
            OR EXISTS (
                SELECT value FROM unnest(expected_types) value
                EXCEPT SELECT value FROM unnest(actual_types) value
            )
            OR EXISTS (
                SELECT value FROM unnest(actual_types) value
                EXCEPT SELECT value FROM unnest(expected_types) value
            ) THEN
        RAISE EXCEPTION
            'V34 preflight failed: audit entity constraint does not match V33';
    END IF;

    SELECT string_agg(quote_literal(expected.entity_type), ', '
        ORDER BY expected.ordinality)
    INTO expected_type_sql
    FROM unnest(expected_types) WITH ORDINALITY
        AS expected(entity_type, ordinality);

    EXECUTE format(
        'CREATE TEMP TABLE v34_expected_audit_entity_constraint ('
        'entity_type VARCHAR(80) NOT NULL, '
        'CONSTRAINT chk_v34_expected_audit_entity '
        'CHECK (entity_type IN (%s))'
        ') ON COMMIT DROP',
        expected_type_sql
    );

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO expected_constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v34_expected_audit_entity_constraint'
      AND constraint_row.conname = 'chk_v34_expected_audit_entity'
      AND constraint_row.contype = 'c';

    normalized_constraint_expression := regexp_replace(
        regexp_replace(
            constraint_expression,
            '''[A-Z][A-Z0-9_]*''',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '[[:space:]]+',
        '',
        'g'
    );
    normalized_expected_expression := regexp_replace(
        regexp_replace(
            expected_constraint_expression,
            '''[A-Z][A-Z0-9_]*''',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '[[:space:]]+',
        '',
        'g'
    );

    normalized_constraint_expression := replace(
        regexp_replace(
            translate(normalized_constraint_expression, '()', ''),
            '''__ENTITY_TYPE__''::charactervarying(::text)?',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '::text[]',
        ''
    );
    normalized_expected_expression := replace(
        regexp_replace(
            translate(normalized_expected_expression, '()', ''),
            '''__ENTITY_TYPE__''::charactervarying(::text)?',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '::text[]',
        ''
    );

    IF normalized_constraint_expression IS DISTINCT FROM normalized_expected_expression THEN
        RAISE EXCEPTION
            'V34 preflight failed: incompatible audit entity constraint predicate';
    END IF;

    DROP TABLE v34_expected_audit_entity_constraint;
END;
$$;
ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_entity_type;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION',
        'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT',
        'REPAYMENT_TRANSACTION', 'LOAN_ACCOUNT'
    ));

CREATE OR REPLACE FUNCTION is_repayment_operation_outcome_json_safe(document JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    installment JSONB;
    progress JSONB;
    schedule_item_id TEXT;
    seen_schedule_item_ids TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF document IS NULL OR jsonb_typeof(document) <> 'object' THEN
        RETURN FALSE;
    END IF;

    IF EXISTS (
        WITH RECURSIVE json_nodes(value) AS (
            SELECT document
            UNION ALL
            SELECT child.value
            FROM json_nodes node
            CROSS JOIN LATERAL (
                SELECT object_value.value
                FROM jsonb_each(
                    CASE WHEN jsonb_typeof(node.value) = 'object'
                        THEN node.value ELSE '{}'::JSONB END
                ) AS object_value(key, value)
                UNION ALL
                SELECT array_value.value
                FROM jsonb_array_elements(
                    CASE WHEN jsonb_typeof(node.value) = 'array'
                        THEN node.value ELSE '[]'::JSONB END
                ) AS array_value(value)
            ) child
        )
        SELECT 1
        FROM json_nodes node
        CROSS JOIN LATERAL jsonb_object_keys(
            CASE WHEN jsonb_typeof(node.value) = 'object'
                THEN node.value ELSE '{}'::JSONB END
        ) prohibited_key
        WHERE lower(prohibited_key) = ANY (ARRAY[
                'externalpaymentreference', 'paymentreference',
                'canonicalreference', 'recordedbyuserid', 'actorid', 'userid',
                'customerid', 'salaryadvancelimitid', 'limitid', 'movementid',
                'auditid', 'historyid', 'encryptionmetadata', 'encryptionkeyid',
                'keyid', 'nonce', 'aad', 'ciphertext', 'fingerprint'
            ]::TEXT[])
           OR lower(prohibited_key) LIKE '%encryption%'
    ) THEN
        RETURN FALSE;
    END IF;

    IF NOT document ?& ARRAY[
            'repaymentTransactionId', 'loanApplicationId', 'loanAccountId',
            'repaymentScheduleId', 'receivedAmount', 'paymentValueDate',
            'recordedAt', 'accountBalance', 'accountStatus',
            'accountStatusChanged', 'principalReleased', 'installments'
        ]
        OR (SELECT COUNT(*) FROM jsonb_object_keys(document)) <> 12
        OR jsonb_typeof(document -> 'repaymentTransactionId') <> 'string'
        OR jsonb_typeof(document -> 'loanApplicationId') <> 'string'
        OR jsonb_typeof(document -> 'loanAccountId') <> 'string'
        OR jsonb_typeof(document -> 'repaymentScheduleId') <> 'string'
        OR jsonb_typeof(document -> 'receivedAmount') <> 'number'
        OR jsonb_typeof(document -> 'paymentValueDate') <> 'string'
        OR jsonb_typeof(document -> 'recordedAt') <> 'string'
        OR jsonb_typeof(document -> 'accountBalance') <> 'object'
        OR jsonb_typeof(document -> 'accountStatus') <> 'string'
        OR jsonb_typeof(document -> 'accountStatusChanged') <> 'boolean'
        OR jsonb_typeof(document -> 'principalReleased') <> 'number'
        OR jsonb_typeof(document -> 'installments') <> 'array'
        OR document ->> 'accountStatus' NOT IN ('ACTIVE', 'OVERDUE', 'SETTLED') THEN
        RETURN FALSE;
    END IF;

    IF NOT (document -> 'accountBalance') ?& ARRAY[
            'principalPaid', 'interestPaid', 'feePaid', 'totalPaid',
            'principalOutstanding', 'interestOutstanding', 'feeOutstanding',
            'totalOutstanding', 'lastPaymentValueDate',
            'lastPaymentRecordedAt', 'servicingEvaluationDate'
        ]
        OR (SELECT COUNT(*) FROM jsonb_object_keys(document -> 'accountBalance')) <> 11
        OR EXISTS (
            SELECT 1
            FROM unnest(ARRAY[
                'principalPaid', 'interestPaid', 'feePaid', 'totalPaid',
                'principalOutstanding', 'interestOutstanding', 'feeOutstanding',
                'totalOutstanding'
            ]::TEXT[]) amount_key
            WHERE jsonb_typeof(document -> 'accountBalance' -> amount_key) <> 'number'
        )
        OR jsonb_typeof(document -> 'accountBalance' -> 'servicingEvaluationDate')
            <> 'string'
        OR jsonb_typeof(document -> 'accountBalance' -> 'lastPaymentValueDate')
            NOT IN ('string', 'null')
        OR jsonb_typeof(document -> 'accountBalance' -> 'lastPaymentRecordedAt')
            NOT IN ('string', 'null') THEN
        RETURN FALSE;
    END IF;

    FOR installment IN
        SELECT value FROM jsonb_array_elements(document -> 'installments')
    LOOP
        IF jsonb_typeof(installment) <> 'object'
            OR NOT installment ?& ARRAY['progress', 'previousStatus', 'statusChanged']
            OR (SELECT COUNT(*) FROM jsonb_object_keys(installment)) <> 3
            OR jsonb_typeof(installment -> 'progress') <> 'object'
            OR jsonb_typeof(installment -> 'previousStatus') <> 'string'
            OR jsonb_typeof(installment -> 'statusChanged') <> 'boolean'
            OR installment ->> 'previousStatus' NOT IN (
                'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
            ) THEN
            RETURN FALSE;
        END IF;

        progress := installment -> 'progress';
        IF NOT progress ?& ARRAY[
                'repaymentScheduleItemId', 'repaymentScheduleId', 'loanAccountId',
                'installmentNumber', 'principalPaid', 'interestPaid', 'feePaid',
                'totalPaid', 'principalOutstanding', 'interestOutstanding',
                'feeOutstanding', 'totalOutstanding', 'status',
                'lastPaymentValueDate', 'lastPaymentRecordedAt',
                'servicingEvaluationDate', 'updatedAt'
            ]
            OR (SELECT COUNT(*) FROM jsonb_object_keys(progress)) <> 17
            OR jsonb_typeof(progress -> 'repaymentScheduleItemId') <> 'string'
            OR jsonb_typeof(progress -> 'repaymentScheduleId') <> 'string'
            OR jsonb_typeof(progress -> 'loanAccountId') <> 'string'
            OR jsonb_typeof(progress -> 'installmentNumber') <> 'number'
            OR jsonb_typeof(progress -> 'status') <> 'string'
            OR progress ->> 'status' NOT IN (
                'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
            )
            OR jsonb_typeof(progress -> 'lastPaymentValueDate')
                NOT IN ('string', 'null')
            OR jsonb_typeof(progress -> 'lastPaymentRecordedAt')
                NOT IN ('string', 'null')
            OR jsonb_typeof(progress -> 'servicingEvaluationDate') <> 'string'
            OR jsonb_typeof(progress -> 'updatedAt') <> 'string'
            OR EXISTS (
                SELECT 1
                FROM unnest(ARRAY[
                    'principalPaid', 'interestPaid', 'feePaid', 'totalPaid',
                    'principalOutstanding', 'interestOutstanding',
                    'feeOutstanding', 'totalOutstanding'
                ]::TEXT[]) amount_key
                WHERE jsonb_typeof(progress -> amount_key) <> 'number'
            ) THEN
            RETURN FALSE;
        END IF;

        schedule_item_id := progress ->> 'repaymentScheduleItemId';
        IF schedule_item_id = ANY (seen_schedule_item_ids) THEN
            RETURN FALSE;
        END IF;
        seen_schedule_item_ids := array_append(
            seen_schedule_item_ids,
            schedule_item_id
        );

        IF ((installment ->> 'statusChanged')::BOOLEAN
                AND installment ->> 'previousStatus' = progress ->> 'status')
            OR (NOT (installment ->> 'statusChanged')::BOOLEAN
                AND installment ->> 'previousStatus' <> progress ->> 'status') THEN
            RETURN FALSE;
        END IF;
    END LOOP;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE TABLE repayment_operation_outcomes (
    repayment_transaction_id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    repayment_schedule_id UUID NOT NULL,
    received_amount NUMERIC(19,2) NOT NULL,
    payment_value_date DATE NOT NULL,
    recorded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    principal_released NUMERIC(19,2) NOT NULL,
    account_status VARCHAR(30) NOT NULL,
    account_status_changed BOOLEAN NOT NULL,
    outcome_json JSONB NOT NULL,
    CONSTRAINT fk_repayment_operation_outcome_transaction
        FOREIGN KEY (repayment_transaction_id)
        REFERENCES repayment_transactions (id),
    CONSTRAINT fk_repayment_operation_outcome_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_repayment_operation_outcome_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_accounts (id),
    CONSTRAINT fk_repayment_operation_outcome_schedule
        FOREIGN KEY (repayment_schedule_id) REFERENCES repayment_schedules (id),
    CONSTRAINT chk_repayment_operation_outcome_received
        CHECK (received_amount > 0 AND received_amount = trunc(received_amount)),
    CONSTRAINT chk_repayment_operation_outcome_principal
        CHECK (principal_released >= 0
            AND principal_released = trunc(principal_released)),
    CONSTRAINT chk_repayment_operation_outcome_status
        CHECK (account_status IN ('ACTIVE', 'OVERDUE', 'SETTLED')),
    CONSTRAINT chk_repayment_operation_outcome_json
        CHECK (is_repayment_operation_outcome_json_safe(outcome_json))
);

CREATE INDEX idx_repayment_operation_outcomes_account_recorded
    ON repayment_operation_outcomes (loan_account_id, recorded_at,
        repayment_transaction_id);

CREATE TRIGGER trg_repayment_operation_outcomes_immutable
    BEFORE UPDATE OR DELETE ON repayment_operation_outcomes
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE OR REPLACE FUNCTION validate_repayment_operation_outcome_evidence(
    transaction_id_to_validate UUID
)
RETURNS VOID AS $$
DECLARE
    transaction_row repayment_transactions%ROWTYPE;
    outcome_row repayment_operation_outcomes%ROWTYPE;
    outcome_count INTEGER;
    principal_total NUMERIC(19,2);
    release_count INTEGER;
    release_total NUMERIC(19,2);
    repayment_audit_count INTEGER;
    correct_repayment_audit_count INTEGER;
    account_audit_count INTEGER;
    correct_account_audit_count INTEGER;
    account_transition_count INTEGER;
    correct_account_transition_count INTEGER;
    schedule_item_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO outcome_count
    FROM repayment_operation_outcomes
    WHERE repayment_transaction_id = transaction_id_to_validate;
    IF outcome_count <> 1 THEN
        RAISE EXCEPTION 'Repayment transaction requires exactly one operation outcome';
    END IF;

    SELECT * INTO outcome_row
    FROM repayment_operation_outcomes
    WHERE repayment_transaction_id = transaction_id_to_validate;

    SELECT * INTO transaction_row
    FROM repayment_transactions
    WHERE id = transaction_id_to_validate;
    IF NOT FOUND
            OR transaction_row.loan_application_id <> outcome_row.loan_application_id
            OR transaction_row.loan_account_id <> outcome_row.loan_account_id
            OR transaction_row.repayment_schedule_id <> outcome_row.repayment_schedule_id
            OR transaction_row.received_amount <> outcome_row.received_amount
            OR transaction_row.payment_value_date <> outcome_row.payment_value_date
            OR transaction_row.recorded_at <> outcome_row.recorded_at THEN
        RAISE EXCEPTION 'Repayment outcome identity conflicts with transaction evidence';
    END IF;

    SELECT COALESCE(SUM(amount), 0) INTO principal_total
    FROM repayment_allocations
    WHERE repayment_transaction_id = transaction_id_to_validate
      AND component = 'PRINCIPAL';
    IF principal_total <> outcome_row.principal_released THEN
        RAISE EXCEPTION 'Repayment outcome principal conflicts with allocations';
    END IF;

    SELECT COUNT(*), COALESCE(SUM(amount), 0)
    INTO release_count, release_total
    FROM salary_advance_limit_movements
    WHERE repayment_transaction_id = transaction_id_to_validate
      AND movement_type = 'REPAID_RELEASED';
    IF (principal_total = 0 AND release_count <> 0)
            OR (principal_total > 0
                AND (release_count <> 1 OR release_total <> principal_total)) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with exposure release evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'REPAYMENT_TRANSACTION'
          AND entity_id = transaction_id_to_validate
    )
    INTO repayment_audit_count, correct_repayment_audit_count
    FROM audit_events
    WHERE operation_id = transaction_id_to_validate
      AND action = 'REPAYMENT_RECORDED';
    IF repayment_audit_count <> 1 OR correct_repayment_audit_count <> 1 THEN
        RAISE EXCEPTION 'Repayment outcome requires exact repayment audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_ACCOUNT'
          AND entity_id = outcome_row.loan_account_id
    )
    INTO account_audit_count, correct_account_audit_count
    FROM audit_events
    WHERE operation_id = transaction_id_to_validate
      AND action = 'LOAN_ACCOUNT_STATUS_CHANGED';
    IF account_audit_count <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END)
            OR correct_account_audit_count
                <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with account audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE loan_account_id = outcome_row.loan_account_id
          AND action = 'REPAYMENT_RECORDED'
          AND to_status = outcome_row.account_status
    )
    INTO account_transition_count, correct_account_transition_count
    FROM loan_account_status_transitions
    WHERE operation_id = transaction_id_to_validate;
    IF account_transition_count
            <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END)
            OR correct_account_transition_count
                <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with account transition evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(outcome_row.outcome_json -> 'installments') item
        LEFT JOIN repayment_installment_status_transitions transition_row
          ON transition_row.operation_id = transaction_id_to_validate
         AND transition_row.repayment_schedule_item_id =
                (item -> 'progress' ->> 'repaymentScheduleItemId')::UUID
        WHERE (
            (item ->> 'statusChanged')::BOOLEAN
            AND (
                transition_row.id IS NULL
                OR transition_row.from_status <> item ->> 'previousStatus'
                OR transition_row.to_status <> item -> 'progress' ->> 'status'
                OR transition_row.action <> 'REPAYMENT_RECORDED'
            )
        ) OR (
            NOT (item ->> 'statusChanged')::BOOLEAN
            AND transition_row.id IS NOT NULL
        )
    ) OR EXISTS (
        SELECT 1
        FROM repayment_installment_status_transitions transition_row
        WHERE transition_row.operation_id = transaction_id_to_validate
          AND NOT EXISTS (
              SELECT 1
              FROM jsonb_array_elements(
                  outcome_row.outcome_json -> 'installments'
              ) item
              WHERE (item -> 'progress' ->> 'repaymentScheduleItemId')::UUID
                    = transition_row.repayment_schedule_item_id
          )
    ) THEN
        RAISE EXCEPTION
            'Repayment outcome conflicts with item-specific installment transition evidence';
    END IF;

    SELECT COUNT(*) INTO schedule_item_count
    FROM repayment_schedule_items
    WHERE repayment_schedule_id = outcome_row.repayment_schedule_id;
    IF jsonb_array_length(outcome_row.outcome_json -> 'installments')
                <> schedule_item_count
            OR (outcome_row.outcome_json ->> 'repaymentTransactionId')::UUID
                <> outcome_row.repayment_transaction_id
            OR (outcome_row.outcome_json ->> 'loanApplicationId')::UUID
                <> outcome_row.loan_application_id
            OR (outcome_row.outcome_json ->> 'loanAccountId')::UUID
                <> outcome_row.loan_account_id
            OR (outcome_row.outcome_json ->> 'repaymentScheduleId')::UUID
                <> outcome_row.repayment_schedule_id
            OR (outcome_row.outcome_json ->> 'receivedAmount')::NUMERIC
                <> outcome_row.received_amount
            OR (outcome_row.outcome_json ->> 'paymentValueDate')::DATE
                <> outcome_row.payment_value_date
            OR (outcome_row.outcome_json ->> 'recordedAt')::TIMESTAMP
                <> outcome_row.recorded_at
            OR (outcome_row.outcome_json ->> 'principalReleased')::NUMERIC
                <> outcome_row.principal_released
            OR outcome_row.outcome_json ->> 'accountStatus'
                <> outcome_row.account_status
            OR (outcome_row.outcome_json ->> 'accountStatusChanged')::BOOLEAN
                <> outcome_row.account_status_changed THEN
        RAISE EXCEPTION 'Repayment outcome JSON conflicts with typed evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        WHERE account_row.id = outcome_row.loan_account_id
          AND (
              account_row.status <> outcome_row.account_status
              OR account_row.principal_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'principalPaid')::NUMERIC
              OR account_row.interest_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'interestPaid')::NUMERIC
              OR account_row.fee_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'feePaid')::NUMERIC
              OR account_row.total_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'totalPaid')::NUMERIC
              OR account_row.principal_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'principalOutstanding')::NUMERIC
              OR account_row.interest_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'interestOutstanding')::NUMERIC
              OR account_row.fee_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'feeOutstanding')::NUMERIC
              OR account_row.total_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'totalOutstanding')::NUMERIC
              OR account_row.last_payment_value_date IS DISTINCT FROM
                    NULLIF(outcome_row.outcome_json -> 'accountBalance'
                        ->> 'lastPaymentValueDate', '')::DATE
              OR account_row.last_payment_recorded_at IS DISTINCT FROM
                    NULLIF(outcome_row.outcome_json -> 'accountBalance'
                        ->> 'lastPaymentRecordedAt', '')::TIMESTAMP
              OR account_row.servicing_evaluation_date <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'servicingEvaluationDate')::DATE
          )
    ) THEN
        RAISE EXCEPTION 'Repayment outcome account balance is inconsistent';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(outcome_row.outcome_json -> 'installments')
            WITH ORDINALITY AS outcome_item(value, ordinality)
        LEFT JOIN repayment_installment_progress progress_row
          ON progress_row.repayment_schedule_item_id =
                (outcome_item.value -> 'progress'
                    ->> 'repaymentScheduleItemId')::UUID
        LEFT JOIN repayment_schedule_items schedule_item
          ON schedule_item.id = progress_row.repayment_schedule_item_id
        WHERE progress_row.repayment_schedule_item_id IS NULL
           OR progress_row.repayment_schedule_id <>
                (outcome_item.value -> 'progress'
                    ->> 'repaymentScheduleId')::UUID
           OR progress_row.repayment_schedule_id <> outcome_row.repayment_schedule_id
           OR progress_row.loan_account_id <>
                (outcome_item.value -> 'progress' ->> 'loanAccountId')::UUID
           OR progress_row.loan_account_id <> outcome_row.loan_account_id
           OR schedule_item.repayment_schedule_id <> outcome_row.repayment_schedule_id
           OR progress_row.installment_number <>
                (outcome_item.value -> 'progress' ->> 'installmentNumber')::INTEGER
           OR progress_row.installment_number <> outcome_item.ordinality
           OR progress_row.principal_paid <>
                (outcome_item.value -> 'progress' ->> 'principalPaid')::NUMERIC
           OR progress_row.interest_paid <>
                (outcome_item.value -> 'progress' ->> 'interestPaid')::NUMERIC
           OR progress_row.fee_paid <>
                (outcome_item.value -> 'progress' ->> 'feePaid')::NUMERIC
           OR progress_row.total_paid <>
                (outcome_item.value -> 'progress' ->> 'totalPaid')::NUMERIC
           OR progress_row.principal_outstanding <>
                (outcome_item.value -> 'progress'
                    ->> 'principalOutstanding')::NUMERIC
           OR progress_row.interest_outstanding <>
                (outcome_item.value -> 'progress'
                    ->> 'interestOutstanding')::NUMERIC
           OR progress_row.fee_outstanding <>
                (outcome_item.value -> 'progress' ->> 'feeOutstanding')::NUMERIC
           OR progress_row.total_outstanding <>
                (outcome_item.value -> 'progress' ->> 'totalOutstanding')::NUMERIC
           OR progress_row.status <>
                outcome_item.value -> 'progress' ->> 'status'
           OR progress_row.last_payment_value_date IS DISTINCT FROM
                NULLIF(outcome_item.value -> 'progress'
                    ->> 'lastPaymentValueDate', '')::DATE
           OR progress_row.last_payment_recorded_at IS DISTINCT FROM
                NULLIF(outcome_item.value -> 'progress'
                    ->> 'lastPaymentRecordedAt', '')::TIMESTAMP
           OR progress_row.servicing_evaluation_date <>
                (outcome_item.value -> 'progress'
                    ->> 'servicingEvaluationDate')::DATE
           OR progress_row.updated_at <>
                (outcome_item.value -> 'progress' ->> 'updatedAt')::TIMESTAMP
    ) THEN
        RAISE EXCEPTION 'Repayment outcome installment progress is inconsistent';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_operation_outcome()
RETURNS trigger AS $$
BEGIN
    PERFORM validate_repayment_operation_outcome_evidence(
        NEW.repayment_transaction_id
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_operation_completeness()
RETURNS trigger AS $$
BEGIN
    PERFORM validate_repayment_operation_outcome_evidence(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_repayment_operation_outcome_reconcile
    AFTER INSERT ON repayment_operation_outcomes
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_operation_outcome();

CREATE CONSTRAINT TRIGGER trg_repayment_operation_transaction_completeness
    AFTER INSERT ON repayment_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_operation_completeness();

-- V35: overdue evaluation candidate support
DO $$
DECLARE
    actual_values TEXT[];
    expected_values TEXT[];
    status_constraint_count INTEGER;
BEGIN
    IF to_regclass(current_schema() || '.idx_loan_accounts_overdue_candidates')
            IS NOT NULL THEN
        RAISE EXCEPTION
            'V35 preflight failed: overdue candidate index already exists';
    END IF;

    IF to_regclass(current_schema() || '.loan_accounts') IS NULL THEN
        RAISE EXCEPTION 'V35 preflight failed: loan_accounts is missing';
    END IF;

    SELECT array_agg(
        attribute.attname || ':'
            || pg_catalog.format_type(attribute.atttypid, attribute.atttypmod)
            || ':' || attribute.attnotnull::TEXT
        ORDER BY attribute.attname
    )
    INTO actual_values
    FROM pg_attribute attribute
    JOIN pg_class relation ON relation.oid = attribute.attrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'loan_accounts'
      AND relation.relkind = 'r'
      AND attribute.attnum > 0
      AND NOT attribute.attisdropped
      AND attribute.attname IN (
          'id', 'servicing_evaluation_date', 'status', 'total_outstanding'
      );

    expected_values := ARRAY[
        'id:uuid:true',
        'servicing_evaluation_date:date:true',
        'status:character varying(30):true',
        'total_outstanding:numeric(19,2):true'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount candidate columns do not match V34';
    END IF;

    CREATE TEMP TABLE v35_expected_loan_account_predicates (
        status VARCHAR(30) NOT NULL,
        total_outstanding NUMERIC(19,2) NOT NULL,
        CONSTRAINT chk_loan_accounts_status CHECK (
            status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED')
        ),
        CONSTRAINT chk_loan_accounts_settlement_balance CHECK (
            (status = 'SETTLED' AND total_outstanding = 0)
            OR (status IN ('ACTIVE', 'OVERDUE') AND total_outstanding > 0)
            OR status = 'CLOSED'
        )
    ) ON COMMIT DROP;

    SELECT array_agg(
        constraint_row.conname || ':'
            || regexp_replace(
                lower(pg_get_expr(
                    constraint_row.conbin,
                    constraint_row.conrelid,
                    true
                )),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO actual_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'loan_accounts'
      AND constraint_row.contype = 'c'
      AND constraint_row.conname IN (
          'chk_loan_accounts_status',
          'chk_loan_accounts_settlement_balance'
      );

    SELECT array_agg(
        constraint_row.conname || ':'
            || regexp_replace(
                lower(pg_get_expr(
                    constraint_row.conbin,
                    constraint_row.conrelid,
                    true
                )),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO expected_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v35_expected_loan_account_predicates'
      AND constraint_row.contype = 'c';

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount status predicates do not match V34';
    END IF;

    SELECT count(*)
    INTO status_constraint_count
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'loan_accounts'
      AND constraint_row.contype = 'c'
      AND pg_get_expr(
            constraint_row.conbin,
            constraint_row.conrelid,
            true
          ) ~* '(^|[^a-z_])status([^a-z_]|$)';

    IF status_constraint_count <> 2 THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount status predicates are duplicated or incompatible';
    END IF;

    IF to_regclass(current_schema() || '.repayment_operation_outcomes') IS NULL THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment_operation_outcomes is missing';
    END IF;

    CREATE TEMP TABLE v35_expected_repayment_operation_outcomes (
        repayment_transaction_id UUID,
        loan_application_id UUID NOT NULL,
        loan_account_id UUID NOT NULL,
        repayment_schedule_id UUID NOT NULL,
        received_amount NUMERIC(19,2) NOT NULL,
        payment_value_date DATE NOT NULL,
        recorded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
        principal_released NUMERIC(19,2) NOT NULL,
        account_status VARCHAR(30) NOT NULL,
        account_status_changed BOOLEAN NOT NULL,
        outcome_json JSONB NOT NULL,
        CONSTRAINT repayment_operation_outcomes_pkey
            PRIMARY KEY (repayment_transaction_id),
        CONSTRAINT chk_repayment_operation_outcome_received
            CHECK (received_amount > 0 AND received_amount = trunc(received_amount)),
        CONSTRAINT chk_repayment_operation_outcome_principal
            CHECK (principal_released >= 0
                AND principal_released = trunc(principal_released)),
        CONSTRAINT chk_repayment_operation_outcome_status
            CHECK (account_status IN ('ACTIVE', 'OVERDUE', 'SETTLED')),
        CONSTRAINT chk_repayment_operation_outcome_json
            CHECK (is_repayment_operation_outcome_json_safe(outcome_json))
    ) ON COMMIT DROP;

    SELECT array_agg(
        attribute.attname || ':'
            || pg_catalog.format_type(attribute.atttypid, attribute.atttypmod)
            || ':' || attribute.attnotnull::TEXT
            || ':' || attribute.atthasdef::TEXT
        ORDER BY attribute.attnum
    )
    INTO actual_values
    FROM pg_attribute attribute
    JOIN pg_class relation ON relation.oid = attribute.attrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'repayment_operation_outcomes'
      AND relation.relkind = 'r'
      AND attribute.attnum > 0
      AND NOT attribute.attisdropped;

    SELECT array_agg(
        attribute.attname || ':'
            || pg_catalog.format_type(attribute.atttypid, attribute.atttypmod)
            || ':' || attribute.attnotnull::TEXT
            || ':' || attribute.atthasdef::TEXT
        ORDER BY attribute.attnum
    )
    INTO expected_values
    FROM pg_attribute attribute
    JOIN pg_class relation ON relation.oid = attribute.attrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v35_expected_repayment_operation_outcomes'
      AND attribute.attnum > 0
      AND NOT attribute.attisdropped;

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment outcome columns do not match V34';
    END IF;

    SELECT array_agg(
        constraint_row.conname || ':' || constraint_row.contype::TEXT || ':'
            || constraint_row.condeferrable::TEXT || ':'
            || constraint_row.condeferred::TEXT || ':'
            || regexp_replace(
                lower(pg_get_constraintdef(constraint_row.oid, true)),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO actual_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'repayment_operation_outcomes'
      AND constraint_row.contype IN ('p', 'c');

    SELECT array_agg(
        constraint_row.conname || ':' || constraint_row.contype::TEXT || ':'
            || constraint_row.condeferrable::TEXT || ':'
            || constraint_row.condeferred::TEXT || ':'
            || regexp_replace(
                lower(pg_get_constraintdef(constraint_row.oid, true)),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO expected_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v35_expected_repayment_operation_outcomes'
      AND constraint_row.contype IN ('p', 'c');

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment outcome constraints do not match V34';
    END IF;

    SELECT array_agg(
        constraint_row.conname || ':' || constraint_row.contype::TEXT || ':'
            || constraint_row.condeferrable::TEXT || ':'
            || constraint_row.condeferred::TEXT || ':'
            || regexp_replace(
                lower(pg_get_constraintdef(constraint_row.oid, true)),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO actual_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'repayment_operation_outcomes'
      AND constraint_row.contype = 'f';

    expected_values := ARRAY[
        'fk_repayment_operation_outcome_account:f:false:false:foreignkey(loan_account_id)referencesloan_accounts(id)',
        'fk_repayment_operation_outcome_application:f:false:false:foreignkey(loan_application_id)referencesloan_applications(id)',
        'fk_repayment_operation_outcome_schedule:f:false:false:foreignkey(repayment_schedule_id)referencesrepayment_schedules(id)',
        'fk_repayment_operation_outcome_transaction:f:false:false:foreignkey(repayment_transaction_id)referencesrepayment_transactions(id)'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment outcome relationships do not match V34';
    END IF;

    SELECT array_agg(
        procedure_row.proname || ':' || procedure_row.pronargs::TEXT || ':'
            || pg_get_function_identity_arguments(procedure_row.oid) || ':'
            || procedure_row.prorettype::regtype::TEXT || ':'
            || language_row.lanname || ':'
            || md5(regexp_replace(
                lower(procedure_row.prosrc), '[[:space:]]+', '', 'g'
            ))
        ORDER BY procedure_row.proname
    )
    INTO actual_values
    FROM pg_proc procedure_row
    JOIN pg_namespace namespace ON namespace.oid = procedure_row.pronamespace
    JOIN pg_language language_row ON language_row.oid = procedure_row.prolang
    WHERE namespace.nspname = current_schema()
      AND procedure_row.proname IN (
          'enforce_loan_account_mutation_boundary',
          'reject_immutable_history_row_mutation',
          'validate_repayment_operation_completeness',
          'validate_repayment_operation_outcome',
          'validate_repayment_operation_outcome_evidence'
      );

    expected_values := ARRAY[
        'enforce_loan_account_mutation_boundary:0::trigger:plpgsql:32c22629b196f3e14c4f1d1d6ac7d3f8',
        'reject_immutable_history_row_mutation:0::trigger:plpgsql:ea83b1cb2b74be9ad02f51828d965552',
        'validate_repayment_operation_completeness:0::trigger:plpgsql:c53a1a934b92ed730466e9d2217da584',
        'validate_repayment_operation_outcome:0::trigger:plpgsql:321bd4ca5a0b56d2e343d8bcb4642580',
        'validate_repayment_operation_outcome_evidence:1:transaction_id_to_validate uuid:void:plpgsql:d554450749965a1d8f6d0cf07758d538'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount or repayment reconciliation functions do not match V34';
    END IF;

    SELECT array_agg(
        trigger_row.tgname || ':' || relation.relname || ':'
            || trigger_row.tgtype::INTEGER::TEXT || ':'
            || (trigger_row.tgconstraint <> 0)::TEXT || ':'
            || trigger_row.tgdeferrable::TEXT || ':'
            || trigger_row.tginitdeferred::TEXT || ':'
            || trigger_row.tgenabled::TEXT || ':' || procedure_row.proname
        ORDER BY trigger_row.tgname
    )
    INTO actual_values
    FROM pg_trigger trigger_row
    JOIN pg_class relation ON relation.oid = trigger_row.tgrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    JOIN pg_proc procedure_row ON procedure_row.oid = trigger_row.tgfoid
    JOIN pg_namespace procedure_namespace
      ON procedure_namespace.oid = procedure_row.pronamespace
    WHERE namespace.nspname = current_schema()
      AND procedure_namespace.nspname = current_schema()
      AND trigger_row.tgname IN (
          'trg_loan_accounts_immutable',
          'trg_repayment_operation_outcomes_immutable',
          'trg_repayment_operation_outcome_reconcile',
          'trg_repayment_operation_transaction_completeness'
      )
      AND NOT trigger_row.tgisinternal;

    expected_values := ARRAY[
        'trg_loan_accounts_immutable:loan_accounts:27:false:false:false:O:enforce_loan_account_mutation_boundary',
        'trg_repayment_operation_outcome_reconcile:repayment_operation_outcomes:5:true:true:true:O:validate_repayment_operation_outcome',
        'trg_repayment_operation_outcomes_immutable:repayment_operation_outcomes:27:false:false:false:O:reject_immutable_history_row_mutation',
        'trg_repayment_operation_transaction_completeness:repayment_transactions:5:true:true:true:O:validate_repayment_operation_completeness'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: required V34 triggers do not match exact deferred semantics';
    END IF;
END;
$$;

CREATE INDEX idx_loan_accounts_overdue_candidates
    ON loan_accounts (servicing_evaluation_date, id)
    WHERE status IN ('ACTIVE', 'OVERDUE')
      AND total_outstanding > 0;

-- V36: approved settlement and administrative closure foundation

DO $$
DECLARE
    required_role_count INTEGER;
    required_function_count INTEGER;
BEGIN
    IF to_regclass(current_schema() || '.approved_loan_settlements') IS NOT NULL
            OR to_regclass(current_schema() || '.loan_account_closures') IS NOT NULL THEN
        RAISE EXCEPTION
            'V36 preflight failed: settlement or closure evidence table already exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'repayment_transactions'
          AND column_name = 'transaction_type'
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: repayment transaction type already exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts
        WHERE status = 'CLOSED'
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: CLOSED LoanAccounts predate closure evidence';
    END IF;

    SELECT COUNT(*) INTO required_role_count
    FROM roles
    WHERE code IN ('APPROVER', 'ACCOUNTING_OFFICER');
    IF required_role_count <> 2 THEN
        RAISE EXCEPTION
            'V36 preflight failed: required settlement and closure roles are missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM permissions
        WHERE code IN ('loan:settlement:approve', 'loan:account:close')
           OR id IN (
                '00000000-0000-0000-0000-000000000244'::UUID,
                '00000000-0000-0000-0000-000000000245'::UUID
           )
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: settlement or closure permission already exists';
    END IF;

    IF to_regclass(current_schema() || '.repayment_operation_outcomes') IS NULL
            OR to_regclass(current_schema() || '.loan_account_status_transitions') IS NULL
            OR to_regclass(current_schema()
                || '.repayment_installment_status_transitions') IS NULL THEN
        RAISE EXCEPTION
            'V36 preflight failed: V33-V35 servicing foundation is incomplete';
    END IF;

    SELECT COUNT(*) INTO required_function_count
    FROM pg_proc procedure_row
    JOIN pg_namespace namespace
      ON namespace.oid = procedure_row.pronamespace
    WHERE namespace.nspname = current_schema()
      AND procedure_row.proname IN (
          'reject_immutable_history_row_mutation',
          'validate_repayment_servicing_reconciliation',
          'validate_repayment_operation_outcome_evidence'
      );
    IF required_function_count <> 3 THEN
        RAISE EXCEPTION
            'V36 preflight failed: required servicing reconciliation functions are missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        JOIN pg_class relation ON relation.oid = constraint_row.conrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = current_schema()
          AND relation.relname = 'loan_accounts'
          AND constraint_row.conname = 'chk_loan_accounts_settlement_balance'
          AND pg_get_expr(
                constraint_row.conbin,
                constraint_row.conrelid,
                true
              ) ~ 'status.*CLOSED'
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: LoanAccount settlement predicate does not match V35';
    END IF;
END;
$$;

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000244',
     'loan:settlement:approve',
     'Approve and apply an administrative full-balance settlement'),
    ('00000000-0000-0000-0000-000000000245',
     'loan:account:close',
     'Administratively close an eligible settled Loan Account');

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code = 'loan:settlement:approve'
WHERE role.code = 'APPROVER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code = 'loan:account:close'
WHERE role.code = 'ACCOUNTING_OFFICER';

ALTER TABLE repayment_transactions
    ADD COLUMN transaction_type VARCHAR(30);

UPDATE repayment_transactions
SET transaction_type = 'REPAYMENT';

ALTER TABLE repayment_transactions
    ALTER COLUMN transaction_type SET NOT NULL,
    ADD CONSTRAINT chk_repayment_transactions_type CHECK (
        transaction_type IN ('REPAYMENT', 'APPROVED_SETTLEMENT')
    );

CREATE TABLE approved_loan_settlements (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    repayment_transaction_id UUID NOT NULL,
    request_id UUID NOT NULL,
    settlement_amount NUMERIC(19,2) NOT NULL,
    approved_by_user_id UUID NOT NULL,
    approved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_approved_loan_settlement_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_approved_loan_settlement_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_accounts (id),
    CONSTRAINT fk_approved_loan_settlement_transaction
        FOREIGN KEY (repayment_transaction_id) REFERENCES repayment_transactions (id),
    CONSTRAINT fk_approved_loan_settlement_actor
        FOREIGN KEY (approved_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_approved_loan_settlement_account UNIQUE (loan_account_id),
    CONSTRAINT uq_approved_loan_settlement_transaction
        UNIQUE (repayment_transaction_id),
    CONSTRAINT uq_approved_loan_settlement_request UNIQUE (request_id),
    CONSTRAINT chk_approved_loan_settlement_amount CHECK (
        settlement_amount > 0
        AND settlement_amount = trunc(settlement_amount)
    )
);

CREATE INDEX idx_approved_loan_settlements_application
    ON approved_loan_settlements (loan_application_id, approved_at);

CREATE TABLE loan_account_closures (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    request_id UUID NOT NULL,
    closed_by_user_id UUID NOT NULL,
    closed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_account_closure_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_account_closure_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_accounts (id),
    CONSTRAINT fk_loan_account_closure_actor
        FOREIGN KEY (closed_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_account_closure_application UNIQUE (loan_application_id),
    CONSTRAINT uq_loan_account_closure_account UNIQUE (loan_account_id),
    CONSTRAINT uq_loan_account_closure_request UNIQUE (request_id)
);

CREATE TRIGGER trg_approved_loan_settlements_immutable
BEFORE UPDATE OR DELETE ON approved_loan_settlements
FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_loan_account_closures_immutable
BEFORE UPDATE OR DELETE ON loan_account_closures
FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

ALTER TABLE loan_accounts
    DROP CONSTRAINT chk_loan_accounts_settlement_balance,
    ADD CONSTRAINT chk_loan_accounts_settlement_balance CHECK (
        (status IN ('SETTLED', 'CLOSED') AND total_outstanding = 0)
        OR (status IN ('ACTIVE', 'OVERDUE') AND total_outstanding > 0)
    );

ALTER TABLE loan_account_status_transitions
    DROP CONSTRAINT chk_loan_account_status_history_statuses,
    DROP CONSTRAINT chk_loan_account_status_history_action,
    ADD CONSTRAINT chk_loan_account_status_history_statuses CHECK (
        (from_status IS NULL
            OR from_status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED'))
        AND to_status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED')
        AND from_status IS DISTINCT FROM to_status
    ),
    ADD CONSTRAINT chk_loan_account_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED',
            'APPROVED_SETTLEMENT',
            'ADMINISTRATIVE_CLOSURE'
        )
    );

ALTER TABLE repayment_installment_status_transitions
    DROP CONSTRAINT chk_repayment_installment_status_history_action,
    ADD CONSTRAINT chk_repayment_installment_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED',
            'APPROVED_SETTLEMENT'
        )
    );

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action,
    DROP CONSTRAINT chk_audit_events_entity_type,
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED',
        'CUSTOMER_PROFILE_COMPLETED', 'CUSTOMER_BANK_ACCOUNT_ADDED',
        'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
        'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED',
        'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED',
        'RESERVATION_RELEASED', 'DOCUMENT_CHECKLIST_CREATED',
        'DOCUMENT_CHECKLIST_ITEM_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED',
        'DOCUMENT_REPLACEMENT_REQUESTED', 'DOCUMENT_UPLOADS_COMPLETED',
        'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED',
        'CORRECTION_RESUBMITTED', 'SALARY_ADVANCE_REVALIDATED',
        'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED', 'LOAN_ACCOUNT_STATUS_CHANGED',
        'LOAN_SETTLEMENT_APPROVED', 'LOAN_ACCOUNT_CLOSED'
    )),
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION',
        'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT',
        'REPAYMENT_TRANSACTION', 'LOAN_ACCOUNT',
        'LOAN_SETTLEMENT', 'LOAN_ACCOUNT_CLOSURE'
    ));

CREATE OR REPLACE FUNCTION validate_repayment_servicing_reconciliation()
RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM repayment_transactions transaction_row
        LEFT JOIN manual_disbursements disbursement_row
            ON disbursement_row.loan_account_id = transaction_row.loan_account_id
        LEFT JOIN (
            SELECT
                repayment_transaction_id,
                COUNT(*) AS allocation_count,
                MIN(allocation_sequence) AS minimum_sequence,
                MAX(allocation_sequence) AS maximum_sequence,
                COALESCE(SUM(amount), 0) AS allocated_amount
            FROM repayment_allocations
            GROUP BY repayment_transaction_id
        ) allocation_totals
            ON allocation_totals.repayment_transaction_id = transaction_row.id
        WHERE disbursement_row.id IS NULL
           OR transaction_row.payment_value_date
                < disbursement_row.disbursement_value_date
           OR transaction_row.payment_value_date > transaction_row.recorded_at::date
           OR COALESCE(allocation_totals.allocation_count, 0) = 0
           OR allocation_totals.minimum_sequence <> 1
           OR allocation_totals.maximum_sequence
                <> allocation_totals.allocation_count
           OR allocation_totals.allocated_amount
                <> transaction_row.received_amount
    ) THEN
        RAISE EXCEPTION
            'Repayment transaction does not reconcile to allocation or date evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_allocations allocation_row
        JOIN repayment_transactions transaction_row
            ON transaction_row.id = allocation_row.repayment_transaction_id
        JOIN repayment_schedule_items item
            ON item.id = allocation_row.repayment_schedule_item_id
        WHERE item.repayment_schedule_id <> transaction_row.repayment_schedule_id
    ) THEN
        RAISE EXCEPTION
            'Repayment allocation does not belong to the transaction final schedule';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_schedule_items item
        LEFT JOIN (
            SELECT
                repayment_schedule_item_id,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'PRINCIPAL'
                ), 0) AS principal_paid,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'INTEREST'
                ), 0) AS interest_paid,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'FEE'
                ), 0) AS fee_paid
            FROM repayment_allocations
            GROUP BY repayment_schedule_item_id
        ) allocated
            ON allocated.repayment_schedule_item_id = item.id
        LEFT JOIN repayment_installment_progress progress
            ON progress.repayment_schedule_item_id = item.id
        WHERE progress.repayment_schedule_item_id IS NULL
           OR COALESCE(allocated.principal_paid, 0) > item.principal_due
           OR COALESCE(allocated.interest_paid, 0) > item.interest_due
           OR COALESCE(allocated.fee_paid, 0) > item.fee_due
           OR progress.principal_paid
                <> COALESCE(allocated.principal_paid, 0)
           OR progress.interest_paid
                <> COALESCE(allocated.interest_paid, 0)
           OR progress.fee_paid <> COALESCE(allocated.fee_paid, 0)
           OR progress.principal_outstanding
                <> item.principal_due - COALESCE(allocated.principal_paid, 0)
           OR progress.interest_outstanding
                <> item.interest_due - COALESCE(allocated.interest_paid, 0)
           OR progress.fee_outstanding
                <> item.fee_due - COALESCE(allocated.fee_paid, 0)
           OR progress.total_paid
                <> COALESCE(allocated.principal_paid, 0)
                    + COALESCE(allocated.interest_paid, 0)
                    + COALESCE(allocated.fee_paid, 0)
           OR progress.total_outstanding
                <> item.total_due
                    - COALESCE(allocated.principal_paid, 0)
                    - COALESCE(allocated.interest_paid, 0)
                    - COALESCE(allocated.fee_paid, 0)
           OR progress.status <> CASE
                WHEN progress.total_outstanding = 0 THEN 'PAID'
                WHEN item.due_date < progress.servicing_evaluation_date
                    THEN 'OVERDUE'
                WHEN progress.total_paid > 0 THEN 'PARTIALLY_PAID'
                WHEN item.due_date = progress.servicing_evaluation_date
                    THEN 'DUE'
                ELSE 'NOT_DUE'
              END
           OR progress.last_payment_value_date IS DISTINCT FROM (
                SELECT MAX(transaction_for_item.payment_value_date)
                FROM repayment_allocations allocation_for_item
                JOIN repayment_transactions transaction_for_item
                    ON transaction_for_item.id
                        = allocation_for_item.repayment_transaction_id
                WHERE allocation_for_item.repayment_schedule_item_id = item.id
              )
           OR progress.last_payment_recorded_at IS DISTINCT FROM (
                SELECT MAX(transaction_for_item.recorded_at)
                FROM repayment_allocations allocation_for_item
                JOIN repayment_transactions transaction_for_item
                    ON transaction_for_item.id
                        = allocation_for_item.repayment_transaction_id
                WHERE allocation_for_item.repayment_schedule_item_id = item.id
              )
    ) THEN
        RAISE EXCEPTION
            'Repayment installment progress does not reconcile to immutable schedule and allocations';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        LEFT JOIN (
            SELECT
                loan_account_id,
                COUNT(*) AS installment_count,
                COALESCE(SUM(principal_paid), 0) AS principal_paid,
                COALESCE(SUM(interest_paid), 0) AS interest_paid,
                COALESCE(SUM(fee_paid), 0) AS fee_paid,
                COALESCE(SUM(total_paid), 0) AS total_paid,
                COALESCE(SUM(principal_outstanding), 0)
                    AS principal_outstanding,
                COALESCE(SUM(interest_outstanding), 0)
                    AS interest_outstanding,
                COALESCE(SUM(fee_outstanding), 0) AS fee_outstanding,
                COALESCE(SUM(total_outstanding), 0) AS total_outstanding,
                COUNT(*) FILTER (WHERE status = 'OVERDUE') AS overdue_count,
                MAX(servicing_evaluation_date) AS maximum_evaluation_date,
                MIN(servicing_evaluation_date) AS minimum_evaluation_date
            FROM repayment_installment_progress
            GROUP BY loan_account_id
        ) progress_totals
            ON progress_totals.loan_account_id = account_row.id
        LEFT JOIN loan_account_closures closure_row
            ON closure_row.loan_account_id = account_row.id
        WHERE COALESCE(progress_totals.installment_count, 0)
                <> account_row.approved_term_months
           OR progress_totals.principal_paid <> account_row.principal_paid
           OR progress_totals.interest_paid <> account_row.interest_paid
           OR progress_totals.fee_paid <> account_row.fee_paid
           OR progress_totals.total_paid <> account_row.total_paid
           OR progress_totals.principal_outstanding
                <> account_row.principal_outstanding
           OR progress_totals.interest_outstanding
                <> account_row.interest_outstanding
           OR progress_totals.fee_outstanding
                <> account_row.fee_outstanding
           OR progress_totals.total_outstanding
                <> account_row.total_outstanding
           OR progress_totals.minimum_evaluation_date
                <> progress_totals.maximum_evaluation_date
           OR account_row.servicing_evaluation_date
                <> progress_totals.maximum_evaluation_date
           OR account_row.status <> CASE
                WHEN closure_row.loan_account_id IS NOT NULL THEN 'CLOSED'
                WHEN account_row.total_outstanding = 0 THEN 'SETTLED'
                WHEN progress_totals.overdue_count > 0 THEN 'OVERDUE'
                ELSE 'ACTIVE'
              END
           OR account_row.last_payment_value_date IS DISTINCT FROM (
                SELECT MAX(transaction_row.payment_value_date)
                FROM repayment_transactions transaction_row
                WHERE transaction_row.loan_account_id = account_row.id
              )
           OR account_row.last_payment_recorded_at IS DISTINCT FROM (
                SELECT MAX(transaction_row.recorded_at)
                FROM repayment_transactions transaction_row
                WHERE transaction_row.loan_account_id = account_row.id
              )
    ) THEN
        RAISE EXCEPTION
            'LoanAccount servicing rollup does not reconcile to installment progress';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_transactions transaction_row
        LEFT JOIN (
            SELECT
                repayment_transaction_id,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'PRINCIPAL'
                ), 0) AS principal_allocated
            FROM repayment_allocations
            GROUP BY repayment_transaction_id
        ) principal
            ON principal.repayment_transaction_id = transaction_row.id
        LEFT JOIN salary_advance_limit_movements release
            ON release.repayment_transaction_id = transaction_row.id
           AND release.movement_type = 'REPAID_RELEASED'
        LEFT JOIN salary_advance_limit_movements conversion
            ON conversion.loan_application_id
                = transaction_row.loan_application_id
           AND conversion.loan_account_id = transaction_row.loan_account_id
           AND conversion.movement_type = 'DISBURSED_TO_USED'
        WHERE (
                COALESCE(principal.principal_allocated, 0) = 0
                AND release.id IS NOT NULL
              )
           OR (
                COALESCE(principal.principal_allocated, 0) > 0
                AND (
                    release.id IS NULL
                    OR release.amount <> principal.principal_allocated
                    OR release.loan_application_id
                        <> transaction_row.loan_application_id
                    OR release.loan_account_id <> transaction_row.loan_account_id
                    OR conversion.id IS NULL
                    OR release.salary_advance_limit_id
                        <> conversion.salary_advance_limit_id
                )
              )
    ) THEN
        RAISE EXCEPTION
            'Salary Advance repayment release does not equal principal allocation';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements movement
        WHERE movement.movement_type = 'REPAID_RELEASED'
          AND NOT EXISTS (
              SELECT 1
              FROM repayment_transactions transaction_row
              WHERE transaction_row.id = movement.repayment_transaction_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements movement
        GROUP BY movement.salary_advance_limit_id
        HAVING COALESCE(SUM(movement.amount) FILTER (
                    WHERE movement.movement_type = 'REPAID_RELEASED'
               ), 0)
            > COALESCE(SUM(movement.amount) FILTER (
                    WHERE movement.movement_type = 'DISBURSED_TO_USED'
              ), 0)
    ) THEN
        RAISE EXCEPTION
            'Salary Advance repayment release exceeds converted principal';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_operation_outcome_evidence(
    transaction_id_to_validate UUID
)
RETURNS VOID AS $$
DECLARE
    transaction_row repayment_transactions%ROWTYPE;
    outcome_row repayment_operation_outcomes%ROWTYPE;
    outcome_count INTEGER;
    principal_total NUMERIC(19,2);
    release_count INTEGER;
    release_total NUMERIC(19,2);
    repayment_audit_count INTEGER;
    correct_repayment_audit_count INTEGER;
    account_audit_count INTEGER;
    correct_account_audit_count INTEGER;
    account_transition_count INTEGER;
    correct_account_transition_count INTEGER;
    schedule_item_count INTEGER;
    settlement_row approved_loan_settlements%ROWTYPE;
    expected_operation_action VARCHAR(40);
    expected_operation_audit_action VARCHAR(80);
    expected_operation_entity_type VARCHAR(80);
    expected_operation_entity_id UUID;
BEGIN
    SELECT COUNT(*) INTO outcome_count
    FROM repayment_operation_outcomes
    WHERE repayment_transaction_id = transaction_id_to_validate;
    IF outcome_count <> 1 THEN
        RAISE EXCEPTION 'Repayment transaction requires exactly one operation outcome';
    END IF;

    SELECT * INTO outcome_row
    FROM repayment_operation_outcomes
    WHERE repayment_transaction_id = transaction_id_to_validate;

    SELECT * INTO transaction_row
    FROM repayment_transactions
    WHERE id = transaction_id_to_validate;
    IF NOT FOUND
            OR transaction_row.loan_application_id <> outcome_row.loan_application_id
            OR transaction_row.loan_account_id <> outcome_row.loan_account_id
            OR transaction_row.repayment_schedule_id <> outcome_row.repayment_schedule_id
            OR transaction_row.received_amount <> outcome_row.received_amount
            OR transaction_row.payment_value_date <> outcome_row.payment_value_date
            OR transaction_row.recorded_at <> outcome_row.recorded_at THEN
        RAISE EXCEPTION 'Repayment outcome identity conflicts with transaction evidence';
    END IF;

    IF transaction_row.transaction_type = 'APPROVED_SETTLEMENT' THEN
        SELECT * INTO settlement_row
        FROM approved_loan_settlements
        WHERE repayment_transaction_id = transaction_id_to_validate;

        IF NOT FOUND
                OR settlement_row.loan_application_id
                    <> transaction_row.loan_application_id
                OR settlement_row.loan_account_id <> transaction_row.loan_account_id
                OR settlement_row.request_id <> transaction_row.request_id
                OR settlement_row.settlement_amount <> transaction_row.received_amount
                OR settlement_row.approved_by_user_id
                    <> transaction_row.recorded_by_user_id
                OR settlement_row.approved_at <> transaction_row.recorded_at
                OR outcome_row.account_status <> 'SETTLED'
                OR NOT outcome_row.account_status_changed THEN
            RAISE EXCEPTION
                'Approved settlement identity conflicts with payment or outcome evidence';
        END IF;

        expected_operation_action := 'APPROVED_SETTLEMENT';
        expected_operation_audit_action := 'LOAN_SETTLEMENT_APPROVED';
        expected_operation_entity_type := 'LOAN_SETTLEMENT';
        expected_operation_entity_id := settlement_row.id;
    ELSE
        IF transaction_row.transaction_type <> 'REPAYMENT'
                OR EXISTS (
                    SELECT 1
                    FROM approved_loan_settlements settlement
                    WHERE settlement.repayment_transaction_id
                        = transaction_id_to_validate
                ) THEN
            RAISE EXCEPTION
                'Repayment transaction type conflicts with settlement evidence';
        END IF;

        expected_operation_action := 'REPAYMENT_RECORDED';
        expected_operation_audit_action := 'REPAYMENT_RECORDED';
        expected_operation_entity_type := 'REPAYMENT_TRANSACTION';
        expected_operation_entity_id := transaction_id_to_validate;
    END IF;

    SELECT COALESCE(SUM(amount), 0) INTO principal_total
    FROM repayment_allocations
    WHERE repayment_transaction_id = transaction_id_to_validate
      AND component = 'PRINCIPAL';
    IF principal_total <> outcome_row.principal_released THEN
        RAISE EXCEPTION 'Repayment outcome principal conflicts with allocations';
    END IF;

    SELECT COUNT(*), COALESCE(SUM(amount), 0)
    INTO release_count, release_total
    FROM salary_advance_limit_movements
    WHERE repayment_transaction_id = transaction_id_to_validate
      AND movement_type = 'REPAID_RELEASED';
    IF (principal_total = 0 AND release_count <> 0)
            OR (principal_total > 0
                AND (release_count <> 1 OR release_total <> principal_total)) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with exposure release evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = expected_operation_entity_type
          AND entity_id = expected_operation_entity_id
          AND actor_user_id = transaction_row.recorded_by_user_id
    )
    INTO repayment_audit_count, correct_repayment_audit_count
    FROM audit_events
    WHERE operation_id = transaction_id_to_validate
      AND action = expected_operation_audit_action;
    IF repayment_audit_count <> 1 OR correct_repayment_audit_count <> 1 THEN
        RAISE EXCEPTION 'Repayment outcome requires exact repayment audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_ACCOUNT'
          AND entity_id = outcome_row.loan_account_id
    )
    INTO account_audit_count, correct_account_audit_count
    FROM audit_events
    WHERE operation_id = transaction_id_to_validate
      AND action = 'LOAN_ACCOUNT_STATUS_CHANGED';
    IF account_audit_count <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END)
            OR correct_account_audit_count
                <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with account audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE loan_account_id = outcome_row.loan_account_id
          AND action = expected_operation_action
          AND actor_type = 'USER'
          AND actor_user_id = transaction_row.recorded_by_user_id
          AND occurred_at = transaction_row.recorded_at
          AND to_status = outcome_row.account_status
    )
    INTO account_transition_count, correct_account_transition_count
    FROM loan_account_status_transitions
    WHERE operation_id = transaction_id_to_validate;
    IF account_transition_count
            <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END)
            OR correct_account_transition_count
                <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with account transition evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(outcome_row.outcome_json -> 'installments') item
        LEFT JOIN repayment_installment_status_transitions transition_row
          ON transition_row.operation_id = transaction_id_to_validate
         AND transition_row.repayment_schedule_item_id =
                (item -> 'progress' ->> 'repaymentScheduleItemId')::UUID
        WHERE (
            (item ->> 'statusChanged')::BOOLEAN
            AND (
                transition_row.id IS NULL
                OR transition_row.from_status <> item ->> 'previousStatus'
                OR transition_row.to_status <> item -> 'progress' ->> 'status'
                OR transition_row.action <> expected_operation_action
                OR transition_row.actor_type <> 'USER'
                OR transition_row.actor_user_id
                    <> transaction_row.recorded_by_user_id
                OR transition_row.occurred_at <> transaction_row.recorded_at
            )
        ) OR (
            NOT (item ->> 'statusChanged')::BOOLEAN
            AND transition_row.id IS NOT NULL
        )
    ) OR EXISTS (
        SELECT 1
        FROM repayment_installment_status_transitions transition_row
        WHERE transition_row.operation_id = transaction_id_to_validate
          AND NOT EXISTS (
              SELECT 1
              FROM jsonb_array_elements(
                  outcome_row.outcome_json -> 'installments'
              ) item
              WHERE (item -> 'progress' ->> 'repaymentScheduleItemId')::UUID
                    = transition_row.repayment_schedule_item_id
          )
    ) THEN
        RAISE EXCEPTION
            'Repayment outcome conflicts with item-specific installment transition evidence';
    END IF;

    SELECT COUNT(*) INTO schedule_item_count
    FROM repayment_schedule_items
    WHERE repayment_schedule_id = outcome_row.repayment_schedule_id;
    IF jsonb_array_length(outcome_row.outcome_json -> 'installments')
                <> schedule_item_count
            OR (outcome_row.outcome_json ->> 'repaymentTransactionId')::UUID
                <> outcome_row.repayment_transaction_id
            OR (outcome_row.outcome_json ->> 'loanApplicationId')::UUID
                <> outcome_row.loan_application_id
            OR (outcome_row.outcome_json ->> 'loanAccountId')::UUID
                <> outcome_row.loan_account_id
            OR (outcome_row.outcome_json ->> 'repaymentScheduleId')::UUID
                <> outcome_row.repayment_schedule_id
            OR (outcome_row.outcome_json ->> 'receivedAmount')::NUMERIC
                <> outcome_row.received_amount
            OR (outcome_row.outcome_json ->> 'paymentValueDate')::DATE
                <> outcome_row.payment_value_date
            OR (outcome_row.outcome_json ->> 'recordedAt')::TIMESTAMP
                <> outcome_row.recorded_at
            OR (outcome_row.outcome_json ->> 'principalReleased')::NUMERIC
                <> outcome_row.principal_released
            OR outcome_row.outcome_json ->> 'accountStatus'
                <> outcome_row.account_status
            OR (outcome_row.outcome_json ->> 'accountStatusChanged')::BOOLEAN
                <> outcome_row.account_status_changed THEN
        RAISE EXCEPTION 'Repayment outcome JSON conflicts with typed evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        WHERE account_row.id = outcome_row.loan_account_id
          AND (
              account_row.status <> outcome_row.account_status
              OR account_row.principal_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'principalPaid')::NUMERIC
              OR account_row.interest_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'interestPaid')::NUMERIC
              OR account_row.fee_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'feePaid')::NUMERIC
              OR account_row.total_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'totalPaid')::NUMERIC
              OR account_row.principal_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'principalOutstanding')::NUMERIC
              OR account_row.interest_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'interestOutstanding')::NUMERIC
              OR account_row.fee_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'feeOutstanding')::NUMERIC
              OR account_row.total_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'totalOutstanding')::NUMERIC
              OR account_row.last_payment_value_date IS DISTINCT FROM
                    NULLIF(outcome_row.outcome_json -> 'accountBalance'
                        ->> 'lastPaymentValueDate', '')::DATE
              OR account_row.last_payment_recorded_at IS DISTINCT FROM
                    NULLIF(outcome_row.outcome_json -> 'accountBalance'
                        ->> 'lastPaymentRecordedAt', '')::TIMESTAMP
              OR account_row.servicing_evaluation_date <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'servicingEvaluationDate')::DATE
          )
    ) THEN
        RAISE EXCEPTION 'Repayment outcome account balance is inconsistent';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(outcome_row.outcome_json -> 'installments')
            WITH ORDINALITY AS outcome_item(value, ordinality)
        LEFT JOIN repayment_installment_progress progress_row
          ON progress_row.repayment_schedule_item_id =
                (outcome_item.value -> 'progress'
                    ->> 'repaymentScheduleItemId')::UUID
        LEFT JOIN repayment_schedule_items schedule_item
          ON schedule_item.id = progress_row.repayment_schedule_item_id
        WHERE progress_row.repayment_schedule_item_id IS NULL
           OR progress_row.repayment_schedule_id <>
                (outcome_item.value -> 'progress'
                    ->> 'repaymentScheduleId')::UUID
           OR progress_row.repayment_schedule_id <> outcome_row.repayment_schedule_id
           OR progress_row.loan_account_id <>
                (outcome_item.value -> 'progress' ->> 'loanAccountId')::UUID
           OR progress_row.loan_account_id <> outcome_row.loan_account_id
           OR schedule_item.repayment_schedule_id <> outcome_row.repayment_schedule_id
           OR progress_row.installment_number <>
                (outcome_item.value -> 'progress' ->> 'installmentNumber')::INTEGER
           OR progress_row.installment_number <> outcome_item.ordinality
           OR progress_row.principal_paid <>
                (outcome_item.value -> 'progress' ->> 'principalPaid')::NUMERIC
           OR progress_row.interest_paid <>
                (outcome_item.value -> 'progress' ->> 'interestPaid')::NUMERIC
           OR progress_row.fee_paid <>
                (outcome_item.value -> 'progress' ->> 'feePaid')::NUMERIC
           OR progress_row.total_paid <>
                (outcome_item.value -> 'progress' ->> 'totalPaid')::NUMERIC
           OR progress_row.principal_outstanding <>
                (outcome_item.value -> 'progress'
                    ->> 'principalOutstanding')::NUMERIC
           OR progress_row.interest_outstanding <>
                (outcome_item.value -> 'progress'
                    ->> 'interestOutstanding')::NUMERIC
           OR progress_row.fee_outstanding <>
                (outcome_item.value -> 'progress' ->> 'feeOutstanding')::NUMERIC
           OR progress_row.total_outstanding <>
                (outcome_item.value -> 'progress' ->> 'totalOutstanding')::NUMERIC
           OR progress_row.status <>
                outcome_item.value -> 'progress' ->> 'status'
           OR progress_row.last_payment_value_date IS DISTINCT FROM
                NULLIF(outcome_item.value -> 'progress'
                    ->> 'lastPaymentValueDate', '')::DATE
           OR progress_row.last_payment_recorded_at IS DISTINCT FROM
                NULLIF(outcome_item.value -> 'progress'
                    ->> 'lastPaymentRecordedAt', '')::TIMESTAMP
           OR progress_row.servicing_evaluation_date <>
                (outcome_item.value -> 'progress'
                    ->> 'servicingEvaluationDate')::DATE
           OR progress_row.updated_at <>
                (outcome_item.value -> 'progress' ->> 'updatedAt')::TIMESTAMP
    ) THEN
        RAISE EXCEPTION 'Repayment outcome installment progress is inconsistent';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_approved_loan_settlement()
RETURNS trigger AS $$
BEGIN
    PERFORM validate_repayment_operation_outcome_evidence(
        COALESCE(NEW.repayment_transaction_id, OLD.repayment_transaction_id)
    );
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_approved_loan_settlement_reconcile
AFTER INSERT ON approved_loan_settlements
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_approved_loan_settlement();

CREATE OR REPLACE FUNCTION validate_loan_account_closure_evidence()
RETURNS trigger AS $$
DECLARE
    closure_row loan_account_closures%ROWTYPE;
    account_row loan_accounts%ROWTYPE;
    settled_transition loan_account_status_transitions%ROWTYPE;
    closure_transition_count INTEGER;
    correct_closure_transition_count INTEGER;
    closure_audit_count INTEGER;
    correct_closure_audit_count INTEGER;
    account_audit_count INTEGER;
    correct_account_audit_count INTEGER;
BEGIN
    SELECT * INTO closure_row
    FROM loan_account_closures
    WHERE id = COALESCE(NEW.id, OLD.id);

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT * INTO account_row
    FROM loan_accounts
    WHERE id = closure_row.loan_account_id;

    IF NOT FOUND
            OR account_row.loan_application_id <> closure_row.loan_application_id
            OR account_row.status <> 'CLOSED'
            OR account_row.total_outstanding <> 0
            OR account_row.updated_at <> closure_row.closed_at THEN
        RAISE EXCEPTION
            'LoanAccount closure evidence conflicts with the administrative account state';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_installment_progress progress
        WHERE progress.loan_account_id = closure_row.loan_account_id
          AND (
              progress.status <> 'PAID'
              OR progress.principal_outstanding <> 0
              OR progress.interest_outstanding <> 0
              OR progress.fee_outstanding <> 0
              OR progress.total_outstanding <> 0
          )
    ) THEN
        RAISE EXCEPTION
            'LoanAccount closure requires fully paid installment servicing progress';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE loan_account_id = closure_row.loan_account_id
          AND from_status = 'SETTLED'
          AND to_status = 'CLOSED'
          AND action = 'ADMINISTRATIVE_CLOSURE'
          AND actor_type = 'USER'
          AND actor_user_id = closure_row.closed_by_user_id
          AND servicing_evaluation_date = account_row.servicing_evaluation_date
          AND occurred_at = closure_row.closed_at
    )
    INTO closure_transition_count, correct_closure_transition_count
    FROM loan_account_status_transitions
    WHERE operation_id = closure_row.id;

    IF closure_transition_count <> 1 OR correct_closure_transition_count <> 1 THEN
        RAISE EXCEPTION
            'LoanAccount closure requires exact status transition evidence';
    END IF;

    SELECT transition_row.* INTO settled_transition
    FROM loan_account_status_transitions transition_row
    WHERE transition_row.loan_account_id = closure_row.loan_account_id
      AND transition_row.to_status = 'SETTLED'
      AND transition_row.sequence_number = (
          SELECT MAX(prior.sequence_number)
          FROM loan_account_status_transitions prior
          WHERE prior.loan_account_id = closure_row.loan_account_id
            AND prior.sequence_number < (
                SELECT sequence_number
                FROM loan_account_status_transitions
                WHERE operation_id = closure_row.id
            )
      );

    IF NOT FOUND
            OR settled_transition.action NOT IN (
                'REPAYMENT_RECORDED', 'APPROVED_SETTLEMENT'
            )
            OR NOT EXISTS (
                SELECT 1
                FROM repayment_operation_outcomes outcome_row
                WHERE outcome_row.repayment_transaction_id
                    = settled_transition.operation_id
                  AND outcome_row.loan_account_id = closure_row.loan_account_id
                  AND outcome_row.account_status = 'SETTLED'
                  AND outcome_row.account_status_changed
            )
            OR (
                settled_transition.action = 'APPROVED_SETTLEMENT'
                AND NOT EXISTS (
                    SELECT 1
                    FROM approved_loan_settlements settlement
                    WHERE settlement.repayment_transaction_id
                        = settled_transition.operation_id
                      AND settlement.loan_account_id
                        = closure_row.loan_account_id
                )
            ) THEN
        RAISE EXCEPTION
            'LoanAccount closure requires consistent contractual payoff or approved settlement evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_applications application_row
        JOIN loan_products product_row
          ON product_row.id = application_row.loan_product_id
        WHERE application_row.id = closure_row.loan_application_id
          AND product_row.product_code = 'SALARY_ADVANCE'
          AND (
              COALESCE((
                  SELECT SUM(movement.amount)
                  FROM salary_advance_limit_movements movement
                  WHERE movement.loan_account_id = closure_row.loan_account_id
                    AND movement.movement_type = 'DISBURSED_TO_USED'
              ), 0) <> account_row.approved_principal
              OR COALESCE((
                  SELECT SUM(movement.amount)
                  FROM salary_advance_limit_movements movement
                  WHERE movement.loan_account_id = closure_row.loan_account_id
                    AND movement.movement_type = 'REPAID_RELEASED'
              ), 0) <> account_row.approved_principal
          )
    ) THEN
        RAISE EXCEPTION
            'Salary Advance closure requires full principal exposure release';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_ACCOUNT_CLOSURE'
          AND entity_id = closure_row.id
          AND actor_user_id = closure_row.closed_by_user_id
    )
    INTO closure_audit_count, correct_closure_audit_count
    FROM audit_events
    WHERE operation_id = closure_row.id
      AND action = 'LOAN_ACCOUNT_CLOSED';

    IF closure_audit_count <> 1 OR correct_closure_audit_count <> 1 THEN
        RAISE EXCEPTION
            'LoanAccount closure requires exact administrative audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_ACCOUNT'
          AND entity_id = closure_row.loan_account_id
          AND actor_user_id = closure_row.closed_by_user_id
    )
    INTO account_audit_count, correct_account_audit_count
    FROM audit_events
    WHERE operation_id = closure_row.id
      AND action = 'LOAN_ACCOUNT_STATUS_CHANGED';

    IF account_audit_count <> 1 OR correct_account_audit_count <> 1 THEN
        RAISE EXCEPTION
            'LoanAccount closure requires exact account-status audit evidence';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_account_closure_reconcile
AFTER INSERT ON loan_account_closures
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_loan_account_closure_evidence();

-- V37: Customer-owned cancellation of a returned Salary Advance correction.

ALTER TABLE loan_correction_requests
    ADD COLUMN cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    DROP CONSTRAINT chk_loan_correction_requests_status,
    DROP CONSTRAINT chk_loan_correction_requests_timestamps,
    ADD CONSTRAINT chk_loan_correction_requests_status CHECK (
        status IN ('OPEN', 'READY_FOR_RESUBMISSION', 'RESUBMITTED', 'CANCELLED')
    ),
    ADD CONSTRAINT chk_loan_correction_requests_timestamps CHECK (
        (status = 'OPEN'
            AND ready_at IS NULL
            AND resubmitted_at IS NULL
            AND cancelled_at IS NULL
            AND resubmission_request_id IS NULL)
        OR (status = 'READY_FOR_RESUBMISSION'
            AND ready_at IS NOT NULL
            AND resubmitted_at IS NULL
            AND cancelled_at IS NULL
            AND resubmission_request_id IS NULL)
        OR (status = 'RESUBMITTED'
            AND ready_at IS NOT NULL
            AND resubmitted_at IS NOT NULL
            AND cancelled_at IS NULL
            AND resubmission_request_id IS NOT NULL)
        OR (status = 'CANCELLED'
            AND resubmitted_at IS NULL
            AND cancelled_at IS NOT NULL
            AND resubmission_request_id IS NULL)
    );

CREATE TABLE loan_application_cancellations (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    correction_request_id UUID NOT NULL,
    reservation_release_movement_id UUID NOT NULL,
    request_id UUID NOT NULL,
    cancelled_by_user_id UUID NOT NULL,
    cancelled_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_application_cancellations_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_application_cancellations_correction
        FOREIGN KEY (correction_request_id) REFERENCES loan_correction_requests (id),
    CONSTRAINT fk_loan_application_cancellations_release
        FOREIGN KEY (reservation_release_movement_id)
        REFERENCES salary_advance_limit_movements (id),
    CONSTRAINT fk_loan_application_cancellations_actor
        FOREIGN KEY (cancelled_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_application_cancellations_application
        UNIQUE (loan_application_id),
    CONSTRAINT uq_loan_application_cancellations_correction
        UNIQUE (correction_request_id),
    CONSTRAINT uq_loan_application_cancellations_release
        UNIQUE (reservation_release_movement_id),
    CONSTRAINT uq_loan_application_cancellations_request
        UNIQUE (request_id)
);

CREATE TRIGGER trg_loan_application_cancellations_immutable
BEFORE UPDATE OR DELETE ON loan_application_cancellations
FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action,
    ADD CONSTRAINT chk_loan_application_status_transitions_action CHECK (action IN (
        'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
        'RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION',
        'REQUEST_STAFF_CORRECTION', 'APPROVE', 'REJECT',
        'RETURN_TO_LOAN_OFFICER_REVIEW', 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
        'RESUBMIT_CORRECTION', 'CANCEL_APPLICATION', 'GENERATE_APPROVED_OFFER',
        'ACCEPT_APPROVED_OFFER', 'DECLINE_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER',
        'CONFIRM_DISBURSEMENT_READINESS', 'CONFIRM_MANUAL_DISBURSEMENT'
    ));

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action,
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED',
        'CUSTOMER_PROFILE_COMPLETED', 'CUSTOMER_BANK_ACCOUNT_ADDED',
        'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
        'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED',
        'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED',
        'RESERVATION_RELEASED', 'DOCUMENT_CHECKLIST_CREATED',
        'DOCUMENT_CHECKLIST_ITEM_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED',
        'DOCUMENT_REPLACEMENT_REQUESTED', 'DOCUMENT_UPLOADS_COMPLETED',
        'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED',
        'CORRECTION_RESUBMITTED', 'LOAN_APPLICATION_CANCELLED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED',
        'LOAN_CONTRACT_SUPERSEDED', 'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED', 'LOAN_ACCOUNT_STATUS_CHANGED',
        'LOAN_SETTLEMENT_APPROVED', 'LOAN_ACCOUNT_CLOSED'
    ));

CREATE OR REPLACE FUNCTION validate_loan_application_cancellation_evidence()
RETURNS trigger AS $$
DECLARE
    cancellation_row loan_application_cancellations%ROWTYPE;
    application_row loan_applications%ROWTYPE;
    correction_row loan_correction_requests%ROWTYPE;
    release_row salary_advance_limit_movements%ROWTYPE;
    reservation_count INTEGER;
    matching_reservation_count INTEGER;
    release_count INTEGER;
    transition_count INTEGER;
    correct_transition_count INTEGER;
    cancellation_audit_count INTEGER;
    correct_cancellation_audit_count INTEGER;
    release_audit_count INTEGER;
    correct_release_audit_count INTEGER;
BEGIN
    SELECT * INTO cancellation_row
    FROM loan_application_cancellations
    WHERE id = COALESCE(NEW.id, OLD.id);

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT * INTO application_row
    FROM loan_applications
    WHERE id = cancellation_row.loan_application_id;

    SELECT * INTO correction_row
    FROM loan_correction_requests
    WHERE id = cancellation_row.correction_request_id;

    SELECT * INTO release_row
    FROM salary_advance_limit_movements
    WHERE id = cancellation_row.reservation_release_movement_id;

    IF application_row.id IS NULL
            OR application_row.product_code <> 'SALARY_ADVANCE'
            OR application_row.status <> 'CANCELLED'
            OR correction_row.id IS NULL
            OR correction_row.loan_application_id <> application_row.id
            OR correction_row.status <> 'CANCELLED'
            OR correction_row.cancelled_at <> cancellation_row.cancelled_at
            OR correction_row.resubmitted_at IS NOT NULL
            OR correction_row.resubmission_request_id IS NOT NULL
            OR release_row.id IS NULL
            OR release_row.loan_application_id <> application_row.id
            OR release_row.movement_type <> 'RESERVATION_RELEASED'
            OR release_row.amount <> application_row.requested_amount
            OR release_row.loan_account_id IS NOT NULL
            OR release_row.repayment_transaction_id IS NOT NULL
            OR release_row.occurred_at <> cancellation_row.cancelled_at
            OR NOT EXISTS (
                SELECT 1
                FROM users actor
                WHERE actor.id = cancellation_row.cancelled_by_user_id
                  AND actor.user_type = 'CUSTOMER'
                  AND actor.customer_id = application_row.customer_id
            ) THEN
        RAISE EXCEPTION
            'Loan Application cancellation conflicts with terminal correction or release evidence';
    END IF;

    SELECT
        COUNT(*),
        COUNT(*) FILTER (
            WHERE salary_advance_limit_id = release_row.salary_advance_limit_id
              AND amount = application_row.requested_amount
              AND loan_account_id IS NULL
              AND repayment_transaction_id IS NULL
        )
    INTO reservation_count, matching_reservation_count
    FROM salary_advance_limit_movements
    WHERE loan_application_id = application_row.id
      AND movement_type = 'RESERVED';

    SELECT COUNT(*) INTO release_count
    FROM salary_advance_limit_movements
    WHERE loan_application_id = application_row.id
      AND movement_type = 'RESERVATION_RELEASED';

    IF reservation_count <> 1
            OR matching_reservation_count <> 1
            OR release_count <> 1 THEN
        RAISE EXCEPTION
            'Loan Application cancellation requires one exact reservation and release';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE loan_application_id = application_row.id
          AND from_status = 'RETURNED_FOR_REVISION'
          AND to_status = 'CANCELLED'
          AND action = 'CANCEL_APPLICATION'
          AND reason = 'CUSTOMER_CANCELLATION'
          AND actor_type = 'USER'
          AND actor_user_id = cancellation_row.cancelled_by_user_id
          AND occurred_at = cancellation_row.cancelled_at
    )
    INTO transition_count, correct_transition_count
    FROM loan_application_status_transitions
    WHERE operation_id = cancellation_row.id;

    IF transition_count <> 1 OR correct_transition_count <> 1 THEN
        RAISE EXCEPTION
            'Loan Application cancellation requires exact lifecycle history';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_APPLICATION'
          AND entity_id = application_row.id
          AND actor_user_id = cancellation_row.cancelled_by_user_id
    )
    INTO cancellation_audit_count, correct_cancellation_audit_count
    FROM audit_events
    WHERE operation_id = cancellation_row.id
      AND action = 'LOAN_APPLICATION_CANCELLED';

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'SALARY_ADVANCE_LIMIT_MOVEMENT'
          AND entity_id = release_row.id
          AND actor_user_id = cancellation_row.cancelled_by_user_id
    )
    INTO release_audit_count, correct_release_audit_count
    FROM audit_events
    WHERE operation_id = cancellation_row.id
      AND action = 'RESERVATION_RELEASED';

    IF cancellation_audit_count <> 1
            OR correct_cancellation_audit_count <> 1
            OR release_audit_count <> 1
            OR correct_release_audit_count <> 1 THEN
        RAISE EXCEPTION
            'Loan Application cancellation requires exact audit evidence';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_application_cancellations_reconcile
AFTER INSERT ON loan_application_cancellations
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_loan_application_cancellation_evidence();
