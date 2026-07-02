CREATE TABLE approval_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    review_recommendation_id UUID NOT NULL,
    approver_user_id UUID NOT NULL,
    decision VARCHAR(50) NOT NULL,
    reason TEXT,
    internal_notes TEXT,
    decided_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_approval_decisions_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_approval_decisions_recommendation
        FOREIGN KEY (review_recommendation_id)
        REFERENCES review_recommendations (id),
    CONSTRAINT fk_approval_decisions_approver
        FOREIGN KEY (approver_user_id)
        REFERENCES users (id),
    CONSTRAINT chk_approval_decisions_decision
        CHECK (decision IN (
            'APPROVE',
            'REJECT',
            'RETURN_TO_LOAN_OFFICER_REVIEW',
            'REQUEST_CUSTOMER_OR_STAFF_CORRECTION'
        )),
    CONSTRAINT chk_approval_decisions_reason_required
        CHECK (
            decision = 'APPROVE'
            OR (reason IS NOT NULL AND btrim(reason) <> '')
        )
);

CREATE INDEX idx_approval_decisions_application_id
    ON approval_decisions (loan_application_id);

CREATE INDEX idx_approval_decisions_recommendation_id
    ON approval_decisions (review_recommendation_id);

CREATE INDEX idx_approval_decisions_approver_id
    ON approval_decisions (approver_user_id);

CREATE INDEX idx_approval_decisions_decided_at
    ON approval_decisions (decided_at);
