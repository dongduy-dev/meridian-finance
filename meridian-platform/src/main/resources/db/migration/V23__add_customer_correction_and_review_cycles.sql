DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM document_review_decisions
        WHERE outcome = 'REQUEST_REPLACEMENT'
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill historical REQUEST_REPLACEMENT instructions deterministically';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM approval_decisions decision
        JOIN review_recommendations recommendation
          ON recommendation.id = decision.review_recommendation_id
        WHERE decision.loan_application_id <> recommendation.loan_application_id
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill review cycles: approval decision application does not match recommendation';
    END IF;

    IF EXISTS (
        SELECT review_recommendation_id
        FROM approval_decisions
        GROUP BY review_recommendation_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill review cycles: recommendation has multiple approval decisions';
    END IF;

    IF EXISTS (
        SELECT loan_application_id
        FROM review_recommendations
        GROUP BY loan_application_id
        HAVING COUNT(*) FILTER (
            WHERE NOT EXISTS (
                SELECT 1 FROM approval_decisions decision
                WHERE decision.review_recommendation_id = review_recommendations.id
            )
        ) > 1
    ) THEN
        RAISE EXCEPTION 'V23 cannot backfill review cycles: application has multiple unresolved recommendations';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_applications
        WHERE status = 'RETURNED_FOR_REVISION'
    ) OR EXISTS (
        SELECT 1
        FROM review_recommendations
        WHERE recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
    ) OR EXISTS (
        SELECT 1
        FROM approval_decisions
        WHERE decision = 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
    ) THEN
        RAISE EXCEPTION
            'V23 cannot backfill legacy revision actions without correction tasks and audience-specific instructions';
    END IF;
END
$$;

