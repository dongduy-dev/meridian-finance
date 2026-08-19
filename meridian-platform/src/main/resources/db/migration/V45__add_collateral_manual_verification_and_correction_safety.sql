DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'collateral_loan_verifications'
    ) THEN
        RAISE EXCEPTION 'V45 preflight failed: Collateral Loan verification evidence is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'collateral_loan_verifications'
          AND column_name = 'verification_sequence'
    ) THEN
        RAISE EXCEPTION 'V45 preflight failed: Collateral Loan verification sequence already exists';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        JOIN pg_class relation ON relation.oid = constraint_row.conrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = current_schema()
          AND relation.relname = 'loan_correction_tasks'
          AND constraint_row.conname = 'chk_loan_correction_tasks_document_type'
          AND constraint_row.contype = 'c'
    ) THEN
        RAISE EXCEPTION 'V45 preflight failed: correction task document constraint is missing';
    END IF;
END;
$$;

ALTER TABLE loan_correction_tasks
    DROP CONSTRAINT chk_loan_correction_tasks_document_type,
    ADD CONSTRAINT chk_loan_correction_tasks_document_type CHECK (
        document_type IN (
            'RECENT_PAYSLIP',
            'INCOME_PROOF',
            'BANK_STATEMENT',
            'EMPLOYMENT_PROOF',
            'COLLATERAL_OWNERSHIP_EVIDENCE'
        )
    );

CREATE OR REPLACE FUNCTION validate_loan_correction_task_product_document()
RETURNS trigger AS $$
DECLARE
    application_product VARCHAR(50);
    checklist_application_id UUID;
    checklist_document_type VARCHAR(50);
    current_version_id UUID;
BEGIN
    SELECT application.product_code
    INTO application_product
    FROM loan_correction_requests correction
    JOIN loan_applications application
      ON application.id = correction.loan_application_id
    WHERE correction.id = NEW.correction_request_id;

    SELECT
        checklist.loan_application_id,
        item.document_type,
        document.current_version_id
    INTO
        checklist_application_id,
        checklist_document_type,
        current_version_id
    FROM document_checklist_items item
    JOIN document_checklists checklist ON checklist.id = item.checklist_id
    LEFT JOIN documents document ON document.checklist_item_id = item.id
    WHERE item.id = NEW.checklist_item_id;

    IF application_product IS NULL
            OR checklist_application_id IS NULL
            OR checklist_application_id <> (
                SELECT loan_application_id
                FROM loan_correction_requests
                WHERE id = NEW.correction_request_id
            )
            OR checklist_document_type <> NEW.document_type THEN
        RAISE EXCEPTION
            'Correction task document evidence does not belong to the Loan Application';
    END IF;

    IF TG_OP = 'INSERT'
            AND NEW.baseline_document_version_id IS NOT NULL
            AND (
                current_version_id IS NULL
                OR current_version_id <> NEW.baseline_document_version_id
            ) THEN
        RAISE EXCEPTION 'Correction task baseline is not the current document version';
    END IF;

    IF application_product = 'SALARY_ADVANCE' THEN
        IF NEW.document_type <> 'RECENT_PAYSLIP' THEN
            RAISE EXCEPTION 'Salary Advance correction requires RECENT_PAYSLIP evidence';
        END IF;
    ELSIF application_product = 'UNSECURED_CONSUMER_LOAN' THEN
        IF NEW.document_type NOT IN ('INCOME_PROOF', 'BANK_STATEMENT', 'EMPLOYMENT_PROOF')
                OR NEW.scope = 'SUPPORTING_DOCUMENT_UPLOAD'
                OR NEW.create_checklist_item THEN
            RAISE EXCEPTION
                'UCL correction requires replacement or review of existing base evidence';
        END IF;
    ELSIF application_product = 'COLLATERAL_LOAN' THEN
        IF NEW.document_type <> 'COLLATERAL_OWNERSHIP_EVIDENCE'
                OR NEW.scope NOT IN ('DOCUMENT_REPLACEMENT', 'DOCUMENT_REVIEW')
                OR NEW.create_checklist_item THEN
            RAISE EXCEPTION
                'Collateral Loan correction requires replacement or review of ownership evidence';
        END IF;
    ELSE
        RAISE EXCEPTION 'Correction execution is not supported for this Loan product';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE collateral_loan_verifications
    ADD COLUMN verification_sequence INTEGER,
    ADD COLUMN source_correction_request_id UUID,
    ADD COLUMN reviewed_by_user_id UUID,
    ADD COLUMN reviewed_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN assessment_note VARCHAR(2000);

UPDATE collateral_loan_verifications
SET verification_sequence = 1;

