DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM loan_applications
        WHERE requested_amount <> trunc(requested_amount)
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce whole-VND loan application amounts because existing requested_amount values contain non-zero fractional VND';
    END IF;
END $$;

ALTER TABLE loan_applications
    ADD CONSTRAINT chk_loan_applications_requested_amount_whole_vnd
        CHECK (requested_amount = trunc(requested_amount));
