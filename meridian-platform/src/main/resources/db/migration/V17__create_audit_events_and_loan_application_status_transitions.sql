CREATE TABLE loan_application_status_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    sequence_number SMALLINT NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    action VARCHAR(80) NOT NULL,
    reason TEXT,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    occurred_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_application_status_transitions_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_application_status_transitions_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_loan_application_status_transitions_application_sequence
        UNIQUE (loan_application_id, sequence_number),
    CONSTRAINT chk_loan_application_status_transitions_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_loan_application_status_transitions_from_status
        CHECK (
            from_status IS NULL
            OR from_status IN (
                'DRAFT',
                'SUBMITTED',
                'VERIFICATION_PENDING',
                'VERIFICATION_FAILED',
                'DOCUMENTS_PENDING',
                'UNDER_REVIEW',
                'RETURNED_FOR_REVISION',
                'RETURNED_TO_REVIEW',
                'APPROVAL_PENDING',
                'APPROVED',
                'REJECTED',
                'CUSTOMER_ACCEPTANCE_PENDING',
                'CUSTOMER_DECLINED',
                'CONTRACT_PENDING',
                'DISBURSEMENT_PENDING',
                'DISBURSED',
                'CANCELLED',
                'EXPIRED'
            )
        ),
    CONSTRAINT chk_loan_application_status_transitions_to_status
        CHECK (
            to_status IN (
                'DRAFT',
                'SUBMITTED',
                'VERIFICATION_PENDING',
                'VERIFICATION_FAILED',
                'DOCUMENTS_PENDING',
                'UNDER_REVIEW',
                'RETURNED_FOR_REVISION',
                'RETURNED_TO_REVIEW',
                'APPROVAL_PENDING',
                'APPROVED',
                'REJECTED',
                'CUSTOMER_ACCEPTANCE_PENDING',
                'CUSTOMER_DECLINED',
                'CONTRACT_PENDING',
                'DISBURSEMENT_PENDING',
                'DISBURSED',
                'CANCELLED',
                'EXPIRED'
            )
        ),
    CONSTRAINT chk_loan_application_status_transitions_action
        CHECK (
            action IN (
                'SUBMIT_APPLICATION',
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
    CONSTRAINT chk_loan_application_status_transitions_initial
        CHECK (
            (
                from_status IS NULL
                AND action = 'SUBMIT_APPLICATION'
                AND to_status = 'SUBMITTED'
                AND sequence_number = 1
            )
            OR from_status IS NOT NULL
        ),
    CONSTRAINT chk_loan_application_status_transitions_status_change
        CHECK (from_status IS NULL OR from_status <> to_status),
    CONSTRAINT chk_loan_application_status_transitions_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_loan_application_status_transitions_actor
        CHECK (
            (actor_type = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        )
);

CREATE INDEX idx_loan_application_status_transitions_operation
    ON loan_application_status_transitions (operation_id);

CREATE INDEX idx_loan_application_status_transitions_actor_occurred
    ON loan_application_status_transitions (actor_user_id, occurred_at);

CREATE INDEX idx_loan_application_status_transitions_action_occurred
    ON loan_application_status_transitions (action, occurred_at);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL,
    sequence_number SMALLINT NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_events_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_audit_events_operation_sequence
        UNIQUE (operation_id, sequence_number),
    CONSTRAINT chk_audit_events_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_audit_events_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_audit_events_actor
        CHECK (
            (actor_type = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        ),
    CONSTRAINT chk_audit_events_entity_type
        CHECK (
            entity_type IN (
                'LOAN_APPLICATION',
                'SALARY_ADVANCE_LIMIT_MOVEMENT',
                'REVIEW_RECOMMENDATION',
                'APPROVAL_DECISION',
                'APPROVED_OFFER'
            )
        ),
    CONSTRAINT chk_audit_events_action
        CHECK (
            action IN (
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
                'RESERVATION_RELEASED'
            )
        ),
    CONSTRAINT chk_audit_events_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_audit_events_payload_size
        CHECK (octet_length(payload::text) <= 2048)
);

CREATE INDEX idx_audit_events_entity_occurred
    ON audit_events (entity_type, entity_id, occurred_at);

CREATE INDEX idx_audit_events_actor_occurred
    ON audit_events (actor_user_id, occurred_at);

CREATE INDEX idx_audit_events_action_occurred
    ON audit_events (action, occurred_at);

CREATE OR REPLACE FUNCTION reject_immutable_history_row_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Immutable history rows cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_application_status_transitions_immutable
    BEFORE UPDATE OR DELETE ON loan_application_status_transitions
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_audit_events_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_history_row_mutation();
