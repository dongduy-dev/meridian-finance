ALTER TABLE unsecured_consumer_loan_verifications
    ADD COLUMN reviewed_by_user_id UUID,
    ADD COLUMN reviewed_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN assessment_note VARCHAR(2000),
    ADD CONSTRAINT fk_ucl_verifications_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    ADD CONSTRAINT chk_ucl_verifications_decision_evidence_consistency CHECK (
        (
            reviewed_by_user_id IS NULL
            AND reviewed_at IS NULL
            AND assessment_note IS NULL
        )
        OR (
            reviewed_by_user_id IS NOT NULL
            AND reviewed_at IS NOT NULL
            AND assessment_note IS NOT NULL
            AND btrim(assessment_note) <> ''
        )
    ),
    ADD CONSTRAINT chk_ucl_verifications_pending_evidence CHECK (
        product_verification_result <> 'PENDING_MANUAL_REVIEW'
        OR (
            reviewed_by_user_id IS NULL
            AND reviewed_at IS NULL
            AND assessment_note IS NULL
        )
    ),
    ADD CONSTRAINT chk_ucl_verifications_verified_evidence CHECK (
        product_verification_result <> 'VERIFIED'
        OR (
            reviewed_by_user_id IS NOT NULL
            AND reviewed_at IS NOT NULL
            AND assessment_note IS NOT NULL
            AND btrim(assessment_note) <> ''
        )
    ),
    ADD CONSTRAINT chk_ucl_verifications_review_chronology CHECK (
        reviewed_at IS NULL OR reviewed_at >= created_at
    );

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action,
    ADD CONSTRAINT chk_loan_application_status_transitions_action CHECK (action IN (
        'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS',
        'START_PRODUCT_VERIFICATION', 'COMPLETE_PRODUCT_VERIFICATION', 'START_REVIEW',
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
        'UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED',
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
