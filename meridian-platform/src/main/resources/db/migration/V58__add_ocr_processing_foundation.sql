CREATE TABLE ocr_jobs (
    id UUID PRIMARY KEY,
    intake_document_version_id UUID NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    source_storage_key VARCHAR(255) NOT NULL,
    source_mime_type VARCHAR(100) NOT NULL,
    source_sha256_hex VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    lease_owner VARCHAR(120),
    lease_expires_at TIMESTAMP WITHOUT TIME ZONE,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    failure_category VARCHAR(50),
    trace_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    failed_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT fk_ocr_jobs_intake_document_version
        FOREIGN KEY (intake_document_version_id) REFERENCES intake_document_versions (id),
    CONSTRAINT uq_ocr_jobs_intake_document_version
        UNIQUE (intake_document_version_id),
    CONSTRAINT chk_ocr_jobs_evidence_type
        CHECK (evidence_type IN (
            'CUSTOMER_IDENTITY', 'UCL_PAPER_APPLICATION', 'COLLATERAL_PAPER_APPLICATION'
        )),
    CONSTRAINT chk_ocr_jobs_source_mime
        CHECK (source_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_ocr_jobs_source_sha256
        CHECK (source_sha256_hex ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_ocr_jobs_state
        CHECK (state IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_ocr_jobs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_ocr_jobs_failure_category CHECK (
        failure_category IS NULL OR failure_category IN (
            'SOURCE_NOT_FOUND', 'SOURCE_INTEGRITY_MISMATCH', 'UNSUPPORTED_SOURCE',
            'PROVIDER_UNAVAILABLE', 'PROVIDER_REJECTED', 'PROVIDER_ERROR',
            'CONFIGURATION_ERROR'
        )
    ),
    CONSTRAINT chk_ocr_jobs_lease CHECK (
        (state = 'PROCESSING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (state <> 'PROCESSING' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT chk_ocr_jobs_completion CHECK (
        (state = 'COMPLETED' AND completed_at IS NOT NULL AND failed_at IS NULL)
        OR (state = 'FAILED' AND completed_at IS NULL AND failed_at IS NOT NULL
            AND failure_category IS NOT NULL)
        OR (state IN ('PENDING', 'PROCESSING') AND completed_at IS NULL AND failed_at IS NULL)
    )
);

CREATE INDEX idx_ocr_jobs_pending_claim
    ON ocr_jobs (next_attempt_at, created_at, id)
    WHERE state = 'PENDING';

CREATE INDEX idx_ocr_jobs_expired_lease
    ON ocr_jobs (lease_expires_at, created_at, id)
    WHERE state = 'PROCESSING';

CREATE TABLE ocr_results (
    id UUID PRIMARY KEY,
    ocr_job_id UUID NOT NULL,
    provider VARCHAR(80) NOT NULL,
    processor_name VARCHAR(255) NOT NULL,
    processor_version VARCHAR(120),
    encrypted_extracted_text TEXT NOT NULL,
    encrypted_normalized_payload TEXT NOT NULL,
    encrypted_structured_suggestions TEXT NOT NULL,
    normalized_confidence NUMERIC(5, 4),
    disposition VARCHAR(30) NOT NULL,
    processing_duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_ocr_results_job FOREIGN KEY (ocr_job_id) REFERENCES ocr_jobs (id),
    CONSTRAINT uq_ocr_results_job UNIQUE (ocr_job_id),
    CONSTRAINT chk_ocr_results_disposition
        CHECK (disposition IN ('HIGH_CONFIDENCE', 'PENDING_REVIEW', 'REVIEWED')),
    CONSTRAINT chk_ocr_results_confidence
        CHECK (normalized_confidence IS NULL
            OR (normalized_confidence >= 0 AND normalized_confidence <= 1)),
    CONSTRAINT chk_ocr_results_duration CHECK (processing_duration_ms >= 0),
    CONSTRAINT chk_ocr_results_text_envelope
        CHECK (encrypted_extracted_text LIKE 'v1:gcm:%'),
    CONSTRAINT chk_ocr_results_payload_envelope
        CHECK (encrypted_normalized_payload LIKE 'v1:gcm:%'),
    CONSTRAINT chk_ocr_results_suggestions_envelope
        CHECK (encrypted_structured_suggestions LIKE 'v1:gcm:%')
);

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
        'OCR_JOB'
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
        'INTAKE_DOCUMENT_VERSION_UPLOADED', 'OCR_JOB_CREATED'
    ));
