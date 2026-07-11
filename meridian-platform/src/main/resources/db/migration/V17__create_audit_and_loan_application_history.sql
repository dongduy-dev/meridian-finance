CREATE TABLE loan_application_status_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    sequence_number SMALLINT NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    reason TEXT,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_application_status_transitions_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_application_status_transitions_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_loan_application_status_transitions_sequence_positive
        CHECK (sequence_number > 0),
    CONSTRAINT chk_loan_application_status_transitions_from_status
        CHECK (from_status IS NULL OR from_status IN (
            'DRAFT', 'SUBMITTED', 'VERIFICATION_PENDING', 'VERIFICATION_FAILED',
            'DOCUMENTS_PENDING', 'UNDER_REVIEW', 'RETURNED_FOR_REVISION',
            'RETURNED_TO_REVIEW', 'APPROVAL_PENDING', 'APPROVED', 'REJECTED',
            'CUSTOMER_ACCEPTANCE_PENDING', 'CUSTOMER_DECLINED', 'CONTRACT_PENDING',
            'DISBURSEMENT_PENDING', 'DISBURSED', 'CANCELLED', 'EXPIRED'
        )),
    CONSTRAINT chk_loan_application_status_transitions_to_status
        CHECK (to_status IN (
            'DRAFT', 'SUBMITTED', 'VERIFICATION_PENDING', 'VERIFICATION_FAILED',
            'DOCUMENTS_PENDING', 'UNDER_REVIEW', 'RETURNED_FOR_REVISION',
            'RETURNED_TO_REVIEW', 'APPROVAL_PENDING', 'APPROVED', 'REJECTED',
            'CUSTOMER_ACCEPTANCE_PENDING', 'CUSTOMER_DECLINED', 'CONTRACT_PENDING',
            'DISBURSEMENT_PENDING', 'DISBURSED', 'CANCELLED', 'EXPIRED'
        )),
    CONSTRAINT chk_loan_application_status_transitions_action
        CHECK (action IN (
            'APPLICATION_SUBMITTED', 'REVIEW_STARTED', 'RECOMMEND_APPROVAL',
            'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION',
            'APPROVE', 'REJECT', 'RETURN_TO_LOAN_OFFICER_REVIEW',
            'REQUEST_CUSTOMER_OR_STAFF_CORRECTION', 'APPROVED_OFFER_GENERATED',
            'OFFER_ACCEPTED', 'OFFER_DECLINED', 'OFFER_EXPIRED'
        )),
    CONSTRAINT chk_loan_application_status_transitions_initial_submission
        CHECK (
            (from_status IS NULL AND action = 'APPLICATION_SUBMITTED' AND to_status = 'SUBMITTED')
            OR from_status IS NOT NULL
        ),
    CONSTRAINT chk_loan_application_status_transitions_status_change
        CHECK (from_status IS NULL OR from_status <> to_status),
    CONSTRAINT chk_loan_application_status_transitions_actor
        CHECK (
            (actor_type = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        ),
    CONSTRAINT uq_loan_application_status_transitions_operation_sequence
        UNIQUE (operation_id, sequence_number)
);

CREATE INDEX idx_loan_application_status_transitions_application_occurred
    ON loan_application_status_transitions (loan_application_id, occurred_at, id);
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
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_events_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_audit_events_sequence_positive
        CHECK (sequence_number > 0),
    CONSTRAINT chk_audit_events_actor
        CHECK (
            (actor_type = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
        ),
    CONSTRAINT chk_audit_events_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT uq_audit_events_operation_sequence
        UNIQUE (operation_id, sequence_number)
);

CREATE INDEX idx_audit_events_entity_occurred
    ON audit_events (entity_type, entity_id, occurred_at, id);
CREATE INDEX idx_audit_events_actor_occurred
    ON audit_events (actor_user_id, occurred_at);
CREATE INDEX idx_audit_events_action_occurred
    ON audit_events (action, occurred_at);

CREATE OR REPLACE FUNCTION reject_loan_application_status_transition_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'loan_application_status_transitions is append-only';
END;
$$;

CREATE TRIGGER trg_loan_application_status_transitions_append_only
BEFORE UPDATE OR DELETE ON loan_application_status_transitions
FOR EACH ROW EXECUTE FUNCTION reject_loan_application_status_transition_mutation();

CREATE OR REPLACE FUNCTION reject_audit_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;

CREATE TRIGGER trg_audit_events_append_only
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();
