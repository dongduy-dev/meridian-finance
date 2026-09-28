ALTER TABLE loan_application_review_cycles
    ADD COLUMN assigned_loan_officer_user_id UUID;

UPDATE loan_application_review_cycles cycle
SET assigned_loan_officer_user_id = recommendation.loan_officer_user_id
FROM review_recommendations recommendation
WHERE recommendation.review_cycle_id = cycle.id;

WITH proven_initial_starts AS (
    SELECT cycle.id AS review_cycle_id,
           (array_agg(DISTINCT transition.actor_user_id))[1] AS loan_officer_user_id
    FROM loan_application_review_cycles cycle
    JOIN loan_application_status_transitions transition
      ON transition.loan_application_id = cycle.loan_application_id
     AND transition.action = 'START_REVIEW'
     AND transition.actor_type = 'USER'
     AND transition.actor_user_id IS NOT NULL
     AND transition.occurred_at = cycle.started_at
    WHERE cycle.cycle_number = 1
      AND cycle.assigned_loan_officer_user_id IS NULL
    GROUP BY cycle.id
    HAVING COUNT(DISTINCT transition.actor_user_id) = 1
)
UPDATE loan_application_review_cycles cycle
SET assigned_loan_officer_user_id = proven.loan_officer_user_id
FROM proven_initial_starts proven
WHERE cycle.id = proven.review_cycle_id;

DO $$
DECLARE
    propagated_count INTEGER;
BEGIN
    LOOP
        UPDATE loan_application_review_cycles cycle
        SET assigned_loan_officer_user_id = preceding.assigned_loan_officer_user_id
        FROM loan_application_review_cycles preceding
        WHERE cycle.assigned_loan_officer_user_id IS NULL
          AND preceding.loan_application_id = cycle.loan_application_id
          AND preceding.cycle_number = cycle.cycle_number - 1
          AND preceding.assigned_loan_officer_user_id IS NOT NULL;

        GET DIAGNOSTICS propagated_count = ROW_COUNT;
        EXIT WHEN propagated_count = 0;
    END LOOP;
END
$$;

ALTER TABLE loan_application_review_cycles
    ADD CONSTRAINT fk_loan_review_cycles_assigned_loan_officer
        FOREIGN KEY (assigned_loan_officer_user_id) REFERENCES users (id);

CREATE INDEX idx_loan_review_cycles_assigned_officer
    ON loan_application_review_cycles (
        assigned_loan_officer_user_id,
        loan_application_id,
        cycle_number DESC
    )
    WHERE assigned_loan_officer_user_id IS NOT NULL;
