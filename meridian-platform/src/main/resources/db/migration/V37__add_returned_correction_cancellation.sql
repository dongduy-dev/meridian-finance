DO $$
BEGIN
    IF to_regclass(current_schema() || '.loan_application_cancellations') IS NOT NULL THEN
        RAISE EXCEPTION
            'V37 preflight failed: Loan Application cancellation evidence table already exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'loan_correction_requests'
          AND column_name = 'cancelled_at'
    ) THEN
        RAISE EXCEPTION
            'V37 preflight failed: correction cancellation timestamp already exists';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM role_permissions role_permission
        JOIN roles role ON role.id = role_permission.role_id
        JOIN permissions permission ON permission.id = role_permission.permission_id
        WHERE role.code = 'CUSTOMER'
          AND permission.code = 'loan:cancel:own'
    ) THEN
        RAISE EXCEPTION
            'V37 preflight failed: Customer cancellation permission is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_proc procedure_row
        JOIN pg_namespace namespace ON namespace.oid = procedure_row.pronamespace
        WHERE namespace.nspname = current_schema()
          AND procedure_row.proname = 'reject_immutable_history_row_mutation'
    ) THEN
        RAISE EXCEPTION
            'V37 preflight failed: immutable-evidence trigger function is missing';
    END IF;
END;
$$;

ALTER TABLE loan_correction_requests
    ADD COLUMN cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    DROP CONSTRAINT chk_loan_correction_requests_status,
    DROP CONSTRAINT chk_loan_correction_requests_timestamps,
    ADD CONSTRAINT chk_loan_correction_requests_status CHECK (
        status IN ('OPEN', 'READY_FOR_RESUBMISSION', 'RESUBMITTED', 'CANCELLED')
    ),
    ADD CONSTRAINT chk_loan_correction_requests_timestamps CHECK (
        (status = 'OPEN'
            AND ready_at IS NULL
            AND resubmitted_at IS NULL
            AND cancelled_at IS NULL
            AND resubmission_request_id IS NULL)
        OR (status = 'READY_FOR_RESUBMISSION'
            AND ready_at IS NOT NULL
            AND resubmitted_at IS NULL
            AND cancelled_at IS NULL
            AND resubmission_request_id IS NULL)
        OR (status = 'RESUBMITTED'
            AND ready_at IS NOT NULL
            AND resubmitted_at IS NOT NULL
            AND cancelled_at IS NULL
            AND resubmission_request_id IS NOT NULL)
        OR (status = 'CANCELLED'
            AND resubmitted_at IS NULL
            AND cancelled_at IS NOT NULL
            AND resubmission_request_id IS NULL)
    );

CREATE TABLE loan_application_cancellations (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    correction_request_id UUID NOT NULL,
    reservation_release_movement_id UUID NOT NULL,
    request_id UUID NOT NULL,
    cancelled_by_user_id UUID NOT NULL,
    cancelled_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_application_cancellations_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_application_cancellations_correction
        FOREIGN KEY (correction_request_id) REFERENCES loan_correction_requests (id),
    CONSTRAINT fk_loan_application_cancellations_release
        FOREIGN KEY (reservation_release_movement_id)
        REFERENCES salary_advance_limit_movements (id),
    CONSTRAINT fk_loan_application_cancellations_actor
        FOREIGN KEY (cancelled_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_application_cancellations_application
        UNIQUE (loan_application_id),
    CONSTRAINT uq_loan_application_cancellations_correction
        UNIQUE (correction_request_id),
    CONSTRAINT uq_loan_application_cancellations_release
        UNIQUE (reservation_release_movement_id),
    CONSTRAINT uq_loan_application_cancellations_request
        UNIQUE (request_id)
);

CREATE TRIGGER trg_loan_application_cancellations_immutable
BEFORE UPDATE OR DELETE ON loan_application_cancellations
FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action,
    ADD CONSTRAINT chk_loan_application_status_transitions_action CHECK (action IN (
        'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
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

    SELECT * INTO release_row
    FROM salary_advance_limit_movements
    WHERE id = cancellation_row.reservation_release_movement_id;

    IF application_row.id IS NULL
            OR application_row.product_code <> 'SALARY_ADVANCE'
            OR application_row.status <> 'CANCELLED'
            OR correction_row.id IS NULL
            OR correction_row.loan_application_id <> application_row.id
            OR correction_row.status <> 'CANCELLED'
            OR correction_row.cancelled_at <> cancellation_row.cancelled_at
            OR correction_row.resubmitted_at IS NOT NULL
            OR correction_row.resubmission_request_id IS NOT NULL
            OR release_row.id IS NULL
            OR release_row.loan_application_id <> application_row.id
            OR release_row.movement_type <> 'RESERVATION_RELEASED'
            OR release_row.amount <> application_row.requested_amount
            OR release_row.loan_account_id IS NOT NULL
            OR release_row.repayment_transaction_id IS NOT NULL
            OR release_row.occurred_at <> cancellation_row.cancelled_at
            OR NOT EXISTS (
                SELECT 1
                FROM users actor
                WHERE actor.id = cancellation_row.cancelled_by_user_id
                  AND actor.user_type = 'CUSTOMER'
                  AND actor.customer_id = application_row.customer_id
            ) THEN
        RAISE EXCEPTION
            'Loan Application cancellation conflicts with terminal correction or release evidence';
    END IF;

    SELECT
        COUNT(*),
        COUNT(*) FILTER (
            WHERE salary_advance_limit_id = release_row.salary_advance_limit_id
              AND amount = application_row.requested_amount
              AND loan_account_id IS NULL
              AND repayment_transaction_id IS NULL
        )
    INTO reservation_count, matching_reservation_count
    FROM salary_advance_limit_movements
    WHERE loan_application_id = application_row.id
      AND movement_type = 'RESERVED';

    SELECT COUNT(*) INTO release_count
    FROM salary_advance_limit_movements
    WHERE loan_application_id = application_row.id
      AND movement_type = 'RESERVATION_RELEASED';

    IF reservation_count <> 1
            OR matching_reservation_count <> 1
            OR release_count <> 1 THEN
        RAISE EXCEPTION
            'Loan Application cancellation requires one exact reservation and release';
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

    IF transition_count <> 1 OR correct_transition_count <> 1 THEN
        RAISE EXCEPTION
            'Loan Application cancellation requires exact lifecycle history';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_APPLICATION'
          AND entity_id = application_row.id
          AND actor_user_id = cancellation_row.cancelled_by_user_id
    )
    INTO cancellation_audit_count, correct_cancellation_audit_count
    FROM audit_events
    WHERE operation_id = cancellation_row.id
      AND action = 'LOAN_APPLICATION_CANCELLED';

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'SALARY_ADVANCE_LIMIT_MOVEMENT'
          AND entity_id = release_row.id
          AND actor_user_id = cancellation_row.cancelled_by_user_id
    )
    INTO release_audit_count, correct_release_audit_count
    FROM audit_events
    WHERE operation_id = cancellation_row.id
      AND action = 'RESERVATION_RELEASED';

    IF cancellation_audit_count <> 1
            OR correct_cancellation_audit_count <> 1
            OR release_audit_count <> 1
            OR correct_release_audit_count <> 1 THEN
        RAISE EXCEPTION
            'Loan Application cancellation requires exact audit evidence';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_application_cancellations_reconcile
AFTER INSERT ON loan_application_cancellations
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_loan_application_cancellation_evidence();
