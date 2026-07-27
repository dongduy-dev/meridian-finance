DO $$
DECLARE
    incompatible_application_id UUID;
    incompatible_limit_id UUID;
BEGIN
    SELECT id
    INTO incompatible_application_id
    FROM loan_applications
    WHERE status = 'DISBURSED'
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V28 cannot create activation evidence for an existing DISBURSED Loan Application %',
            incompatible_application_id
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements
        WHERE loan_account_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION
            'V28 cannot attach pre-existing Salary Advance movement LoanAccount references'
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements
        WHERE movement_type IN ('DISBURSED_TO_USED', 'REPAID_RELEASED')
    ) THEN
        RAISE EXCEPTION
            'V28 cannot reconcile pre-existing Salary Advance conversion or repayment-release movements'
            USING ERRCODE = '23514';
    END IF;

    SELECT id
    INTO incompatible_limit_id
    FROM salary_advance_limits
    WHERE used_amount <> 0
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V28 cannot reconcile pre-existing used Salary Advance exposure for limit %',
            incompatible_limit_id
            USING ERRCODE = '23514';
    END IF;

    SELECT limit_row.id
    INTO incompatible_limit_id
    FROM salary_advance_limits limit_row
    LEFT JOIN (
        SELECT
            salary_advance_limit_id,
            COALESCE(SUM(
                CASE
                    WHEN movement_type = 'RESERVED' THEN amount
                    WHEN movement_type = 'RESERVATION_RELEASED' THEN -amount
                    ELSE 0
                END
            ), 0) AS outstanding_reserved
        FROM salary_advance_limit_movements
        GROUP BY salary_advance_limit_id
    ) movement_totals
        ON movement_totals.salary_advance_limit_id = limit_row.id
    WHERE limit_row.reserved_amount <> COALESCE(movement_totals.outstanding_reserved, 0)
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V28 cannot reconcile existing Salary Advance reservation evidence for limit %',
            incompatible_limit_id
            USING ERRCODE = '23514';
    END IF;

    SELECT application_row.id
    INTO incompatible_application_id
    FROM loan_applications application_row
    WHERE application_row.status = 'DISBURSEMENT_PENDING'
      AND (
          SELECT COUNT(*)
          FROM loan_contracts contract_row
          WHERE contract_row.loan_application_id = application_row.id
            AND contract_row.status = 'READY_FOR_DISBURSEMENT'
      ) <> 1
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V28 requires one ready contract for existing DISBURSEMENT_PENDING Loan Application %',
            incompatible_application_id
            USING ERRCODE = '23514';
    END IF;
END
$$;

ALTER TABLE loan_contracts
    ADD CONSTRAINT uq_loan_contracts_id_application_customer
        UNIQUE (id, loan_application_id, customer_id);

CREATE TABLE loan_accounts (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_contract_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    account_number VARCHAR(35) NOT NULL,
    status VARCHAR(30) NOT NULL,

    approved_principal NUMERIC(19,2) NOT NULL,
    approved_term_months INTEGER NOT NULL,
    total_interest NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL,
    total_repayment_amount NUMERIC(19,2) NOT NULL,

    activated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_accounts_application_customer
        FOREIGN KEY (loan_application_id, customer_id)
        REFERENCES loan_applications (id, customer_id),
    CONSTRAINT fk_loan_accounts_contract_application_customer
        FOREIGN KEY (loan_contract_id, loan_application_id, customer_id)
        REFERENCES loan_contracts (id, loan_application_id, customer_id),
    CONSTRAINT fk_loan_accounts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers (id),

    CONSTRAINT uq_loan_accounts_application UNIQUE (loan_application_id),
    CONSTRAINT uq_loan_accounts_contract UNIQUE (loan_contract_id),
    CONSTRAINT uq_loan_accounts_account_number UNIQUE (account_number),
    CONSTRAINT uq_loan_accounts_id_application_contract
        UNIQUE (id, loan_application_id, loan_contract_id),

    CONSTRAINT chk_loan_accounts_account_number CHECK (
        account_number ~ '^LA-[0-9A-F]{32}$'
        AND account_number = 'LA-' || upper(replace(id::text, '-', ''))
    ),
    CONSTRAINT chk_loan_accounts_status CHECK (
        status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED')
    ),
    CONSTRAINT chk_loan_accounts_terms CHECK (
        approved_principal > 0
        AND approved_term_months > 0
        AND total_interest >= 0
        AND fee_amount >= 0
        AND total_repayment_amount = approved_principal + total_interest + fee_amount
        AND approved_principal = trunc(approved_principal)
        AND total_interest = trunc(total_interest)
        AND fee_amount = trunc(fee_amount)
        AND total_repayment_amount = trunc(total_repayment_amount)
    )
);