CREATE TABLE loan_application_review_cycles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    cycle_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_review_cycles_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT uq_loan_review_cycles_application_number
        UNIQUE (loan_application_id, cycle_number),
    CONSTRAINT uq_loan_review_cycles_id_application
        UNIQUE (id, loan_application_id),
    CONSTRAINT chk_loan_review_cycles_number
        CHECK (cycle_number > 0),
    CONSTRAINT chk_loan_review_cycles_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'SUPERSEDED', 'CORRECTION_REQUIRED', 'CORRECTED')),
    CONSTRAINT chk_loan_review_cycles_end_state
        CHECK (
            (status = 'ACTIVE' AND ended_at IS NULL)
            OR (status <> 'ACTIVE' AND ended_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_loan_review_cycles_active_application
    ON loan_application_review_cycles (loan_application_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_loan_review_cycles_application_status
    ON loan_application_review_cycles (loan_application_id, status, cycle_number DESC);

WITH ordered_recommendations AS (
    SELECT recommendation.id,
           recommendation.loan_application_id,
           recommendation.submitted_at,
           ROW_NUMBER() OVER (
               PARTITION BY recommendation.loan_application_id
               ORDER BY recommendation.submitted_at, recommendation.id
           )::INTEGER AS cycle_number,
           recommendation.recommendation,
           decision.decision,
           decision.decided_at
    FROM review_recommendations recommendation
    LEFT JOIN approval_decisions decision
      ON decision.review_recommendation_id = recommendation.id
)
INSERT INTO loan_application_review_cycles (
    id, loan_application_id, cycle_number, status, started_at, ended_at, created_at, updated_at
)
SELECT gen_random_uuid(),
       ordered.loan_application_id,
       ordered.cycle_number,
       CASE
           WHEN ordered.recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
               OR ordered.decision = 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
               THEN 'CORRECTION_REQUIRED'
           WHEN ordered.decision IN ('APPROVE', 'REJECT') THEN 'COMPLETED'
           WHEN ordered.decision = 'RETURN_TO_LOAN_OFFICER_REVIEW' THEN 'SUPERSEDED'
           ELSE 'ACTIVE'
       END,
       ordered.submitted_at,
       CASE
           WHEN ordered.recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
               THEN ordered.submitted_at
           WHEN ordered.decision IS NOT NULL THEN ordered.decided_at
           ELSE NULL
       END,
       ordered.submitted_at,
       COALESCE(ordered.decided_at, ordered.submitted_at)
FROM ordered_recommendations ordered;

INSERT INTO loan_application_review_cycles (
    id, loan_application_id, cycle_number, status, started_at, ended_at, created_at, updated_at
)
SELECT gen_random_uuid(),
       application.id,
       1,
       'ACTIVE',
       COALESCE(
           (
               SELECT transition.occurred_at
               FROM loan_application_status_transitions transition
               WHERE transition.loan_application_id = application.id
                 AND transition.to_status = 'UNDER_REVIEW'
               ORDER BY transition.sequence_number DESC
               LIMIT 1
           ),
           application.submitted_at
       ),
       NULL,
       application.submitted_at,
       application.updated_at
FROM loan_applications application
WHERE application.status = 'UNDER_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM loan_application_review_cycles cycle
      WHERE cycle.loan_application_id = application.id
  );

INSERT INTO loan_application_review_cycles (
    id, loan_application_id, cycle_number, status, started_at, ended_at, created_at, updated_at
)
SELECT gen_random_uuid(),
       application.id,
       COALESCE(MAX(existing.cycle_number), 0) + 1,
       'ACTIVE',
       COALESCE(
           (
               SELECT decision.decided_at
               FROM approval_decisions decision
               WHERE decision.loan_application_id = application.id
                 AND decision.decision = 'RETURN_TO_LOAN_OFFICER_REVIEW'
               ORDER BY decision.decided_at DESC, decision.id DESC
               LIMIT 1
           ),
           application.updated_at
       ),
       NULL,
       application.updated_at,
       application.updated_at
FROM loan_applications application
LEFT JOIN loan_application_review_cycles existing
  ON existing.loan_application_id = application.id
WHERE application.status = 'RETURNED_TO_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM loan_application_review_cycles active_cycle
      WHERE active_cycle.loan_application_id = application.id
        AND active_cycle.status = 'ACTIVE'
  )
GROUP BY application.id, application.updated_at;

ALTER TABLE review_recommendations
    ADD COLUMN review_cycle_id UUID,
    ADD COLUMN reason_code VARCHAR(80);

WITH ordered_recommendations AS (
    SELECT recommendation.id,
           recommendation.loan_application_id,
           ROW_NUMBER() OVER (
               PARTITION BY recommendation.loan_application_id
               ORDER BY recommendation.submitted_at, recommendation.id
           )::INTEGER AS cycle_number
    FROM review_recommendations recommendation
)
UPDATE review_recommendations recommendation
SET review_cycle_id = cycle.id
FROM ordered_recommendations ordered
JOIN loan_application_review_cycles cycle
  ON cycle.loan_application_id = ordered.loan_application_id
 AND cycle.cycle_number = ordered.cycle_number
WHERE recommendation.id = ordered.id;

ALTER TABLE review_recommendations
    ALTER COLUMN review_cycle_id SET NOT NULL,
    ADD CONSTRAINT fk_review_recommendations_cycle_application
        FOREIGN KEY (review_cycle_id, loan_application_id)
        REFERENCES loan_application_review_cycles (id, loan_application_id),
    ADD CONSTRAINT uq_review_recommendations_cycle UNIQUE (review_cycle_id),
    DROP CONSTRAINT chk_review_recommendations_reason_required,
    ADD CONSTRAINT chk_review_recommendations_reason_contract
        CHECK (
            (
                recommendation IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
                AND reason IS NULL
                AND reason_code IS NOT NULL
            )
            OR (
                recommendation NOT IN ('RETURN_TO_CUSTOMER_REVISION', 'REQUEST_STAFF_CORRECTION')
                AND reason_code IS NULL
                AND (
                    recommendation = 'RECOMMEND_APPROVAL'
                    OR (reason IS NOT NULL AND btrim(reason) <> '')
                )
            )
        );

ALTER TABLE approval_decisions
    ADD COLUMN reason_code VARCHAR(80),
    DROP CONSTRAINT chk_approval_decisions_reason_required,
    ADD CONSTRAINT uq_approval_decisions_recommendation UNIQUE (review_recommendation_id),
    ADD CONSTRAINT chk_approval_decisions_reason_contract
        CHECK (
            (
                decision = 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
                AND reason IS NULL
                AND reason_code IS NOT NULL
            )
            OR (
                decision <> 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
                AND reason_code IS NULL
                AND (decision = 'APPROVE' OR (reason IS NOT NULL AND btrim(reason) <> ''))
            )
        );

CREATE TABLE loan_correction_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    source_review_cycle_id UUID,
    source_action VARCHAR(60) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    created_by_user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    resubmission_request_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ready_at TIMESTAMP WITHOUT TIME ZONE,
    resubmitted_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_loan_correction_requests_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_correction_requests_cycle_application
        FOREIGN KEY (source_review_cycle_id, loan_application_id)
        REFERENCES loan_application_review_cycles (id, loan_application_id),
    CONSTRAINT fk_loan_correction_requests_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_correction_requests_resubmission
        UNIQUE (resubmission_request_id),
    CONSTRAINT chk_loan_correction_requests_source_action
        CHECK (source_action IN (
            'RETURN_TO_CUSTOMER_REVISION',
            'REQUEST_STAFF_CORRECTION',
            'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
            'REQUEST_REPLACEMENT'
        )),
    CONSTRAINT chk_loan_correction_requests_reason
        CHECK (reason_code IN (
            'SUPPORTING_DOCUMENT_REQUIRED',
            'RECENT_PAYSLIP_REQUIRED',
            'DOCUMENT_REPLACEMENT_REQUIRED',
            'DOCUMENT_REVIEW_REQUIRED'
        )),
    CONSTRAINT chk_loan_correction_requests_status
        CHECK (status IN ('OPEN', 'READY_FOR_RESUBMISSION', 'RESUBMITTED')),
    CONSTRAINT chk_loan_correction_requests_timestamps
        CHECK (
            (status = 'OPEN' AND ready_at IS NULL AND resubmitted_at IS NULL AND resubmission_request_id IS NULL)
            OR (status = 'READY_FOR_RESUBMISSION' AND ready_at IS NOT NULL AND resubmitted_at IS NULL AND resubmission_request_id IS NULL)
            OR (status = 'RESUBMITTED' AND ready_at IS NOT NULL AND resubmitted_at IS NOT NULL AND resubmission_request_id IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_loan_correction_requests_active_application
    ON loan_correction_requests (loan_application_id)
    WHERE status IN ('OPEN', 'READY_FOR_RESUBMISSION');

CREATE INDEX idx_loan_correction_requests_application_status
    ON loan_correction_requests (loan_application_id, status, created_at DESC);

CREATE TABLE loan_correction_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correction_request_id UUID NOT NULL,
    task_sequence INTEGER NOT NULL,
    responsible_party VARCHAR(20) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    document_type VARCHAR(50),
    create_checklist_item BOOLEAN NOT NULL,
    checklist_item_id UUID,
    baseline_document_version_id UUID,
    customer_instruction VARCHAR(500),
    staff_instruction VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    completed_by_user_id UUID,
    completion_request_id UUID,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_loan_correction_tasks_request
        FOREIGN KEY (correction_request_id) REFERENCES loan_correction_requests (id),
    CONSTRAINT fk_loan_correction_tasks_checklist_item
        FOREIGN KEY (checklist_item_id) REFERENCES document_checklist_items (id),
    CONSTRAINT fk_loan_correction_tasks_baseline_version
        FOREIGN KEY (baseline_document_version_id) REFERENCES document_versions (id),
    CONSTRAINT fk_loan_correction_tasks_completed_by
        FOREIGN KEY (completed_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_correction_tasks_request_sequence
        UNIQUE (correction_request_id, task_sequence),
    CONSTRAINT uq_loan_correction_tasks_completion_request
        UNIQUE (completion_request_id),
    CONSTRAINT uq_loan_correction_tasks_tuple
        UNIQUE NULLS NOT DISTINCT (
            correction_request_id,
            responsible_party,
            scope,
            document_type,
            checklist_item_id,
            baseline_document_version_id
        ),
    CONSTRAINT chk_loan_correction_tasks_sequence CHECK (task_sequence > 0),
    CONSTRAINT chk_loan_correction_tasks_responsibility CHECK (responsible_party IN ('CUSTOMER', 'STAFF')),
    CONSTRAINT chk_loan_correction_tasks_scope CHECK (scope IN (
        'SUPPORTING_DOCUMENT_UPLOAD', 'DOCUMENT_REPLACEMENT', 'DOCUMENT_REVIEW'
    )),
    CONSTRAINT chk_loan_correction_tasks_document_type
        CHECK (document_type IS NULL OR document_type = 'RECENT_PAYSLIP'),
    CONSTRAINT chk_loan_correction_tasks_instruction
        CHECK (
            (responsible_party = 'CUSTOMER' AND customer_instruction IS NOT NULL AND btrim(customer_instruction) <> '' AND staff_instruction IS NULL)
            OR (responsible_party = 'STAFF' AND staff_instruction IS NOT NULL AND btrim(staff_instruction) <> '' AND customer_instruction IS NULL)
        ),
    CONSTRAINT chk_loan_correction_tasks_scope_fields
        CHECK (
            (
                scope = 'SUPPORTING_DOCUMENT_UPLOAD'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party = 'CUSTOMER'
                AND create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NULL
            )
            OR (
                scope = 'DOCUMENT_REPLACEMENT'
                AND document_type = 'RECENT_PAYSLIP'
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
        ),
    CONSTRAINT chk_loan_correction_tasks_status CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT chk_loan_correction_tasks_completion
        CHECK (
            (status = 'OPEN' AND completed_by_user_id IS NULL AND completion_request_id IS NULL AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND completed_by_user_id IS NOT NULL AND completion_request_id IS NOT NULL AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_loan_correction_tasks_customer_queue
    ON loan_correction_tasks (responsible_party, status, created_at, id);

CREATE INDEX idx_loan_correction_tasks_request_order
    ON loan_correction_tasks (correction_request_id, task_sequence, id);

CREATE INDEX idx_document_review_queue_current
    ON document_checklist_items (requirement_status, current_review_decision_id, updated_at, id)
    WHERE requirement_status = 'REQUIRED';

ALTER TABLE salary_advance_verifications
    DROP CONSTRAINT uq_salary_advance_verifications_application,
    ADD COLUMN verification_sequence INTEGER,
    ADD COLUMN correction_request_id UUID,
    ADD CONSTRAINT fk_salary_advance_verifications_correction_request
        FOREIGN KEY (correction_request_id) REFERENCES loan_correction_requests (id);

UPDATE salary_advance_verifications SET verification_sequence = 1;

ALTER TABLE salary_advance_verifications
    ALTER COLUMN verification_sequence SET NOT NULL,
    ADD CONSTRAINT chk_salary_advance_verifications_sequence CHECK (verification_sequence > 0),
    ADD CONSTRAINT uq_salary_advance_verifications_application_sequence
        UNIQUE (loan_application_id, verification_sequence),
    ADD CONSTRAINT uq_salary_advance_verifications_correction_request
        UNIQUE (correction_request_id);

INSERT INTO permissions (id, code, description)
VALUES ('00000000-0000-0000-0000-000000000235', 'loan:correction:own', 'Complete and resubmit own customer corrections'),
       ('00000000-0000-0000-0000-000000000236', 'document:upload:own', 'Upload own correction documents')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('loan:correction:own', 'document:upload:own')
WHERE role.code = 'CUSTOMER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action;

ALTER TABLE loan_application_status_transitions
    ADD CONSTRAINT chk_loan_application_status_transitions_action
        CHECK (action IN (
            'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
            'RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION',
            'REQUEST_STAFF_CORRECTION', 'APPROVE', 'REJECT',
            'RETURN_TO_LOAN_OFFICER_REVIEW', 'REQUEST_CUSTOMER_OR_STAFF_CORRECTION',
            'RESUBMIT_CORRECTION', 'GENERATE_APPROVED_OFFER', 'ACCEPT_APPROVED_OFFER',
            'DECLINE_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER'
        ));

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_entity_type,
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type
        CHECK (entity_type IN (
            'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
            'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION', 'APPROVAL_DECISION',
            'APPROVED_OFFER', 'DOCUMENT_CHECKLIST', 'DOCUMENT_CHECKLIST_ITEM',
            'DOCUMENT_VERSION', 'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
            'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK', 'SALARY_ADVANCE_VERIFICATION'
        )),
    ADD CONSTRAINT chk_audit_events_action
        CHECK (action IN (
            'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
            'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
            'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
            'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
            'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
            'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
            'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
            'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
            'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED',
            'DOCUMENT_VERSION_UPLOADED', 'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED',
            'DOCUMENT_REPLACEMENT_REQUESTED', 'DOCUMENT_UPLOADS_COMPLETED',
            'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
            'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED',
            'CORRECTION_RESUBMITTED', 'SALARY_ADVANCE_REVALIDATED'
        ));

ALTER TABLE document_review_decisions
    ADD COLUMN correction_reason_code VARCHAR(80),
    ADD COLUMN customer_instruction VARCHAR(500);

ALTER TABLE document_review_decisions
    ADD CONSTRAINT chk_document_review_decisions_correction_contract CHECK (
        (
            outcome = 'REQUEST_REPLACEMENT'
            AND correction_reason_code = 'DOCUMENT_REPLACEMENT_REQUIRED'
            AND customer_instruction IS NOT NULL
            AND char_length(btrim(customer_instruction)) BETWEEN 1 AND 500
        )
        OR (
            outcome <> 'REQUEST_REPLACEMENT'
            AND correction_reason_code IS NULL
            AND customer_instruction IS NULL
        )
    );
