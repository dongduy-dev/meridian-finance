CREATE TABLE review_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL,
    loan_officer_user_id UUID NOT NULL,
    recommendation VARCHAR(50) NOT NULL,
    reason TEXT,
    internal_notes TEXT,
    submitted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_recommendations_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_review_recommendations_loan_officer
        FOREIGN KEY (loan_officer_user_id)
        REFERENCES users (id),
    CONSTRAINT chk_review_recommendations_recommendation
        CHECK (recommendation IN (
            'RECOMMEND_APPROVAL',
            'RECOMMEND_REJECTION',
            'RETURN_TO_CUSTOMER_REVISION',
            'REQUEST_STAFF_CORRECTION'
        )),
    CONSTRAINT chk_review_recommendations_reason_required
        CHECK (
            recommendation = 'RECOMMEND_APPROVAL'
            OR (reason IS NOT NULL AND btrim(reason) <> '')
        )
);

CREATE INDEX idx_review_recommendations_application_id
    ON review_recommendations (loan_application_id);

CREATE INDEX idx_review_recommendations_loan_officer_id
    ON review_recommendations (loan_officer_user_id);

CREATE INDEX idx_review_recommendations_submitted_at
    ON review_recommendations (submitted_at);