CREATE INDEX idx_loan_accounts_customer_id
    ON loan_accounts (customer_id);

CREATE TABLE manual_disbursements (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_contract_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    request_id UUID NOT NULL,
    expected_contract_version INTEGER NOT NULL,
    external_transfer_reference VARCHAR(64) NOT NULL,
    disbursed_amount NUMERIC(19,2) NOT NULL,
    disbursement_value_date DATE NOT NULL,
    first_repayment_date DATE NOT NULL,
    confirmed_by_user_id UUID NOT NULL,
    confirmed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_manual_disbursements_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_manual_disbursements_contract_version
        FOREIGN KEY (loan_contract_id, loan_application_id, expected_contract_version)
        REFERENCES loan_contracts (id, loan_application_id, contract_version),
    CONSTRAINT fk_manual_disbursements_account_application_contract
        FOREIGN KEY (loan_account_id, loan_application_id, loan_contract_id)
        REFERENCES loan_accounts (id, loan_application_id, loan_contract_id),
    CONSTRAINT fk_manual_disbursements_confirmed_by
        FOREIGN KEY (confirmed_by_user_id)
        REFERENCES users (id),

    CONSTRAINT uq_manual_disbursements_application UNIQUE (loan_application_id),
    CONSTRAINT uq_manual_disbursements_contract UNIQUE (loan_contract_id),
    CONSTRAINT uq_manual_disbursements_account UNIQUE (loan_account_id),
    CONSTRAINT uq_manual_disbursements_request UNIQUE (request_id),
    CONSTRAINT uq_manual_disbursements_transfer_reference UNIQUE (external_transfer_reference),

    CONSTRAINT chk_manual_disbursements_contract_version
        CHECK (expected_contract_version > 0),
    CONSTRAINT chk_manual_disbursements_transfer_reference CHECK (
        external_transfer_reference = btrim(external_transfer_reference)
        AND external_transfer_reference ~ '^[A-Z0-9][A-Z0-9._:/-]{0,63}$'
    ),
    CONSTRAINT chk_manual_disbursements_amount CHECK (
        disbursed_amount > 0
        AND disbursed_amount = trunc(disbursed_amount)
    ),
    CONSTRAINT chk_manual_disbursements_repayment_dates CHECK (
        first_repayment_date > disbursement_value_date
        AND first_repayment_date <= (disbursement_value_date + INTERVAL '1 month')::date
    )
);

