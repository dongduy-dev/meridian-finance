CREATE TABLE assisted_action_documents (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    evidence_type VARCHAR(60) NOT NULL,
    approved_offer_id UUID,
    declared_offer_decision VARCHAR(20),
    loan_contract_id UUID,
    contract_version INTEGER,
    current_version_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT uq_assisted_action_documents_offer UNIQUE (approved_offer_id),
    CONSTRAINT uq_assisted_action_documents_contract_version
        UNIQUE (loan_contract_id, contract_version),
    CONSTRAINT uq_assisted_action_documents_id_application
        UNIQUE (id, loan_application_id),
    CONSTRAINT chk_assisted_action_documents_target CHECK (
        (evidence_type = 'CUSTOMER_OFFER_RESPONSE'
            AND approved_offer_id IS NOT NULL
            AND declared_offer_decision IN ('ACCEPT', 'DECLINE')
            AND loan_contract_id IS NULL
            AND contract_version IS NULL)
        OR
        (evidence_type = 'CUSTOMER_CONTRACT_ACKNOWLEDGMENT'
            AND approved_offer_id IS NULL
            AND declared_offer_decision IS NULL
            AND loan_contract_id IS NOT NULL
            AND contract_version > 0)
    )
);

CREATE INDEX idx_assisted_action_documents_application
    ON assisted_action_documents (loan_application_id, evidence_type);

