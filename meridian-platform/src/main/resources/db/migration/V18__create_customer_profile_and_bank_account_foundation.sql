CREATE SEQUENCE customer_number_seq START WITH 1 INCREMENT BY 1;

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
    CONSTRAINT uq_customer_profiles_customer_id UNIQUE (customer_id),
    CONSTRAINT uq_customer_profiles_identity_reference_fingerprint UNIQUE (identity_reference_fingerprint),
    CONSTRAINT fk_customer_profiles_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
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
    CONSTRAINT fk_customer_bank_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
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

WITH referenced_customers AS (
    SELECT customer_id FROM users WHERE customer_id IS NOT NULL
    UNION
    SELECT customer_id FROM loan_applications WHERE customer_id IS NOT NULL
    UNION
    SELECT customer_id FROM customer_partner_employee_links WHERE customer_id IS NOT NULL
    UNION
    SELECT customer_id FROM salary_advance_limits WHERE customer_id IS NOT NULL
    UNION
    SELECT customer_id FROM salary_advance_verifications WHERE customer_id IS NOT NULL
), numbered_customers AS (
    SELECT
        customer_id,
        row_number() OVER (ORDER BY customer_id) AS sequence_number
    FROM referenced_customers
)
INSERT INTO customers (
    id,
    customer_number,
    status,
    verification_status,
    profile_completion_status
)
SELECT
    customer_id,
    'CUS-' || lpad(sequence_number::text, 9, '0'),
    'ACTIVE',
    'UNVERIFIED',
    'INCOMPLETE'
FROM numbered_customers;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE customer_id IS NOT NULL
        GROUP BY customer_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot add unique customer user mapping because duplicate users.customer_id values exist';
    END IF;
END $$;

DROP INDEX IF EXISTS idx_users_customer_id;
CREATE UNIQUE INDEX uq_users_customer_id_present ON users (customer_id) WHERE customer_id IS NOT NULL;

CREATE UNIQUE INDEX uq_customer_bank_accounts_primary_active
    ON customer_bank_accounts (customer_id)
    WHERE status = 'ACTIVE' AND primary_account = TRUE;

CREATE UNIQUE INDEX uq_customer_bank_accounts_active_fingerprint
    ON customer_bank_accounts (customer_id, account_number_fingerprint)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_customer_bank_accounts_customer_status
    ON customer_bank_accounts (customer_id, status);

ALTER TABLE users
    ADD CONSTRAINT fk_users_customer FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE loan_applications
    ADD CONSTRAINT fk_loan_applications_customer FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE customer_partner_employee_links
    ADD CONSTRAINT fk_customer_partner_employee_links_customer FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE salary_advance_limits
    ADD CONSTRAINT fk_salary_advance_limits_customer FOREIGN KEY (customer_id) REFERENCES customers (id);

ALTER TABLE salary_advance_verifications
    ADD CONSTRAINT fk_salary_advance_verifications_customer FOREIGN KEY (customer_id) REFERENCES customers (id);

SELECT setval(
    'customer_number_seq',
    COALESCE((SELECT MAX(CAST(substring(customer_number FROM 5) AS BIGINT)) FROM customers WHERE customer_number ~ '^CUS-[0-9]{9}$'), 0) + 1,
    FALSE
);