CREATE TABLE repayment_schedules (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_contract_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    schedule_type VARCHAR(20) NOT NULL,
    version INTEGER NOT NULL,
    approved_term_months INTEGER NOT NULL,
    approved_principal NUMERIC(19,2) NOT NULL,
    total_interest NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL,
    total_repayment_amount NUMERIC(19,2) NOT NULL,
    first_due_date DATE NOT NULL,
    last_due_date DATE NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_schedules_application
        FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_repayment_schedules_contract
        FOREIGN KEY (loan_contract_id)
        REFERENCES loan_contracts (id),
    CONSTRAINT fk_repayment_schedules_account_application_contract
        FOREIGN KEY (loan_account_id, loan_application_id, loan_contract_id)
        REFERENCES loan_accounts (id, loan_application_id, loan_contract_id),

    CONSTRAINT uq_repayment_schedules_application UNIQUE (loan_application_id),
    CONSTRAINT uq_repayment_schedules_contract UNIQUE (loan_contract_id),
    CONSTRAINT uq_repayment_schedules_account UNIQUE (loan_account_id),

    CONSTRAINT chk_repayment_schedules_type_version CHECK (
        schedule_type = 'FINAL' AND version = 1
    ),
    CONSTRAINT chk_repayment_schedules_terms CHECK (
        approved_term_months > 0
        AND approved_principal > 0
        AND total_interest >= 0
        AND fee_amount >= 0
        AND total_repayment_amount = approved_principal + total_interest + fee_amount
        AND approved_principal = trunc(approved_principal)
        AND total_interest = trunc(total_interest)
        AND fee_amount = trunc(fee_amount)
        AND total_repayment_amount = trunc(total_repayment_amount)
        AND last_due_date >= first_due_date
    )
);

CREATE TABLE repayment_schedule_items (
    id UUID PRIMARY KEY,
    repayment_schedule_id UUID NOT NULL,
    source_loan_contract_repayment_item_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_due NUMERIC(19,2) NOT NULL,
    interest_due NUMERIC(19,2) NOT NULL,
    fee_due NUMERIC(19,2) NOT NULL,
    total_due NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_schedule_items_schedule
        FOREIGN KEY (repayment_schedule_id)
        REFERENCES repayment_schedules (id),
    CONSTRAINT fk_repayment_schedule_items_contract_item
        FOREIGN KEY (source_loan_contract_repayment_item_id)
        REFERENCES loan_contract_repayment_items (id),

    CONSTRAINT uq_repayment_schedule_items_schedule_installment
        UNIQUE (repayment_schedule_id, installment_number),
    CONSTRAINT uq_repayment_schedule_items_source_contract_item
        UNIQUE (source_loan_contract_repayment_item_id),

    CONSTRAINT chk_repayment_schedule_items_valid CHECK (
        installment_number > 0
        AND principal_due >= 0
        AND interest_due >= 0
        AND fee_due >= 0
        AND total_due >= 0
        AND total_due = principal_due + interest_due + fee_due
        AND principal_due = trunc(principal_due)
        AND interest_due = trunc(interest_due)
        AND fee_due = trunc(fee_due)
        AND total_due = trunc(total_due)
    )
);

CREATE INDEX idx_repayment_schedule_items_schedule
    ON repayment_schedule_items (repayment_schedule_id);

ALTER TABLE salary_advance_limit_movements
    ADD CONSTRAINT fk_salary_advance_limit_movements_loan_account
        FOREIGN KEY (loan_account_id)
        REFERENCES loan_accounts (id),
    ADD CONSTRAINT chk_salary_advance_limit_movements_disbursed_to_used CHECK (
        movement_type <> 'DISBURSED_TO_USED'
        OR (
            loan_application_id IS NOT NULL
            AND loan_account_id IS NOT NULL
            AND amount > 0
            AND amount = trunc(amount)
        )
    );

CREATE UNIQUE INDEX uq_salary_advance_limit_movements_application_disbursed_to_used
    ON salary_advance_limit_movements (loan_application_id)
    WHERE movement_type = 'DISBURSED_TO_USED'
      AND loan_application_id IS NOT NULL;

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action;

ALTER TABLE loan_application_status_transitions
    ADD CONSTRAINT chk_loan_application_status_transitions_action CHECK (action IN (
        'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
        'RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION',
        'REQUEST_STAFF_CORRECTION', 'APPROVE', 'REJECT', 'RETURN_TO_LOAN_OFFICER_REVIEW',
        'REQUEST_CUSTOMER_OR_STAFF_CORRECTION', 'RESUBMIT_CORRECTION', 'GENERATE_APPROVED_OFFER',
        'ACCEPT_APPROVED_OFFER', 'DECLINE_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER',
        'CONFIRM_DISBURSEMENT_READINESS', 'CONFIRM_MANUAL_DISBURSEMENT'
    ));

