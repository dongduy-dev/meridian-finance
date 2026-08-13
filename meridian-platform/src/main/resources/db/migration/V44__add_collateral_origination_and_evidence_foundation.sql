DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name IN ('collaterals', 'collateral_loan_verifications')
    ) THEN
        RAISE EXCEPTION 'V44 preflight failed: Collateral Loan CP1 tables already exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        JOIN pg_class relation ON relation.oid = constraint_row.conrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = current_schema()
          AND relation.relname = 'document_checklist_items'
          AND constraint_row.conname = 'chk_document_checklist_items_type'
          AND constraint_row.contype = 'c'
    ) THEN
        RAISE EXCEPTION 'V44 preflight failed: document type constraint is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        JOIN pg_class relation ON relation.oid = constraint_row.conrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = current_schema()
          AND relation.relname = 'audit_events'
          AND constraint_row.conname = 'chk_audit_events_action'
          AND constraint_row.contype = 'c'
    ) THEN
        RAISE EXCEPTION 'V44 preflight failed: audit action constraint is missing';
    END IF;
END;
$$;

CREATE TABLE collaterals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    collateral_type VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    estimated_value NUMERIC(19, 2) NOT NULL,
    ownership_status VARCHAR(200) NOT NULL,
    condition_note VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_collaterals_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT chk_collaterals_type CHECK (collateral_type IN (
        'MOTORBIKE', 'CAR', 'ELECTRONICS', 'PROPERTY_DOCUMENT', 'OTHER'
    )),
    CONSTRAINT chk_collaterals_description CHECK (
        description = btrim(description) AND description <> ''
    ),
    CONSTRAINT chk_collaterals_estimated_value CHECK (
        estimated_value > 0 AND estimated_value = trunc(estimated_value)
    ),
    CONSTRAINT chk_collaterals_ownership_status CHECK (
        ownership_status = btrim(ownership_status) AND ownership_status <> ''
    ),
    CONSTRAINT chk_collaterals_condition_note CHECK (
        condition_note = btrim(condition_note) AND condition_note <> ''
    )
);

CREATE INDEX idx_collaterals_loan_application_id
    ON collaterals (loan_application_id);

CREATE TABLE collateral_loan_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    product_verification_result VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_collateral_loan_verifications_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT uq_collateral_loan_verifications_application
        UNIQUE (loan_application_id),
    CONSTRAINT chk_collateral_loan_verifications_result
        CHECK (product_verification_result = 'PENDING_MANUAL_REVIEW')
);

ALTER TABLE document_checklist_items
    DROP CONSTRAINT chk_document_checklist_items_type,
    ADD CONSTRAINT chk_document_checklist_items_type CHECK (document_type IN (
        'RECENT_PAYSLIP', 'INCOME_PROOF', 'BANK_STATEMENT', 'EMPLOYMENT_PROOF',
        'COLLATERAL_OWNERSHIP_EVIDENCE'
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
        'COLLATERAL_LOAN_APPLICATION_SUBMITTED',
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
