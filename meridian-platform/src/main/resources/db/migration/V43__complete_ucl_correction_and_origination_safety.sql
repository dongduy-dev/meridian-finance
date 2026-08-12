DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'unsecured_consumer_loan_verifications'
    ) THEN
        RAISE EXCEPTION 'V43 preflight failed: UCL verification evidence is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'unsecured_consumer_loan_verifications'
          AND column_name = 'verification_sequence'
    ) THEN
        RAISE EXCEPTION 'V43 preflight failed: UCL verification sequence already exists';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'loan_application_cancellations'
          AND column_name = 'reservation_release_movement_id'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'V43 preflight failed: V37 cancellation evidence is not present';
    END IF;
END;
$$;

ALTER TABLE loan_correction_requests
    DROP CONSTRAINT chk_loan_correction_requests_source_action,
    ADD CONSTRAINT chk_loan_correction_requests_source_action CHECK (
        source_action IN (
            'RETURN_TO_CUSTOMER_REVISION',
            'REQUEST_STAFF_CORRECTION',
            'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
            'REQUEST_REPLACEMENT',
            'COMPLETE_PRODUCT_VERIFICATION'
        )
    ),
    ADD CONSTRAINT uq_loan_correction_requests_id_application
        UNIQUE (id, loan_application_id);