CREATE OR REPLACE FUNCTION require_active_loan_account_insert()
RETURNS trigger AS $$
BEGIN
    IF NEW.status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'New Loan Account must be ACTIVE';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_accounts_active_insert
    BEFORE INSERT ON loan_accounts
    FOR EACH ROW EXECUTE FUNCTION require_active_loan_account_insert();

CREATE OR REPLACE FUNCTION enforce_loan_account_mutation_boundary()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Loan Account rows cannot be deleted';
    END IF;

    IF ROW(
        NEW.id,
        NEW.loan_application_id,
        NEW.loan_contract_id,
        NEW.customer_id,
        NEW.account_number,
        NEW.approved_principal,
        NEW.approved_term_months,
        NEW.total_interest,
        NEW.fee_amount,
        NEW.total_repayment_amount,
        NEW.activated_at,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id,
        OLD.loan_application_id,
        OLD.loan_contract_id,
        OLD.customer_id,
        OLD.account_number,
        OLD.approved_principal,
        OLD.approved_term_months,
        OLD.total_interest,
        OLD.fee_amount,
        OLD.total_repayment_amount,
        OLD.activated_at,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION 'Loan Account immutable source and financial fields cannot be changed';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_accounts_immutable
    BEFORE UPDATE OR DELETE ON loan_accounts
    FOR EACH ROW EXECUTE FUNCTION enforce_loan_account_mutation_boundary();

CREATE TRIGGER trg_manual_disbursements_immutable
    BEFORE UPDATE OR DELETE ON manual_disbursements
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_schedules_immutable
    BEFORE UPDATE OR DELETE ON repayment_schedules
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_schedule_items_immutable
    BEFORE UPDATE OR DELETE ON repayment_schedule_items
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE OR REPLACE FUNCTION validate_repayment_schedule_reconciliation()
RETURNS trigger AS $$
DECLARE
    target_schedule_id UUID;
    schedule_row repayment_schedules%ROWTYPE;
    item_count BIGINT;
    source_count BIGINT;
    minimum_installment INTEGER;
    maximum_installment INTEGER;
    first_item_due_date DATE;
    last_item_due_date DATE;
    principal_sum NUMERIC(19,2);
    interest_sum NUMERIC(19,2);
    fee_sum NUMERIC(19,2);
    total_sum NUMERIC(19,2);
    non_increasing_dates BIGINT;
    source_mismatches BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'repayment_schedules' THEN
        target_schedule_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_schedule_id := COALESCE(NEW.repayment_schedule_id, OLD.repayment_schedule_id);
    END IF;

    SELECT *
    INTO schedule_row
    FROM repayment_schedules
    WHERE id = target_schedule_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT
        COUNT(*),
        COUNT(DISTINCT source_loan_contract_repayment_item_id),
        MIN(installment_number),
        MAX(installment_number),
        MIN(due_date) FILTER (WHERE installment_number = 1),
        MAX(due_date) FILTER (WHERE installment_number = schedule_row.approved_term_months),
        COALESCE(SUM(principal_due), 0),
        COALESCE(SUM(interest_due), 0),
        COALESCE(SUM(fee_due), 0),
        COALESCE(SUM(total_due), 0)
    INTO
        item_count,
        source_count,
        minimum_installment,
        maximum_installment,
        first_item_due_date,
        last_item_due_date,
        principal_sum,
        interest_sum,
        fee_sum,
        total_sum
    FROM repayment_schedule_items
    WHERE repayment_schedule_id = target_schedule_id;

    SELECT COUNT(*)
    INTO non_increasing_dates
    FROM (
        SELECT
            due_date,
            lag(due_date) OVER (ORDER BY installment_number) AS previous_due_date
        FROM repayment_schedule_items
        WHERE repayment_schedule_id = target_schedule_id
    ) ordered_items
    WHERE previous_due_date IS NOT NULL
      AND due_date <= previous_due_date;

    SELECT COUNT(*)
    INTO source_mismatches
    FROM repayment_schedule_items item
    LEFT JOIN loan_contract_repayment_items source_item
        ON source_item.id = item.source_loan_contract_repayment_item_id
    WHERE item.repayment_schedule_id = target_schedule_id
      AND (
          source_item.id IS NULL
          OR source_item.loan_contract_id <> schedule_row.loan_contract_id
          OR source_item.installment_number <> item.installment_number
          OR source_item.principal_due <> item.principal_due
          OR source_item.interest_due <> item.interest_due
          OR source_item.fee_due <> item.fee_due
          OR source_item.total_due <> item.total_due
      );

    IF item_count <> schedule_row.approved_term_months
        OR source_count <> item_count
        OR minimum_installment <> 1
        OR maximum_installment <> schedule_row.approved_term_months
        OR first_item_due_date <> schedule_row.first_due_date
        OR last_item_due_date <> schedule_row.last_due_date
        OR non_increasing_dates <> 0
        OR source_mismatches <> 0
        OR principal_sum <> schedule_row.approved_principal
        OR interest_sum <> schedule_row.total_interest
        OR fee_sum <> schedule_row.fee_amount
        OR total_sum <> schedule_row.total_repayment_amount THEN
        RAISE EXCEPTION 'Final repayment schedule does not reconcile to its source contract';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_repayment_schedule_reconcile_schedule
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedules
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_schedule_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_schedule_reconcile_items
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedule_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_schedule_reconciliation();

CREATE OR REPLACE FUNCTION validate_loan_activation_foundation()
RETURNS trigger AS $$
DECLARE
    target_application_id UUID;
    account_row loan_accounts%ROWTYPE;
    disbursement_row manual_disbursements%ROWTYPE;
    schedule_row repayment_schedules%ROWTYPE;
    account_count BIGINT;
    disbursement_count BIGINT;
    schedule_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'loan_accounts' THEN
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    ELSIF TG_TABLE_NAME = 'manual_disbursements' THEN
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    ELSIF TG_TABLE_NAME = 'repayment_schedules' THEN
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    ELSE
        SELECT loan_application_id
        INTO target_application_id
        FROM repayment_schedules
        WHERE id = COALESCE(NEW.repayment_schedule_id, OLD.repayment_schedule_id);
    END IF;

    IF target_application_id IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*)
    INTO account_count
    FROM loan_accounts
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO disbursement_count
    FROM manual_disbursements
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO schedule_count
    FROM repayment_schedules
    WHERE loan_application_id = target_application_id;

    IF account_count = 0 AND disbursement_count = 0 AND schedule_count = 0 THEN
        RETURN NULL;
    END IF;

    IF account_count <> 1 OR disbursement_count <> 1 OR schedule_count <> 1 THEN
        RAISE EXCEPTION 'Loan activation foundation evidence is incomplete';
    END IF;

    SELECT *
    INTO account_row
    FROM loan_accounts
    WHERE loan_application_id = target_application_id;

    SELECT *
    INTO disbursement_row
    FROM manual_disbursements
    WHERE loan_application_id = target_application_id;

    SELECT *
    INTO schedule_row
    FROM repayment_schedules
    WHERE loan_application_id = target_application_id;

    IF NOT EXISTS (
        SELECT 1
        FROM loan_contracts contract_row
        WHERE contract_row.id = account_row.loan_contract_id
          AND contract_row.loan_application_id = account_row.loan_application_id
          AND contract_row.customer_id = account_row.customer_id
          AND contract_row.status = 'READY_FOR_DISBURSEMENT'
          AND contract_row.approved_principal = account_row.approved_principal
          AND contract_row.approved_term_months = account_row.approved_term_months
          AND contract_row.total_interest = account_row.total_interest
          AND contract_row.fee_amount = account_row.fee_amount
          AND contract_row.total_repayment_amount = account_row.total_repayment_amount
    ) THEN
        RAISE EXCEPTION 'Loan Account does not match its ready contract';
    END IF;

    IF disbursement_row.loan_contract_id <> account_row.loan_contract_id
        OR disbursement_row.loan_account_id <> account_row.id
        OR disbursement_row.disbursed_amount <> account_row.approved_principal THEN
        RAISE EXCEPTION 'Manual disbursement evidence does not match the Loan Account';
    END IF;

    IF schedule_row.loan_contract_id <> account_row.loan_contract_id
        OR schedule_row.loan_account_id <> account_row.id
        OR schedule_row.approved_term_months <> account_row.approved_term_months
        OR schedule_row.approved_principal <> account_row.approved_principal
        OR schedule_row.total_interest <> account_row.total_interest
        OR schedule_row.fee_amount <> account_row.fee_amount
        OR schedule_row.total_repayment_amount <> account_row.total_repayment_amount
        OR schedule_row.first_due_date <> disbursement_row.first_repayment_date
        OR schedule_row.first_due_date <= disbursement_row.disbursement_value_date THEN
        RAISE EXCEPTION 'Final repayment schedule header does not match the Loan Account';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_account
    AFTER INSERT OR UPDATE OR DELETE ON loan_accounts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_disbursement
    AFTER INSERT OR UPDATE OR DELETE ON manual_disbursements
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_schedule
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedules
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE CONSTRAINT TRIGGER trg_loan_activation_foundation_schedule_item
    AFTER INSERT OR UPDATE OR DELETE ON repayment_schedule_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_activation_foundation();

