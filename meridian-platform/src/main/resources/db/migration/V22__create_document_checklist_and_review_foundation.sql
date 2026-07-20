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