ALTER TABLE collateral_loan_verifications
    ALTER COLUMN verification_sequence SET NOT NULL,
    DROP CONSTRAINT uq_collateral_loan_verifications_application,
    DROP CONSTRAINT chk_collateral_loan_verifications_result,
    ADD CONSTRAINT uq_collateral_verifications_application_sequence
        UNIQUE (loan_application_id, verification_sequence),
    ADD CONSTRAINT uq_collateral_verifications_source_correction
        UNIQUE (source_correction_request_id),
    ADD CONSTRAINT fk_collateral_verifications_source_correction_application
        FOREIGN KEY (source_correction_request_id, loan_application_id)
        REFERENCES loan_correction_requests (id, loan_application_id),
    ADD CONSTRAINT fk_collateral_verifications_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    ADD CONSTRAINT chk_collateral_verifications_sequence
        CHECK (verification_sequence > 0),
    ADD CONSTRAINT chk_collateral_verifications_source_sequence CHECK (
        (verification_sequence = 1 AND source_correction_request_id IS NULL)
        OR (verification_sequence > 1 AND source_correction_request_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_collateral_verifications_result CHECK (
        product_verification_result IN (
            'PENDING_MANUAL_REVIEW', 'VERIFIED', 'FAILED', 'REQUIRES_MORE_INFORMATION'
        )
    ),
    ADD CONSTRAINT chk_collateral_verifications_evidence CHECK (
        (
            product_verification_result = 'PENDING_MANUAL_REVIEW'
            AND reviewed_by_user_id IS NULL
            AND reviewed_at IS NULL
            AND assessment_note IS NULL
        )
        OR (
            product_verification_result IN (
                'VERIFIED', 'FAILED', 'REQUIRES_MORE_INFORMATION'
            )
            AND reviewed_by_user_id IS NOT NULL
            AND reviewed_at IS NOT NULL
            AND assessment_note IS NOT NULL
            AND btrim(assessment_note) <> ''
        )
    ),
    ADD CONSTRAINT chk_collateral_verifications_review_chronology CHECK (
        reviewed_at IS NULL OR reviewed_at >= created_at
    );

CREATE OR REPLACE FUNCTION enforce_collateral_verification_cycle_immutability()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Collateral Loan verification cycles cannot be deleted';
    END IF;

    IF OLD.id <> NEW.id
            OR OLD.loan_application_id <> NEW.loan_application_id
            OR OLD.verification_sequence <> NEW.verification_sequence
            OR OLD.source_correction_request_id IS DISTINCT FROM NEW.source_correction_request_id
            OR OLD.created_at <> NEW.created_at THEN
        RAISE EXCEPTION 'Collateral Loan verification cycle identity is immutable';
    END IF;

    IF OLD.product_verification_result <> 'PENDING_MANUAL_REVIEW'
            OR NEW.product_verification_result NOT IN (
                'VERIFIED', 'FAILED', 'REQUIRES_MORE_INFORMATION'
            )
            OR OLD.reviewed_by_user_id IS NOT NULL
            OR OLD.reviewed_at IS NOT NULL
            OR OLD.assessment_note IS NOT NULL THEN
        RAISE EXCEPTION 'A Collateral Loan verification cycle may complete exactly once';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_collateral_verification_cycles_immutable
BEFORE UPDATE OR DELETE ON collateral_loan_verifications
FOR EACH ROW EXECUTE FUNCTION enforce_collateral_verification_cycle_immutability();

CREATE OR REPLACE FUNCTION validate_collateral_verification_cycle_source()
RETURNS trigger AS $$
DECLARE
    source_correction loan_correction_requests%ROWTYPE;
    previous_result VARCHAR(50);
BEGIN
    IF NEW.verification_sequence = 1 THEN
        RETURN NULL;
    END IF;

    SELECT * INTO source_correction
    FROM loan_correction_requests
    WHERE id = NEW.source_correction_request_id;

    SELECT product_verification_result INTO previous_result
    FROM collateral_loan_verifications
    WHERE loan_application_id = NEW.loan_application_id
      AND verification_sequence = NEW.verification_sequence - 1;

    IF source_correction.id IS NULL
            OR source_correction.loan_application_id <> NEW.loan_application_id
            OR source_correction.status <> 'RESUBMITTED'
            OR source_correction.resubmitted_at IS NULL
            OR source_correction.resubmitted_at > NEW.created_at
            OR previous_result IS NULL
            OR previous_result = 'PENDING_MANUAL_REVIEW' THEN
        RAISE EXCEPTION
            'Collateral Loan reverification requires a completed prior cycle and resubmitted source correction';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_collateral_verification_cycles_reconcile_source
AFTER INSERT ON collateral_loan_verifications
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_collateral_verification_cycle_source();

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
        'COLLATERAL_LOAN_VERIFICATION_STARTED',
        'COLLATERAL_LOAN_VERIFICATION_COMPLETED',
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