CREATE OR REPLACE FUNCTION reject_disbursed_to_used_movement_mutation()
RETURNS trigger AS $$
BEGIN
    IF OLD.movement_type = 'DISBURSED_TO_USED' THEN
        RAISE EXCEPTION 'Disbursed-to-used Salary Advance movement is immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.movement_type = 'DISBURSED_TO_USED' THEN
        RAISE EXCEPTION 'Disbursed-to-used Salary Advance movement must be inserted as new evidence';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_salary_advance_disbursed_to_used_immutable
    BEFORE UPDATE OR DELETE ON salary_advance_limit_movements
    FOR EACH ROW EXECUTE FUNCTION reject_disbursed_to_used_movement_mutation();

CREATE OR REPLACE FUNCTION validate_salary_advance_conversion()
RETURNS trigger AS $$
DECLARE
    target_limit_id UUID;
    conversion_count BIGINT;
    expected_reserved NUMERIC(19,2);
    expected_used NUMERIC(19,2);
    limit_row salary_advance_limits%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME = 'salary_advance_limits' THEN
        target_limit_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_limit_id := COALESCE(NEW.salary_advance_limit_id, OLD.salary_advance_limit_id);
    END IF;

    SELECT COUNT(*)
    INTO conversion_count
    FROM salary_advance_limit_movements
    WHERE salary_advance_limit_id = target_limit_id
      AND movement_type = 'DISBURSED_TO_USED';

    IF conversion_count = 0 THEN
        RETURN NULL;
    END IF;

    SELECT *
    INTO limit_row
    FROM salary_advance_limits
    WHERE id = target_limit_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements conversion
        JOIN loan_accounts account_row
            ON account_row.id = conversion.loan_account_id
        WHERE conversion.salary_advance_limit_id = target_limit_id
          AND conversion.movement_type = 'DISBURSED_TO_USED'
          AND (
              account_row.loan_application_id <> conversion.loan_application_id
              OR account_row.customer_id <> limit_row.customer_id
              OR account_row.approved_principal <> conversion.amount
              OR (
                  SELECT COUNT(*)
                  FROM salary_advance_limit_movements reservation
                  WHERE reservation.loan_application_id = conversion.loan_application_id
                    AND reservation.salary_advance_limit_id = conversion.salary_advance_limit_id
                    AND reservation.movement_type = 'RESERVED'
                    AND reservation.amount = conversion.amount
              ) <> 1
              OR EXISTS (
                  SELECT 1
                  FROM salary_advance_limit_movements release
                  WHERE release.loan_application_id = conversion.loan_application_id
                    AND release.movement_type = 'RESERVATION_RELEASED'
              )
          )
    ) THEN
        RAISE EXCEPTION 'Salary Advance disbursed-to-used movement does not match reservation evidence';
    END IF;

    SELECT
        COALESCE(SUM(
            CASE
                WHEN movement_type = 'RESERVED' THEN amount
                WHEN movement_type IN ('RESERVATION_RELEASED', 'DISBURSED_TO_USED') THEN -amount
                ELSE 0
            END
        ), 0),
        COALESCE(SUM(
            CASE
                WHEN movement_type = 'DISBURSED_TO_USED' THEN amount
                WHEN movement_type = 'REPAID_RELEASED' THEN -amount
                ELSE 0
            END
        ), 0)
    INTO expected_reserved, expected_used
    FROM salary_advance_limit_movements
    WHERE salary_advance_limit_id = target_limit_id;

    IF limit_row.reserved_amount <> expected_reserved
        OR limit_row.used_amount <> expected_used
        OR limit_row.available_amount
            <> limit_row.total_limit - limit_row.used_amount - limit_row.reserved_amount THEN
        RAISE EXCEPTION 'Salary Advance limit does not reconcile after exposure conversion';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_salary_advance_conversion_movement
    AFTER INSERT OR UPDATE OR DELETE ON salary_advance_limit_movements
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_salary_advance_conversion();

