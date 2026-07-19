-- Meridian current physical schema snapshot.
-- Documentation only. Flyway migrations under meridian-platform/src/main/resources/db/migration
-- remain the executable database history.
-- Snapshot source: migrations V1 through V23.

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
