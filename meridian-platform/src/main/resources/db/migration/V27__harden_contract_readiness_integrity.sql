DO $$
DECLARE
    duplicate_application_id UUID;
BEGIN
    SELECT loan_application_id
    INTO duplicate_application_id
    FROM salary_advance_limit_movements
    WHERE movement_type = 'RESERVED'
      AND loan_application_id IS NOT NULL
    GROUP BY loan_application_id
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V27 cannot enforce one RESERVED movement per Loan Application because application % has duplicate reservation rows',
            duplicate_application_id
            USING ERRCODE = '23514';
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_salary_advance_limit_movements_application_reserved
    ON salary_advance_limit_movements (loan_application_id)
    WHERE movement_type = 'RESERVED'
      AND loan_application_id IS NOT NULL;

ALTER TABLE loan_contracts
    DROP CONSTRAINT chk_loan_contracts_lifecycle,
    ADD CONSTRAINT chk_loan_contracts_lifecycle CHECK (
        (status = 'PREPARED'
            AND acknowledgment_request_id IS NULL
            AND acknowledged_by_user_id IS NULL
            AND acknowledged_at IS NULL
            AND confirmation_request_id IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL
            AND superseded_by_user_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'ACKNOWLEDGED'
            AND acknowledgment_request_id IS NOT NULL
            AND acknowledged_by_user_id IS NOT NULL
            AND acknowledged_at IS NOT NULL
            AND acknowledged_at >= prepared_at
            AND confirmation_request_id IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL
            AND superseded_by_user_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'READY_FOR_DISBURSEMENT'
            AND acknowledgment_request_id IS NOT NULL
            AND acknowledged_by_user_id IS NOT NULL
            AND acknowledged_at IS NOT NULL
            AND acknowledged_at >= prepared_at
            AND confirmation_request_id IS NOT NULL
            AND confirmed_by_user_id IS NOT NULL
            AND confirmed_at IS NOT NULL
            AND confirmed_at >= acknowledged_at
            AND superseded_by_user_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'SUPERSEDED'
            AND (
                (acknowledgment_request_id IS NULL
                    AND acknowledged_by_user_id IS NULL
                    AND acknowledged_at IS NULL)
                OR
                (acknowledgment_request_id IS NOT NULL
                    AND acknowledged_by_user_id IS NOT NULL
                    AND acknowledged_at IS NOT NULL
                    AND acknowledged_at >= prepared_at)
            )
            AND confirmation_request_id IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL
            AND superseded_by_user_id IS NOT NULL
            AND superseded_at IS NOT NULL
            AND superseded_at >= prepared_at
            AND (acknowledged_at IS NULL OR superseded_at >= acknowledged_at))
    );