CREATE CONSTRAINT TRIGGER trg_salary_advance_conversion_limit
    AFTER UPDATE ON salary_advance_limits
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_salary_advance_conversion();

CREATE OR REPLACE FUNCTION validate_loan_contract_application_lifecycle()
RETURNS trigger AS $$
DECLARE
    target_application_id UUID;
    application_status VARCHAR(50);
    ready_contract_count BIGINT;
    loan_account_count BIGINT;
    manual_disbursement_count BIGINT;
    final_schedule_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'loan_applications' THEN
        target_application_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    END IF;

    SELECT status
    INTO application_status
    FROM loan_applications
    WHERE id = target_application_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*)
    INTO ready_contract_count
    FROM loan_contracts
    WHERE loan_application_id = target_application_id
      AND status = 'READY_FOR_DISBURSEMENT';

    SELECT COUNT(*)
    INTO loan_account_count
    FROM loan_accounts
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO manual_disbursement_count
    FROM manual_disbursements
    WHERE loan_application_id = target_application_id;

    SELECT COUNT(*)
    INTO final_schedule_count
    FROM repayment_schedules
    WHERE loan_application_id = target_application_id
      AND schedule_type = 'FINAL'
      AND version = 1;

    IF application_status = 'DISBURSEMENT_PENDING'
        AND ready_contract_count <> 1 THEN
        RAISE EXCEPTION 'Disbursement-pending application requires one ready current contract';
    END IF;

    IF application_status = 'DISBURSED'
        AND (
            ready_contract_count <> 1
            OR loan_account_count <> 1
            OR manual_disbursement_count <> 1
            OR final_schedule_count <> 1
        ) THEN
        RAISE EXCEPTION 'Disbursed application requires complete activation evidence';
    END IF;

    IF ready_contract_count > 0
        AND application_status NOT IN ('DISBURSEMENT_PENDING', 'DISBURSED') THEN
        RAISE EXCEPTION 'Ready contract requires a disbursement-stage application';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
