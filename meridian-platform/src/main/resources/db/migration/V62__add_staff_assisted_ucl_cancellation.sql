INSERT INTO permissions (id, code, description)
VALUES (
    '00000000-0000-0000-0000-000000000254',
    'loan:cancel:staff',
    'Record an evidenced Customer-requested cancellation for an eligible Staff-assisted UCL application'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'loan:cancel:staff'
WHERE role.code = 'LOAN_OFFICER';

ALTER TABLE assisted_action_documents
    ADD COLUMN correction_request_id UUID,
    DROP CONSTRAINT chk_assisted_action_documents_target,
    ADD CONSTRAINT uq_assisted_action_documents_correction
        UNIQUE (correction_request_id),
    ADD CONSTRAINT chk_assisted_action_documents_target CHECK (
        (evidence_type = 'CUSTOMER_OFFER_RESPONSE'
            AND approved_offer_id IS NOT NULL
            AND declared_offer_decision IN ('ACCEPT', 'DECLINE')
            AND loan_contract_id IS NULL
            AND contract_version IS NULL
            AND correction_request_id IS NULL)
        OR
        (evidence_type = 'CUSTOMER_CONTRACT_ACKNOWLEDGMENT'
            AND approved_offer_id IS NULL
            AND declared_offer_decision IS NULL
            AND loan_contract_id IS NOT NULL
            AND contract_version > 0
            AND correction_request_id IS NULL)
        OR
        (evidence_type = 'CUSTOMER_CANCELLATION_REQUEST'
            AND approved_offer_id IS NULL
            AND declared_offer_decision IS NULL
            AND loan_contract_id IS NULL
            AND contract_version IS NULL
            AND correction_request_id IS NOT NULL)
    );

ALTER TABLE loan_application_cancellations
    ADD COLUMN assisted_evidence_document_version_id UUID;

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
            OR NOT (
                (application_row.origination_channel = 'CUSTOMER_DIGITAL'
                    AND cancellation_row.assisted_evidence_document_version_id IS NULL
                    AND EXISTS (
                        SELECT 1
                        FROM users actor
                        WHERE actor.id = cancellation_row.cancelled_by_user_id
                          AND actor.user_type = 'CUSTOMER'
                          AND actor.customer_id = application_row.customer_id
                    ))
                OR
                (application_row.origination_channel = 'STAFF_ASSISTED'
                    AND application_row.product_code = 'UNSECURED_CONSUMER_LOAN'
                    AND cancellation_row.assisted_evidence_document_version_id IS NOT NULL
                    AND EXISTS (
                        SELECT 1
                        FROM users actor
                        WHERE actor.id = cancellation_row.cancelled_by_user_id
                          AND actor.user_type = 'STAFF'
                          AND actor.customer_id IS NULL
                    ))
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