ALTER TABLE loan_correction_tasks
    DROP CONSTRAINT chk_loan_correction_tasks_document_type,
    DROP CONSTRAINT chk_loan_correction_tasks_scope_fields,
    ADD CONSTRAINT chk_loan_correction_tasks_document_type CHECK (
        document_type IN (
            'RECENT_PAYSLIP',
            'INCOME_PROOF',
            'BANK_STATEMENT',
            'EMPLOYMENT_PROOF'
        )
    ),
    ADD CONSTRAINT chk_loan_correction_tasks_scope_fields CHECK (
        (
            scope = 'SUPPORTING_DOCUMENT_UPLOAD'
            AND responsible_party IN ('CUSTOMER', 'STAFF')
            AND create_checklist_item
            AND checklist_item_id IS NOT NULL
            AND baseline_document_version_id IS NULL
        )
        OR (
            scope = 'DOCUMENT_REPLACEMENT'
            AND responsible_party = 'CUSTOMER'
            AND NOT create_checklist_item
            AND checklist_item_id IS NOT NULL
            AND baseline_document_version_id IS NOT NULL
        )
        OR (
            scope = 'DOCUMENT_REVIEW'
            AND responsible_party = 'STAFF'
            AND NOT create_checklist_item
            AND checklist_item_id IS NOT NULL
            AND baseline_document_version_id IS NOT NULL
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
    ELSE
        RAISE EXCEPTION 'Correction execution is not supported for this Loan product';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_correction_tasks_product_document
BEFORE INSERT OR UPDATE ON loan_correction_tasks
FOR EACH ROW EXECUTE FUNCTION validate_loan_correction_task_product_document();

ALTER TABLE unsecured_consumer_loan_verifications
    ADD COLUMN verification_sequence INTEGER,
    ADD COLUMN source_correction_request_id UUID;

UPDATE unsecured_consumer_loan_verifications
SET verification_sequence = 1;

ALTER TABLE unsecured_consumer_loan_verifications
    ALTER COLUMN verification_sequence SET NOT NULL,
    DROP CONSTRAINT uq_ucl_verifications_application,
    DROP CONSTRAINT chk_ucl_verifications_decision_evidence_consistency,
    DROP CONSTRAINT chk_ucl_verifications_pending_evidence,
    DROP CONSTRAINT chk_ucl_verifications_verified_evidence,
    DROP CONSTRAINT chk_ucl_verifications_review_chronology,
    ADD CONSTRAINT uq_ucl_verifications_application_sequence
        UNIQUE (loan_application_id, verification_sequence),
    ADD CONSTRAINT uq_ucl_verifications_source_correction
        UNIQUE (source_correction_request_id),
    ADD CONSTRAINT fk_ucl_verifications_source_correction_application
        FOREIGN KEY (source_correction_request_id, loan_application_id)
        REFERENCES loan_correction_requests (id, loan_application_id),
    ADD CONSTRAINT chk_ucl_verifications_sequence
        CHECK (verification_sequence > 0),
    ADD CONSTRAINT chk_ucl_verifications_source_sequence CHECK (
        (verification_sequence = 1 AND source_correction_request_id IS NULL)
        OR (verification_sequence > 1 AND source_correction_request_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_ucl_verifications_evidence CHECK (
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
    ADD CONSTRAINT chk_ucl_verifications_review_chronology CHECK (
        reviewed_at IS NULL OR reviewed_at >= created_at
    );

CREATE OR REPLACE FUNCTION enforce_ucl_verification_cycle_immutability()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'UCL verification cycles cannot be deleted';
    END IF;

    IF OLD.id <> NEW.id
            OR OLD.loan_application_id <> NEW.loan_application_id
            OR OLD.verification_sequence <> NEW.verification_sequence
            OR OLD.source_correction_request_id IS DISTINCT FROM NEW.source_correction_request_id
            OR OLD.created_at <> NEW.created_at THEN
        RAISE EXCEPTION 'UCL verification cycle identity is immutable';
    END IF;

    IF OLD.product_verification_result <> 'PENDING_MANUAL_REVIEW'
            OR NEW.product_verification_result NOT IN (
                'VERIFIED', 'FAILED', 'REQUIRES_MORE_INFORMATION'
            )
            OR OLD.reviewed_by_user_id IS NOT NULL
            OR OLD.reviewed_at IS NOT NULL
            OR OLD.assessment_note IS NOT NULL THEN
        RAISE EXCEPTION 'A UCL verification cycle may complete exactly once';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ucl_verification_cycles_immutable
BEFORE UPDATE OR DELETE ON unsecured_consumer_loan_verifications
FOR EACH ROW EXECUTE FUNCTION enforce_ucl_verification_cycle_immutability();

CREATE OR REPLACE FUNCTION validate_ucl_verification_cycle_source()
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
    FROM unsecured_consumer_loan_verifications
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
            'UCL reverification requires a completed prior cycle and resubmitted source correction';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ucl_verification_cycles_reconcile_source
AFTER INSERT ON unsecured_consumer_loan_verifications
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_ucl_verification_cycle_source();

ALTER TABLE loan_application_cancellations
    ALTER COLUMN reservation_release_movement_id DROP NOT NULL;

CREATE OR REPLACE FUNCTION validate_loan_application_cancellation_evidence()
RETURNS trigger AS $$
DECLARE
    cancellation_row loan_application_cancellations%ROWTYPE;
    application_row loan_applications%ROWTYPE;
    correction_row loan_correction_requests%ROWTYPE;
    release_row salary_advance_limit_movements%ROWTYPE;
    reservation_count INTEGER;
    matching_reservation_count INTEGER;
    release_count INTEGER;
    transition_count INTEGER;
    correct_transition_count INTEGER;
    cancellation_audit_count INTEGER;
    correct_cancellation_audit_count INTEGER;
    release_audit_count INTEGER;
    correct_release_audit_count INTEGER;
BEGIN
    SELECT * INTO cancellation_row
    FROM loan_application_cancellations
    WHERE id = COALESCE(NEW.id, OLD.id);

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT * INTO application_row
    FROM loan_applications
    WHERE id = cancellation_row.loan_application_id;

    SELECT * INTO correction_row
    FROM loan_correction_requests
    WHERE id = cancellation_row.correction_request_id;

    IF application_row.id IS NULL
            OR application_row.product_code NOT IN (
                'SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN'
            )
            OR application_row.status <> 'CANCELLED'
            OR correction_row.id IS NULL
            OR correction_row.loan_application_id <> application_row.id
            OR correction_row.status <> 'CANCELLED'
            OR correction_row.cancelled_at <> cancellation_row.cancelled_at
            OR correction_row.resubmitted_at IS NOT NULL
            OR correction_row.resubmission_request_id IS NOT NULL
            OR NOT EXISTS (
                SELECT 1
                FROM users actor
                WHERE actor.id = cancellation_row.cancelled_by_user_id
                  AND actor.user_type = 'CUSTOMER'
                  AND actor.customer_id = application_row.customer_id
            ) THEN
        RAISE EXCEPTION
            'Loan Application cancellation conflicts with terminal correction evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE loan_application_id = application_row.id
          AND from_status = 'RETURNED_FOR_REVISION'
          AND to_status = 'CANCELLED'
          AND action = 'CANCEL_APPLICATION'
          AND reason = 'CUSTOMER_CANCELLATION'
          AND actor_type = 'USER'
          AND actor_user_id = cancellation_row.cancelled_by_user_id
          AND occurred_at = cancellation_row.cancelled_at
    )
    INTO transition_count, correct_transition_count
    FROM loan_application_status_transitions
    WHERE operation_id = cancellation_row.id;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_APPLICATION'
          AND entity_id = application_row.id
          AND actor_user_id = cancellation_row.cancelled_by_user_id
    )
    INTO cancellation_audit_count, correct_cancellation_audit_count
    FROM audit_events
    WHERE operation_id = cancellation_row.id
      AND action = 'LOAN_APPLICATION_CANCELLED';

    IF transition_count <> 1
            OR correct_transition_count <> 1
            OR cancellation_audit_count <> 1
            OR correct_cancellation_audit_count <> 1 THEN
        RAISE EXCEPTION
            'Loan Application cancellation requires exact lifecycle and audit evidence';
    END IF;

    SELECT COUNT(*) INTO reservation_count
    FROM salary_advance_limit_movements
    WHERE loan_application_id = application_row.id
      AND movement_type = 'RESERVED';

    SELECT COUNT(*) INTO release_count
    FROM salary_advance_limit_movements
    WHERE loan_application_id = application_row.id
      AND movement_type = 'RESERVATION_RELEASED';

    SELECT COUNT(*) INTO release_audit_count
    FROM audit_events
    WHERE operation_id = cancellation_row.id
      AND action = 'RESERVATION_RELEASED';

    IF application_row.product_code = 'UNSECURED_CONSUMER_LOAN' THEN
        IF cancellation_row.reservation_release_movement_id IS NOT NULL
                OR reservation_count <> 0
                OR release_count <> 0
                OR release_audit_count <> 0 THEN
            RAISE EXCEPTION 'UCL cancellation must have no Salary exposure effect';
        END IF;
        RETURN NULL;
    END IF;

    SELECT * INTO release_row
    FROM salary_advance_limit_movements
    WHERE id = cancellation_row.reservation_release_movement_id;

    IF cancellation_row.reservation_release_movement_id IS NULL
            OR release_row.id IS NULL
            OR release_row.loan_application_id <> application_row.id
            OR release_row.movement_type <> 'RESERVATION_RELEASED'
            OR release_row.amount <> application_row.requested_amount
            OR release_row.loan_account_id IS NOT NULL
            OR release_row.repayment_transaction_id IS NOT NULL
            OR release_row.occurred_at <> cancellation_row.cancelled_at THEN
        RAISE EXCEPTION
            'Salary Advance cancellation requires an exact reservation release';
    END IF;

    SELECT COUNT(*) FILTER (
        WHERE salary_advance_limit_id = release_row.salary_advance_limit_id
          AND amount = application_row.requested_amount
          AND loan_account_id IS NULL
          AND repayment_transaction_id IS NULL
    )
    INTO matching_reservation_count
    FROM salary_advance_limit_movements
    WHERE loan_application_id = application_row.id
      AND movement_type = 'RESERVED';

    SELECT COUNT(*) FILTER (
        WHERE entity_type = 'SALARY_ADVANCE_LIMIT_MOVEMENT'
          AND entity_id = release_row.id
          AND actor_user_id = cancellation_row.cancelled_by_user_id
    )
    INTO correct_release_audit_count
    FROM audit_events
    WHERE operation_id = cancellation_row.id
      AND action = 'RESERVATION_RELEASED';

    IF reservation_count <> 1
            OR matching_reservation_count <> 1
            OR release_count <> 1
            OR release_audit_count <> 1
            OR correct_release_audit_count <> 1 THEN
        RAISE EXCEPTION
            'Salary Advance cancellation requires one exact reservation, release, and audit';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
