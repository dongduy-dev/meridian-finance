CREATE TABLE assisted_origination_cases (
    id UUID PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL,
    customer_id UUID,
    status VARCHAR(30) NOT NULL,
    created_by_staff_user_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    terminal_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT fk_assisted_origination_cases_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_assisted_origination_cases_creator
        FOREIGN KEY (created_by_staff_user_id) REFERENCES users (id),
    CONSTRAINT chk_assisted_origination_cases_product
        CHECK (product_code IN ('UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN')),
    CONSTRAINT chk_assisted_origination_cases_status
        CHECK (status IN ('OPEN', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT chk_assisted_origination_cases_terminal
        CHECK ((status = 'OPEN' AND terminal_at IS NULL)
            OR (status IN ('COMPLETED', 'ABANDONED') AND terminal_at IS NOT NULL))
);

CREATE INDEX idx_assisted_origination_cases_status_updated
    ON assisted_origination_cases (status, updated_at DESC, id DESC);

CREATE INDEX idx_assisted_origination_cases_customer
    ON assisted_origination_cases (customer_id)
    WHERE customer_id IS NOT NULL;

CREATE TABLE intake_documents (
    id UUID PRIMARY KEY,
    assisted_origination_case_id UUID NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    current_version_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_intake_documents_case
        FOREIGN KEY (assisted_origination_case_id) REFERENCES assisted_origination_cases (id),
    CONSTRAINT uq_intake_documents_case_type
        UNIQUE (assisted_origination_case_id, evidence_type),
    CONSTRAINT uq_intake_documents_id_case
        UNIQUE (id, assisted_origination_case_id),
    CONSTRAINT chk_intake_documents_evidence_type
        CHECK (evidence_type IN (
            'CUSTOMER_IDENTITY', 'UCL_PAPER_APPLICATION', 'COLLATERAL_PAPER_APPLICATION'
        ))
);

CREATE TABLE intake_document_versions (
    id UUID PRIMARY KEY,
    intake_document_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    upload_request_id UUID NOT NULL,
    baseline_version_id UUID,
    original_filename VARCHAR(255) NOT NULL,
    declared_mime_type VARCHAR(100) NOT NULL,
    detected_mime_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    uploader_staff_user_id UUID NOT NULL,
    uploaded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_intake_document_versions_document
        FOREIGN KEY (intake_document_id) REFERENCES intake_documents (id),
    CONSTRAINT fk_intake_document_versions_baseline
        FOREIGN KEY (baseline_version_id) REFERENCES intake_document_versions (id),
    CONSTRAINT fk_intake_document_versions_uploader
        FOREIGN KEY (uploader_staff_user_id) REFERENCES users (id),
    CONSTRAINT uq_intake_document_versions_document_sequence
        UNIQUE (intake_document_id, version_number),
    CONSTRAINT uq_intake_document_versions_upload_request
        UNIQUE (upload_request_id),
    CONSTRAINT uq_intake_document_versions_storage_key
        UNIQUE (storage_key),
    CONSTRAINT uq_intake_document_versions_id_document
        UNIQUE (id, intake_document_id),
    CONSTRAINT chk_intake_document_versions_sequence CHECK (version_number > 0),
    CONSTRAINT chk_intake_document_versions_byte_size
        CHECK (byte_size > 0 AND byte_size <= 10485760),
    CONSTRAINT chk_intake_document_versions_declared_mime
        CHECK (declared_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_intake_document_versions_detected_mime
        CHECK (detected_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_intake_document_versions_mime_match
        CHECK (declared_mime_type = detected_mime_type),
    CONSTRAINT chk_intake_document_versions_sha256
        CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_intake_document_versions_filename_safe
        CHECK (btrim(original_filename) <> ''
            AND original_filename !~ '[\\/\x00-\x1F\x7F]'
            AND original_filename NOT LIKE '%..%')
);

ALTER TABLE intake_documents
    ADD CONSTRAINT fk_intake_documents_current_version
        FOREIGN KEY (current_version_id, id)
        REFERENCES intake_document_versions (id, intake_document_id);

CREATE INDEX idx_intake_document_versions_document_uploaded
    ON intake_document_versions (intake_document_id, uploaded_at DESC);

CREATE TRIGGER trg_intake_document_versions_immutable
    BEFORE UPDATE OR DELETE ON intake_document_versions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000246', 'customer:intake:manage',
     'Create and maintain selected Customers during Staff-assisted intake'),
    ('00000000-0000-0000-0000-000000000247', 'loan:originate:staff',
     'Manage UCL and Collateral Staff-assisted origination intake'),
    ('00000000-0000-0000-0000-000000000248', 'document:upload:intake',
     'Read and upload controlled Staff-assisted intake evidence')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN (
    'customer:intake:manage', 'loan:originate:staff', 'document:upload:intake'
)
WHERE role.code = 'LOAN_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_entity_type,
    DROP CONSTRAINT chk_audit_events_action,
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION', 'DOCUMENT_REVIEW_DECISION',
        'LOAN_REVIEW_CYCLE', 'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT', 'REPAYMENT_TRANSACTION',
        'LOAN_ACCOUNT', 'LOAN_SETTLEMENT', 'LOAN_ACCOUNT_CLOSURE',
        'PARTNER_COMPANY', 'PARTNER_EMPLOYEE_IMPORT_BATCH', 'LOAN_PRODUCT',
        'IDENTITY_USER', 'ASSISTED_ORIGINATION_CASE', 'INTAKE_DOCUMENT_VERSION'
    )),
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED',
        'CUSTOMER_PROFILE_COMPLETED', 'CUSTOMER_BANK_ACCOUNT_ADDED',
        'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY', 'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
        'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED',
        'COLLATERAL_LOAN_APPLICATION_SUBMITTED',
        'COLLATERAL_LOAN_VERIFICATION_STARTED', 'COLLATERAL_LOAN_VERIFICATION_COMPLETED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED',
        'DOCUMENT_REPLACEMENT_REQUESTED', 'DOCUMENT_UPLOADS_COMPLETED',
        'DOCUMENT_CHECKLIST_ITEM_CREATED', 'REVIEW_CYCLE_CREATED',
        'REVIEW_CYCLE_STATE_CHANGED', 'CORRECTION_REQUEST_CREATED',
        'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'LOAN_APPLICATION_CANCELLED', 'SALARY_ADVANCE_REVALIDATED',
        'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED', 'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED', 'LOAN_ACCOUNT_STATUS_CHANGED',
        'LOAN_SETTLEMENT_APPROVED', 'LOAN_ACCOUNT_CLOSED',
        'PARTNER_COMPANY_CREATED', 'PARTNER_COMPANY_UPDATED',
        'PARTNER_COMPANY_STATUS_CHANGED', 'PARTNER_EMPLOYEE_IMPORT_COMPLETED',
        'LOAN_PRODUCT_LIMITS_UPDATED', 'LOAN_PRODUCT_ACTIVATED',
        'LOAN_PRODUCT_DEACTIVATED', 'IDENTITY_USER_STATUS_CHANGED',
        'IDENTITY_USER_ROLE_ASSIGNED', 'IDENTITY_USER_ROLE_REMOVED',
        'STAFF_ASSISTED_CUSTOMER_CREATED', 'ASSISTED_ORIGINATION_CASE_CREATED',
        'ASSISTED_ORIGINATION_CUSTOMER_ASSOCIATED',
        'ASSISTED_ORIGINATION_CASE_ABANDONED', 'INTAKE_DOCUMENT_VERSION_UPLOADED'
    ));
