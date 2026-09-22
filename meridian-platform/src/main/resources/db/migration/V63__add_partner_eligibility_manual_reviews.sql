CREATE TABLE partner_eligibility_reviews (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    partner_company_id UUID NOT NULL,
    effective_month VARCHAR(7) NOT NULL,
    source_import_batch_id UUID,
    trigger_outcome VARCHAR(50) NOT NULL,
    requested_employee_code VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    decision_outcome VARCHAR(50),
    decision_reason VARCHAR(60),
    selected_partner_employee_id UUID,
    selected_import_batch_id UUID,
    reviewer_user_id UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_partner_eligibility_reviews_partner_company
        FOREIGN KEY (partner_company_id)
        REFERENCES partner_companies (id),
    CONSTRAINT fk_partner_eligibility_reviews_source_batch_company
        FOREIGN KEY (source_import_batch_id, partner_company_id)
        REFERENCES partner_employee_import_batches (id, partner_company_id),
    CONSTRAINT fk_partner_eligibility_reviews_selected_employee_company
        FOREIGN KEY (selected_partner_employee_id, partner_company_id)
        REFERENCES partner_employees (id, partner_company_id),
    CONSTRAINT fk_partner_eligibility_reviews_selected_batch_company
        FOREIGN KEY (selected_import_batch_id, partner_company_id)
        REFERENCES partner_employee_import_batches (id, partner_company_id),
    CONSTRAINT chk_partner_eligibility_reviews_effective_month CHECK (
        effective_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'
    ),
    CONSTRAINT chk_partner_eligibility_reviews_trigger CHECK (
        trigger_outcome IN ('NOT_FOUND', 'MULTIPLE_MATCHES', 'PENDING_MANUAL_REVIEW')
    ),
    CONSTRAINT chk_partner_eligibility_reviews_requested_code CHECK (
        btrim(requested_employee_code) <> ''
    ),
    CONSTRAINT chk_partner_eligibility_reviews_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED')
    ),
    CONSTRAINT chk_partner_eligibility_reviews_reason CHECK (
        decision_reason IS NULL OR decision_reason IN (
            'CURRENT_EMPLOYEE_CONFIRMED',
            'NO_ELIGIBLE_CURRENT_EMPLOYEE',
            'IDENTITY_EVIDENCE_MISMATCH',
            'INSUFFICIENT_SOURCE_EVIDENCE'
        )
    ),
    CONSTRAINT chk_partner_eligibility_reviews_terminal_evidence CHECK (
        (status IN ('PENDING', 'SUPERSEDED')
            AND decision_outcome IS NULL
            AND decision_reason IS NULL
            AND selected_partner_employee_id IS NULL
            AND selected_import_batch_id IS NULL
            AND reviewer_user_id IS NULL
            AND reviewed_at IS NULL)
        OR
        (status = 'APPROVED'
            AND decision_outcome = 'MANUAL_REVIEW_APPROVED'
            AND decision_reason = 'CURRENT_EMPLOYEE_CONFIRMED'
            AND selected_partner_employee_id IS NOT NULL
            AND selected_import_batch_id IS NOT NULL
            AND reviewer_user_id IS NOT NULL
            AND reviewed_at IS NOT NULL)
        OR
        (status = 'REJECTED'
            AND decision_outcome = 'MANUAL_REVIEW_REJECTED'
            AND decision_reason IN (
                'NO_ELIGIBLE_CURRENT_EMPLOYEE',
                'IDENTITY_EVIDENCE_MISMATCH',
                'INSUFFICIENT_SOURCE_EVIDENCE'
            )
            AND selected_partner_employee_id IS NULL
            AND selected_import_batch_id IS NULL
            AND reviewer_user_id IS NOT NULL
            AND reviewed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_partner_eligibility_reviews_pending_customer_company
    ON partner_eligibility_reviews (customer_id, partner_company_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_partner_eligibility_reviews_queue
    ON partner_eligibility_reviews (status, created_at, id);

CREATE INDEX idx_partner_eligibility_reviews_company_month
    ON partner_eligibility_reviews (partner_company_id, effective_month);

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
        'PARTNER_COMPANY', 'PARTNER_EMPLOYEE_IMPORT_BATCH', 'PARTNER_ELIGIBILITY_REVIEW',
        'LOAN_PRODUCT', 'IDENTITY_USER', 'ASSISTED_ORIGINATION_CASE',
        'INTAKE_DOCUMENT_VERSION', 'ASSISTED_ACTION_DOCUMENT_VERSION', 'OCR_JOB', 'OCR_REVIEW'
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
        'PARTNER_ELIGIBILITY_REVIEW_APPROVED', 'PARTNER_ELIGIBILITY_REVIEW_REJECTED',
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
