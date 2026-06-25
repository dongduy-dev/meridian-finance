ALTER TABLE partner_employees
    ADD CONSTRAINT uq_partner_employees_id_partner_company
    UNIQUE (id, partner_company_id);

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