CREATE TABLE assisted_action_document_versions (
    id UUID PRIMARY KEY,
    assisted_action_document_id UUID NOT NULL,
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
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_assisted_action_versions_document
        FOREIGN KEY (assisted_action_document_id) REFERENCES assisted_action_documents (id),
    CONSTRAINT fk_assisted_action_versions_baseline
        FOREIGN KEY (baseline_version_id) REFERENCES assisted_action_document_versions (id),
    CONSTRAINT fk_assisted_action_versions_uploader
        FOREIGN KEY (uploader_staff_user_id) REFERENCES users (id),
    CONSTRAINT uq_assisted_action_versions_document_sequence
        UNIQUE (assisted_action_document_id, version_number),
    CONSTRAINT uq_assisted_action_versions_upload_request UNIQUE (upload_request_id),
    CONSTRAINT uq_assisted_action_versions_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_assisted_action_versions_id_document
        UNIQUE (id, assisted_action_document_id),
    CONSTRAINT chk_assisted_action_versions_sequence CHECK (version_number > 0),
    CONSTRAINT chk_assisted_action_versions_byte_size
        CHECK (byte_size > 0 AND byte_size <= 10485760),
    CONSTRAINT chk_assisted_action_versions_declared_mime
        CHECK (declared_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_assisted_action_versions_detected_mime
        CHECK (detected_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_assisted_action_versions_mime_match
        CHECK (declared_mime_type = detected_mime_type),
    CONSTRAINT chk_assisted_action_versions_sha256
        CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_assisted_action_versions_filename_safe
        CHECK (btrim(original_filename) <> ''
            AND original_filename !~ '[\\/\x00-\x1F\x7F]'
            AND original_filename NOT LIKE '%..%')
);

ALTER TABLE assisted_action_documents
    ADD CONSTRAINT fk_assisted_action_documents_current_version
        FOREIGN KEY (current_version_id, id)
        REFERENCES assisted_action_document_versions (id, assisted_action_document_id);

CREATE INDEX idx_assisted_action_versions_document_uploaded
    ON assisted_action_document_versions (assisted_action_document_id, uploaded_at DESC);

CREATE TRIGGER trg_assisted_action_document_versions_immutable
    BEFORE UPDATE OR DELETE ON assisted_action_document_versions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TABLE staff_assisted_offer_responses (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    loan_application_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    approved_offer_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    evidence_document_version_id UUID NOT NULL,
    recorded_by_staff_user_id UUID NOT NULL,
    recorded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_staff_assisted_offer_response_application_customer
        FOREIGN KEY (loan_application_id, customer_id)
        REFERENCES loan_applications (id, customer_id),
    CONSTRAINT fk_staff_assisted_offer_response_offer_application
        FOREIGN KEY (approved_offer_id, loan_application_id)
        REFERENCES approved_offers (id, loan_application_id),
    CONSTRAINT fk_staff_assisted_offer_response_actor
        FOREIGN KEY (recorded_by_staff_user_id) REFERENCES users (id),
    CONSTRAINT uq_staff_assisted_offer_response_request UNIQUE (request_id),
    CONSTRAINT uq_staff_assisted_offer_response_offer UNIQUE (approved_offer_id),
    CONSTRAINT chk_staff_assisted_offer_response_action CHECK (action IN ('ACCEPT', 'DECLINE'))
);

CREATE TRIGGER trg_staff_assisted_offer_responses_immutable
    BEFORE UPDATE OR DELETE ON staff_assisted_offer_responses
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TABLE staff_assisted_contract_acknowledgments (
    id UUID PRIMARY KEY,
    acknowledgment_request_id UUID NOT NULL,
    loan_application_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    loan_contract_id UUID NOT NULL,
    contract_version INTEGER NOT NULL,
    evidence_document_version_id UUID NOT NULL,
    recorded_by_staff_user_id UUID NOT NULL,
    recorded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_staff_assisted_contract_ack_application_customer
        FOREIGN KEY (loan_application_id, customer_id)
        REFERENCES loan_applications (id, customer_id),
    CONSTRAINT fk_staff_assisted_contract_ack_contract_application_version
        FOREIGN KEY (loan_contract_id, loan_application_id, contract_version)
        REFERENCES loan_contracts (id, loan_application_id, contract_version),
    CONSTRAINT fk_staff_assisted_contract_ack_actor
        FOREIGN KEY (recorded_by_staff_user_id) REFERENCES users (id),
    CONSTRAINT uq_staff_assisted_contract_ack_request UNIQUE (acknowledgment_request_id),
    CONSTRAINT uq_staff_assisted_contract_ack_version UNIQUE (loan_contract_id, contract_version),
    CONSTRAINT chk_staff_assisted_contract_ack_version CHECK (contract_version > 0)
);

CREATE TRIGGER trg_staff_assisted_contract_acknowledgments_immutable
    BEFORE UPDATE OR DELETE ON staff_assisted_contract_acknowledgments
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000250', 'loan:offer:respond:staff',
     'Record an evidenced Staff-assisted Customer approved-offer response'),
    ('00000000-0000-0000-0000-000000000251', 'loan:contract:acknowledge:staff',
     'Record an evidenced Staff-assisted Customer contract acknowledgment'),
    ('00000000-0000-0000-0000-000000000252', 'document:upload:assisted-action',
     'Upload signed Staff-assisted downstream Customer-action evidence');

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN (
    'loan:offer:respond:staff', 'document:upload:assisted-action'
)
WHERE role.code = 'LOAN_OFFICER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN (
    'loan:contract:acknowledge:staff', 'document:upload:assisted-action'
)
WHERE role.code = 'ACCOUNTING_OFFICER';

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
        'IDENTITY_USER', 'ASSISTED_ORIGINATION_CASE', 'INTAKE_DOCUMENT_VERSION',
        'ASSISTED_ACTION_DOCUMENT_VERSION', 'OCR_JOB', 'OCR_REVIEW'
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
        'ASSISTED_ORIGINATION_CASE_ABANDONED',
        'ASSISTED_ORIGINATION_CASE_COMPLETED',
        'INTAKE_DOCUMENT_VERSION_UPLOADED',
        'ASSISTED_ACTION_DOCUMENT_VERSION_UPLOADED',
        'OCR_JOB_CREATED', 'OCR_RESULT_REVIEWED'
    ));
