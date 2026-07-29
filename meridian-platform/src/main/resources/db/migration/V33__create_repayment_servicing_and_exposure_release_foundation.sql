DO $$
DECLARE
    unexpected_table TEXT;
    incompatible_account_id UUID;
    incompatible_limit_id UUID;
    permission_count INTEGER;
    accounting_grant_count INTEGER;
BEGIN
    SELECT candidate.table_name
    INTO unexpected_table
    FROM unnest(ARRAY[
        'repayment_transactions',
        'repayment_allocations',
        'repayment_installment_progress',
        'loan_account_status_transitions',
        'repayment_installment_status_transitions'
    ]) AS candidate(table_name)
    WHERE to_regclass(current_schema() || '.' || candidate.table_name) IS NOT NULL
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: repayment table % unexpectedly exists',
            unexpected_table
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements
        WHERE movement_type = 'REPAID_RELEASED'
    ) THEN
        RAISE EXCEPTION
            'V33 preflight failed: REPAID_RELEASED movement lacks repayment evidence'
            USING ERRCODE = '23514';
    END IF;

    SELECT account_row.id
    INTO incompatible_account_id
    FROM loan_accounts account_row
    JOIN loan_applications application_row
        ON application_row.id = account_row.loan_application_id
    WHERE application_row.product_code <> 'SALARY_ADVANCE'
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: unsupported-product activated LoanAccount %',
            incompatible_account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT id
    INTO incompatible_account_id
    FROM loan_accounts
    WHERE status IN ('SETTLED', 'CLOSED')
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: settled or closed LoanAccount % has no servicing evidence',
            incompatible_account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT account_row.id
    INTO incompatible_account_id
    FROM loan_accounts account_row
    LEFT JOIN loan_applications application_row
        ON application_row.id = account_row.loan_application_id
    LEFT JOIN loan_contracts contract_row
        ON contract_row.id = account_row.loan_contract_id
       AND contract_row.loan_application_id = account_row.loan_application_id
       AND contract_row.customer_id = account_row.customer_id
    LEFT JOIN manual_disbursements disbursement_row
        ON disbursement_row.loan_account_id = account_row.id
       AND disbursement_row.loan_application_id = account_row.loan_application_id
       AND disbursement_row.loan_contract_id = account_row.loan_contract_id
    LEFT JOIN repayment_schedules schedule_row
        ON schedule_row.loan_account_id = account_row.id
       AND schedule_row.loan_application_id = account_row.loan_application_id
       AND schedule_row.loan_contract_id = account_row.loan_contract_id
    WHERE application_row.id IS NULL
       OR application_row.status <> 'DISBURSED'
       OR application_row.customer_id <> account_row.customer_id
       OR contract_row.id IS NULL
       OR contract_row.status <> 'READY_FOR_DISBURSEMENT'
       OR disbursement_row.id IS NULL
       OR disbursement_row.disbursed_amount <> account_row.approved_principal
       OR schedule_row.id IS NULL
       OR schedule_row.schedule_type <> 'FINAL'
       OR schedule_row.version <> 1
       OR schedule_row.approved_principal <> account_row.approved_principal
       OR schedule_row.total_interest <> account_row.total_interest
       OR schedule_row.fee_amount <> account_row.fee_amount
       OR schedule_row.total_repayment_amount <> account_row.total_repayment_amount
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: LoanAccount % lacks complete activation evidence',
            incompatible_account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT schedule_row.loan_account_id
    INTO incompatible_account_id
    FROM repayment_schedules schedule_row
    LEFT JOIN (
        SELECT
            repayment_schedule_id,
            COUNT(*) AS item_count,
            COALESCE(SUM(principal_due), 0) AS principal_sum,
            COALESCE(SUM(interest_due), 0) AS interest_sum,
            COALESCE(SUM(fee_due), 0) AS fee_sum,
            COALESCE(SUM(total_due), 0) AS total_sum
        FROM repayment_schedule_items
        GROUP BY repayment_schedule_id
    ) item_totals ON item_totals.repayment_schedule_id = schedule_row.id
    WHERE COALESCE(item_totals.item_count, 0) <> schedule_row.approved_term_months
       OR item_totals.principal_sum <> schedule_row.approved_principal
       OR item_totals.interest_sum <> schedule_row.total_interest
       OR item_totals.fee_sum <> schedule_row.fee_amount
       OR item_totals.total_sum <> schedule_row.total_repayment_amount
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: final schedule for LoanAccount % is invalid',
            incompatible_account_id
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
                    WHEN movement_type IN (
                        'RESERVATION_RELEASED', 'DISBURSED_TO_USED'
                    ) THEN -amount
                    ELSE 0
                END
            ), 0) AS expected_reserved,
            COALESCE(SUM(
                CASE
                    WHEN movement_type = 'DISBURSED_TO_USED' THEN amount
                    WHEN movement_type = 'REPAID_RELEASED' THEN -amount
                    ELSE 0
                END
            ), 0) AS expected_used
        FROM salary_advance_limit_movements
        GROUP BY salary_advance_limit_id
    ) movement_totals
        ON movement_totals.salary_advance_limit_id = limit_row.id
    WHERE limit_row.reserved_amount
            <> COALESCE(movement_totals.expected_reserved, 0)
       OR limit_row.used_amount <> COALESCE(movement_totals.expected_used, 0)
       OR limit_row.available_amount
            <> limit_row.total_limit
                - limit_row.used_amount
                - limit_row.reserved_amount
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'V33 preflight failed: Salary Advance limit % is inconsistent',
            incompatible_limit_id
            USING ERRCODE = '23514';
    END IF;

    SELECT COUNT(*)
    INTO permission_count
    FROM permissions
    WHERE code = 'repayment:update';

    SELECT COUNT(*)
    INTO accounting_grant_count
    FROM permissions permission_row
    JOIN role_permissions role_permission
        ON role_permission.permission_id = permission_row.id
    JOIN roles role_row
        ON role_row.id = role_permission.role_id
    WHERE permission_row.code = 'repayment:update'
      AND role_row.code = 'ACCOUNTING_OFFICER';

    IF permission_count <> 1 OR accounting_grant_count <> 1 THEN
        RAISE EXCEPTION
            'V33 preflight failed: repayment:update permission or current grant is missing or ambiguous'
            USING ERRCODE = '23514';
    END IF;
END;
$$;

ALTER TABLE loan_accounts
    ADD CONSTRAINT uq_loan_accounts_id_application
        UNIQUE (id, loan_application_id);

ALTER TABLE repayment_schedules
    ADD CONSTRAINT uq_repayment_schedules_id_application_account
        UNIQUE (id, loan_application_id, loan_account_id),
    ADD CONSTRAINT uq_repayment_schedules_id_account
        UNIQUE (id, loan_account_id);

ALTER TABLE repayment_schedule_items
    ADD CONSTRAINT uq_repayment_schedule_items_id_schedule
        UNIQUE (id, repayment_schedule_id);

CREATE INDEX idx_repayment_schedule_items_due_order
    ON repayment_schedule_items (
        repayment_schedule_id,
        due_date,
        installment_number
    );

ALTER TABLE loan_accounts
    ADD COLUMN principal_paid NUMERIC(19,2),
    ADD COLUMN interest_paid NUMERIC(19,2),
    ADD COLUMN fee_paid NUMERIC(19,2),
    ADD COLUMN total_paid NUMERIC(19,2),
    ADD COLUMN principal_outstanding NUMERIC(19,2),
    ADD COLUMN interest_outstanding NUMERIC(19,2),
    ADD COLUMN fee_outstanding NUMERIC(19,2),
    ADD COLUMN total_outstanding NUMERIC(19,2),
    ADD COLUMN last_payment_value_date DATE,
    ADD COLUMN last_payment_recorded_at TIMESTAMP,
    ADD COLUMN servicing_evaluation_date DATE;

CREATE TABLE repayment_transactions (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    repayment_schedule_id UUID NOT NULL,
    request_id UUID NOT NULL,
    external_payment_reference VARCHAR(64) NOT NULL,
    received_amount NUMERIC(19,2) NOT NULL,
    payment_value_date DATE NOT NULL,
    recorded_by_user_id UUID NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_transactions_account_application
        FOREIGN KEY (loan_account_id, loan_application_id)
        REFERENCES loan_accounts (id, loan_application_id),
    CONSTRAINT fk_repayment_transactions_schedule_ownership
        FOREIGN KEY (
            repayment_schedule_id,
            loan_application_id,
            loan_account_id
        )
        REFERENCES repayment_schedules (
            id,
            loan_application_id,
            loan_account_id
        ),
    CONSTRAINT fk_repayment_transactions_recorded_by
        FOREIGN KEY (recorded_by_user_id)
        REFERENCES users (id),

    CONSTRAINT uq_repayment_transactions_request UNIQUE (request_id),
    CONSTRAINT uq_repayment_transactions_reference
        UNIQUE (external_payment_reference),
    CONSTRAINT uq_repayment_transactions_id_schedule
        UNIQUE (id, repayment_schedule_id),

    CONSTRAINT chk_repayment_transactions_reference CHECK (
        external_payment_reference = btrim(external_payment_reference)
        AND external_payment_reference
            ~ '^[A-Z0-9][A-Z0-9._:/-]{0,63}$'
    ),
    CONSTRAINT chk_repayment_transactions_amount CHECK (
        received_amount > 0
        AND received_amount = trunc(received_amount)
    ),
    CONSTRAINT chk_repayment_transactions_dates CHECK (
        payment_value_date <= recorded_at::date
    )
);

CREATE INDEX idx_repayment_transactions_account_recorded
    ON repayment_transactions (loan_account_id, recorded_at, id);

CREATE INDEX idx_repayment_transactions_schedule
    ON repayment_transactions (repayment_schedule_id);

CREATE TABLE repayment_allocations (
    id UUID PRIMARY KEY,
    repayment_transaction_id UUID NOT NULL,
    allocation_sequence INTEGER NOT NULL,
    repayment_schedule_item_id UUID NOT NULL,
    component VARCHAR(20) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_allocations_transaction
        FOREIGN KEY (repayment_transaction_id)
        REFERENCES repayment_transactions (id),
    CONSTRAINT fk_repayment_allocations_schedule_item
        FOREIGN KEY (repayment_schedule_item_id)
        REFERENCES repayment_schedule_items (id),

    CONSTRAINT uq_repayment_allocations_transaction_sequence
        UNIQUE (repayment_transaction_id, allocation_sequence),
    CONSTRAINT uq_repayment_allocations_transaction_item_component
        UNIQUE (
            repayment_transaction_id,
            repayment_schedule_item_id,
            component
        ),

    CONSTRAINT chk_repayment_allocations_sequence
        CHECK (allocation_sequence > 0),
    CONSTRAINT chk_repayment_allocations_component
        CHECK (component IN ('FEE', 'INTEREST', 'PRINCIPAL')),
    CONSTRAINT chk_repayment_allocations_amount CHECK (
        amount > 0
        AND amount = trunc(amount)
    )
);

CREATE INDEX idx_repayment_allocations_schedule_item
    ON repayment_allocations (repayment_schedule_item_id);

CREATE TABLE repayment_installment_progress (
    repayment_schedule_item_id UUID PRIMARY KEY,
    repayment_schedule_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,

    principal_paid NUMERIC(19,2) NOT NULL,
    interest_paid NUMERIC(19,2) NOT NULL,
    fee_paid NUMERIC(19,2) NOT NULL,
    total_paid NUMERIC(19,2) NOT NULL,
    principal_outstanding NUMERIC(19,2) NOT NULL,
    interest_outstanding NUMERIC(19,2) NOT NULL,
    fee_outstanding NUMERIC(19,2) NOT NULL,
    total_outstanding NUMERIC(19,2) NOT NULL,

    status VARCHAR(30) NOT NULL,
    last_payment_value_date DATE,
    last_payment_recorded_at TIMESTAMP,
    servicing_evaluation_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_repayment_progress_item_schedule
        FOREIGN KEY (repayment_schedule_item_id, repayment_schedule_id)
        REFERENCES repayment_schedule_items (id, repayment_schedule_id),
    CONSTRAINT fk_repayment_progress_schedule_account
        FOREIGN KEY (repayment_schedule_id, loan_account_id)
        REFERENCES repayment_schedules (id, loan_account_id),

    CONSTRAINT uq_repayment_progress_schedule_installment
        UNIQUE (repayment_schedule_id, installment_number),

    CONSTRAINT chk_repayment_progress_installment
        CHECK (installment_number > 0),
    CONSTRAINT chk_repayment_progress_amounts CHECK (
        principal_paid >= 0
        AND interest_paid >= 0
        AND fee_paid >= 0
        AND total_paid >= 0
        AND principal_outstanding >= 0
        AND interest_outstanding >= 0
        AND fee_outstanding >= 0
        AND total_outstanding >= 0
        AND principal_paid = trunc(principal_paid)
        AND interest_paid = trunc(interest_paid)
        AND fee_paid = trunc(fee_paid)
        AND total_paid = trunc(total_paid)
        AND principal_outstanding = trunc(principal_outstanding)
        AND interest_outstanding = trunc(interest_outstanding)
        AND fee_outstanding = trunc(fee_outstanding)
        AND total_outstanding = trunc(total_outstanding)
        AND total_paid = principal_paid + interest_paid + fee_paid
        AND total_outstanding
            = principal_outstanding + interest_outstanding + fee_outstanding
    ),
    CONSTRAINT chk_repayment_progress_status CHECK (
        status IN ('NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE')
    ),
    CONSTRAINT chk_repayment_progress_payment_dates CHECK (
        (
            total_paid = 0
            AND last_payment_value_date IS NULL
            AND last_payment_recorded_at IS NULL
        )
        OR (
            total_paid > 0
            AND last_payment_value_date IS NOT NULL
            AND last_payment_recorded_at IS NOT NULL
        )
    )
);

CREATE INDEX idx_repayment_progress_account_installment
    ON repayment_installment_progress (loan_account_id, installment_number);

CREATE INDEX idx_repayment_progress_due_status
    ON repayment_installment_progress (
        servicing_evaluation_date,
        status,
        loan_account_id
    );

CREATE TABLE loan_account_status_transitions (
    id UUID PRIMARY KEY,
    loan_account_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    operation_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    servicing_evaluation_date DATE NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_account_status_history_account
        FOREIGN KEY (loan_account_id)
        REFERENCES loan_accounts (id),
    CONSTRAINT fk_loan_account_status_history_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_loan_account_status_history_sequence
        UNIQUE (loan_account_id, sequence_number),
    CONSTRAINT uq_loan_account_status_history_operation
        UNIQUE (loan_account_id, operation_id),
    CONSTRAINT chk_loan_account_status_history_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_loan_account_status_history_statuses CHECK (
        (from_status IS NULL OR from_status IN ('ACTIVE', 'OVERDUE', 'SETTLED'))
        AND to_status IN ('ACTIVE', 'OVERDUE', 'SETTLED')
        AND from_status IS DISTINCT FROM to_status
    ),
    CONSTRAINT chk_loan_account_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED'
        )
    ),
    CONSTRAINT chk_loan_account_status_history_actor CHECK (
        (actor_type = 'USER' AND actor_user_id IS NOT NULL)
        OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
    ),
    CONSTRAINT chk_loan_account_status_history_initial CHECK (
        (
            sequence_number = 1
            AND from_status IS NULL
            AND action = 'ACTIVATION_INITIALIZED'
        )
        OR (sequence_number > 1 AND from_status IS NOT NULL)
    )
);

CREATE INDEX idx_loan_account_status_history_account_occurred
    ON loan_account_status_transitions (
        loan_account_id,
        occurred_at,
        sequence_number
    );

CREATE TABLE repayment_installment_status_transitions (
    id UUID PRIMARY KEY,
    repayment_schedule_item_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL,
    operation_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    servicing_evaluation_date DATE NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_repayment_installment_status_history_item
        FOREIGN KEY (repayment_schedule_item_id)
        REFERENCES repayment_schedule_items (id),
    CONSTRAINT fk_repayment_installment_status_history_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT uq_repayment_installment_status_history_sequence
        UNIQUE (repayment_schedule_item_id, sequence_number),
    CONSTRAINT uq_repayment_installment_status_history_operation
        UNIQUE (repayment_schedule_item_id, operation_id),
    CONSTRAINT chk_repayment_installment_status_history_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT chk_repayment_installment_status_history_statuses CHECK (
        (
            from_status IS NULL
            OR from_status IN (
                'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
            )
        )
        AND to_status IN (
            'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
        )
        AND from_status IS DISTINCT FROM to_status
    ),
    CONSTRAINT chk_repayment_installment_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED'
        )
    ),
    CONSTRAINT chk_repayment_installment_status_history_actor CHECK (
        (actor_type = 'USER' AND actor_user_id IS NOT NULL)
        OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
    ),
    CONSTRAINT chk_repayment_installment_status_history_initial CHECK (
        (
            sequence_number = 1
            AND from_status IS NULL
            AND action = 'ACTIVATION_INITIALIZED'
        )
        OR (sequence_number > 1 AND from_status IS NOT NULL)
    )
);

CREATE INDEX idx_repayment_installment_status_history_item_occurred
    ON repayment_installment_status_transitions (
        repayment_schedule_item_id,
        occurred_at,
        sequence_number
    );

ALTER TABLE salary_advance_limit_movements
    ADD COLUMN repayment_transaction_id UUID,
    ADD CONSTRAINT fk_salary_advance_movements_repayment_transaction
        FOREIGN KEY (repayment_transaction_id)
        REFERENCES repayment_transactions (id),
    ADD CONSTRAINT chk_salary_advance_movements_repayment_release CHECK (
        (
            movement_type = 'REPAID_RELEASED'
            AND repayment_transaction_id IS NOT NULL
            AND loan_application_id IS NOT NULL
            AND loan_account_id IS NOT NULL
            AND amount > 0
            AND amount = trunc(amount)
        )
        OR (
            movement_type <> 'REPAID_RELEASED'
            AND repayment_transaction_id IS NULL
        )
    );

CREATE UNIQUE INDEX uq_salary_advance_movements_repayment_release
    ON salary_advance_limit_movements (repayment_transaction_id)
    WHERE movement_type = 'REPAID_RELEASED';

INSERT INTO repayment_installment_progress (
    repayment_schedule_item_id,
    repayment_schedule_id,
    loan_account_id,
    installment_number,
    principal_paid,
    interest_paid,
    fee_paid,
    total_paid,
    principal_outstanding,
    interest_outstanding,
    fee_outstanding,
    total_outstanding,
    status,
    last_payment_value_date,
    last_payment_recorded_at,
    servicing_evaluation_date,
    created_at,
    updated_at
)
SELECT
    item.id,
    schedule_row.id,
    account_row.id,
    item.installment_number,
    0,
    0,
    0,
    0,
    item.principal_due,
    item.interest_due,
    item.fee_due,
    item.total_due,
    CASE
        WHEN item.due_date < account_row.activated_at::date THEN 'OVERDUE'
        WHEN item.due_date = account_row.activated_at::date THEN 'DUE'
        ELSE 'NOT_DUE'
    END,
    NULL,
    NULL,
    account_row.activated_at::date,
    account_row.activated_at,
    account_row.activated_at
FROM loan_accounts account_row
JOIN repayment_schedules schedule_row
    ON schedule_row.loan_account_id = account_row.id
JOIN repayment_schedule_items item
    ON item.repayment_schedule_id = schedule_row.id;

UPDATE loan_accounts account_row
SET principal_paid = 0,
    interest_paid = 0,
    fee_paid = 0,
    total_paid = 0,
    principal_outstanding = account_row.approved_principal,
    interest_outstanding = account_row.total_interest,
    fee_outstanding = account_row.fee_amount,
    total_outstanding = account_row.total_repayment_amount,
    last_payment_value_date = NULL,
    last_payment_recorded_at = NULL,
    servicing_evaluation_date = account_row.activated_at::date,
    status = CASE
        WHEN EXISTS (
            SELECT 1
            FROM repayment_installment_progress progress
            WHERE progress.loan_account_id = account_row.id
              AND progress.status = 'OVERDUE'
        ) THEN 'OVERDUE'
        ELSE 'ACTIVE'
    END,
    updated_at = account_row.activated_at;

-- Flush the V28 deferred activation event before altering the same table.
-- Restore deferred mode so later atomic servicing writes retain their contract.
SET CONSTRAINTS trg_loan_activation_foundation_account IMMEDIATE;
SET CONSTRAINTS trg_loan_activation_foundation_account DEFERRED;

ALTER TABLE loan_accounts
    ALTER COLUMN principal_paid SET NOT NULL,
    ALTER COLUMN interest_paid SET NOT NULL,
    ALTER COLUMN fee_paid SET NOT NULL,
    ALTER COLUMN total_paid SET NOT NULL,
    ALTER COLUMN principal_outstanding SET NOT NULL,
    ALTER COLUMN interest_outstanding SET NOT NULL,
    ALTER COLUMN fee_outstanding SET NOT NULL,
    ALTER COLUMN total_outstanding SET NOT NULL,
    ALTER COLUMN servicing_evaluation_date SET NOT NULL,
    ADD CONSTRAINT chk_loan_accounts_servicing_amounts CHECK (
        principal_paid >= 0
        AND interest_paid >= 0
        AND fee_paid >= 0
        AND total_paid >= 0
        AND principal_outstanding >= 0
        AND interest_outstanding >= 0
        AND fee_outstanding >= 0
        AND total_outstanding >= 0
        AND principal_paid = trunc(principal_paid)
        AND interest_paid = trunc(interest_paid)
        AND fee_paid = trunc(fee_paid)
        AND total_paid = trunc(total_paid)
        AND principal_outstanding = trunc(principal_outstanding)
        AND interest_outstanding = trunc(interest_outstanding)
        AND fee_outstanding = trunc(fee_outstanding)
        AND total_outstanding = trunc(total_outstanding)
        AND total_paid = principal_paid + interest_paid + fee_paid
        AND total_outstanding
            = principal_outstanding + interest_outstanding + fee_outstanding
        AND principal_paid + principal_outstanding = approved_principal
        AND interest_paid + interest_outstanding = total_interest
        AND fee_paid + fee_outstanding = fee_amount
        AND total_paid + total_outstanding = total_repayment_amount
    ),
    ADD CONSTRAINT chk_loan_accounts_servicing_dates CHECK (
        (
            total_paid = 0
            AND last_payment_value_date IS NULL
            AND last_payment_recorded_at IS NULL
        )
        OR (
            total_paid > 0
            AND last_payment_value_date IS NOT NULL
            AND last_payment_recorded_at IS NOT NULL
        )
    ),
    ADD CONSTRAINT chk_loan_accounts_settlement_balance CHECK (
        (status = 'SETTLED' AND total_outstanding = 0)
        OR (status IN ('ACTIVE', 'OVERDUE') AND total_outstanding > 0)
        OR status = 'CLOSED'
    );

INSERT INTO loan_account_status_transitions (
    id,
    loan_account_id,
    sequence_number,
    operation_id,
    from_status,
    to_status,
    action,
    actor_type,
    actor_user_id,
    servicing_evaluation_date,
    occurred_at,
    created_at
)
SELECT
    md5(account_row.id::text || ':loan-account-initial-status')::uuid,
    account_row.id,
    1,
    md5(account_row.id::text || ':activation-servicing-operation')::uuid,
    NULL,
    account_row.status,
    'ACTIVATION_INITIALIZED',
    'SYSTEM',
    NULL,
    account_row.servicing_evaluation_date,
    account_row.activated_at,
    account_row.activated_at
FROM loan_accounts account_row;

INSERT INTO repayment_installment_status_transitions (
    id,
    repayment_schedule_item_id,
    sequence_number,
    operation_id,
    from_status,
    to_status,
    action,
    actor_type,
    actor_user_id,
    servicing_evaluation_date,
    occurred_at,
    created_at
)
SELECT
    md5(progress.repayment_schedule_item_id::text
        || ':installment-initial-status')::uuid,
    progress.repayment_schedule_item_id,
    1,
    md5(progress.loan_account_id::text
        || ':activation-servicing-operation')::uuid,
    NULL,
    progress.status,
    'ACTIVATION_INITIALIZED',
    'SYSTEM',
    NULL,
    progress.servicing_evaluation_date,
    account_row.activated_at,
    account_row.activated_at
FROM repayment_installment_progress progress
JOIN loan_accounts account_row
    ON account_row.id = progress.loan_account_id;

CREATE TRIGGER trg_repayment_transactions_immutable
    BEFORE UPDATE OR DELETE ON repayment_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_allocations_immutable
    BEFORE UPDATE OR DELETE ON repayment_allocations
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_loan_account_status_transitions_immutable
    BEFORE UPDATE OR DELETE ON loan_account_status_transitions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_repayment_installment_status_transitions_immutable
    BEFORE UPDATE OR DELETE ON repayment_installment_status_transitions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE OR REPLACE FUNCTION enforce_repayment_progress_mutation_boundary()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Repayment installment progress rows cannot be deleted';
    END IF;

    IF ROW(
        NEW.repayment_schedule_item_id,
        NEW.repayment_schedule_id,
        NEW.loan_account_id,
        NEW.installment_number,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.repayment_schedule_item_id,
        OLD.repayment_schedule_id,
        OLD.loan_account_id,
        OLD.installment_number,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION
            'Repayment installment progress ownership cannot be changed';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_repayment_progress_mutation_boundary
    BEFORE UPDATE OR DELETE ON repayment_installment_progress
    FOR EACH ROW EXECUTE FUNCTION enforce_repayment_progress_mutation_boundary();

CREATE OR REPLACE FUNCTION reject_disbursed_to_used_movement_mutation()
RETURNS trigger AS $$
BEGIN
    IF OLD.movement_type IN ('DISBURSED_TO_USED', 'REPAID_RELEASED') THEN
        RAISE EXCEPTION
            'Salary Advance conversion and repayment-release movements are immutable';
    END IF;
    IF TG_OP = 'UPDATE'
            AND NEW.movement_type IN ('DISBURSED_TO_USED', 'REPAID_RELEASED') THEN
        RAISE EXCEPTION
            'Salary Advance conversion and repayment-release evidence must be inserted';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_servicing_reconciliation()
RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM repayment_transactions transaction_row
        LEFT JOIN manual_disbursements disbursement_row
            ON disbursement_row.loan_account_id = transaction_row.loan_account_id
        LEFT JOIN (
            SELECT
                repayment_transaction_id,
                COUNT(*) AS allocation_count,
                MIN(allocation_sequence) AS minimum_sequence,
                MAX(allocation_sequence) AS maximum_sequence,
                COALESCE(SUM(amount), 0) AS allocated_amount
            FROM repayment_allocations
            GROUP BY repayment_transaction_id
        ) allocation_totals
            ON allocation_totals.repayment_transaction_id = transaction_row.id
        WHERE disbursement_row.id IS NULL
           OR transaction_row.payment_value_date
                < disbursement_row.disbursement_value_date
           OR transaction_row.payment_value_date > transaction_row.recorded_at::date
           OR COALESCE(allocation_totals.allocation_count, 0) = 0
           OR allocation_totals.minimum_sequence <> 1
           OR allocation_totals.maximum_sequence
                <> allocation_totals.allocation_count
           OR allocation_totals.allocated_amount
                <> transaction_row.received_amount
    ) THEN
        RAISE EXCEPTION
            'Repayment transaction does not reconcile to allocation or date evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_allocations allocation_row
        JOIN repayment_transactions transaction_row
            ON transaction_row.id = allocation_row.repayment_transaction_id
        JOIN repayment_schedule_items item
            ON item.id = allocation_row.repayment_schedule_item_id
        WHERE item.repayment_schedule_id <> transaction_row.repayment_schedule_id
    ) THEN
        RAISE EXCEPTION
            'Repayment allocation does not belong to the transaction final schedule';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_schedule_items item
        LEFT JOIN (
            SELECT
                repayment_schedule_item_id,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'PRINCIPAL'
                ), 0) AS principal_paid,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'INTEREST'
                ), 0) AS interest_paid,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'FEE'
                ), 0) AS fee_paid
            FROM repayment_allocations
            GROUP BY repayment_schedule_item_id
        ) allocated
            ON allocated.repayment_schedule_item_id = item.id
        LEFT JOIN repayment_installment_progress progress
            ON progress.repayment_schedule_item_id = item.id
        WHERE progress.repayment_schedule_item_id IS NULL
           OR COALESCE(allocated.principal_paid, 0) > item.principal_due
           OR COALESCE(allocated.interest_paid, 0) > item.interest_due
           OR COALESCE(allocated.fee_paid, 0) > item.fee_due
           OR progress.principal_paid
                <> COALESCE(allocated.principal_paid, 0)
           OR progress.interest_paid
                <> COALESCE(allocated.interest_paid, 0)
           OR progress.fee_paid <> COALESCE(allocated.fee_paid, 0)
           OR progress.principal_outstanding
                <> item.principal_due - COALESCE(allocated.principal_paid, 0)
           OR progress.interest_outstanding
                <> item.interest_due - COALESCE(allocated.interest_paid, 0)
           OR progress.fee_outstanding
                <> item.fee_due - COALESCE(allocated.fee_paid, 0)
           OR progress.total_paid
                <> COALESCE(allocated.principal_paid, 0)
                    + COALESCE(allocated.interest_paid, 0)
                    + COALESCE(allocated.fee_paid, 0)
           OR progress.total_outstanding
                <> item.total_due
                    - COALESCE(allocated.principal_paid, 0)
                    - COALESCE(allocated.interest_paid, 0)
                    - COALESCE(allocated.fee_paid, 0)
           OR progress.status <> CASE
                WHEN progress.total_outstanding = 0 THEN 'PAID'
                WHEN item.due_date < progress.servicing_evaluation_date
                    THEN 'OVERDUE'
                WHEN progress.total_paid > 0 THEN 'PARTIALLY_PAID'
                WHEN item.due_date = progress.servicing_evaluation_date
                    THEN 'DUE'
                ELSE 'NOT_DUE'
              END
           OR progress.last_payment_value_date IS DISTINCT FROM (
                SELECT MAX(transaction_for_item.payment_value_date)
                FROM repayment_allocations allocation_for_item
                JOIN repayment_transactions transaction_for_item
                    ON transaction_for_item.id
                        = allocation_for_item.repayment_transaction_id
                WHERE allocation_for_item.repayment_schedule_item_id = item.id
              )
           OR progress.last_payment_recorded_at IS DISTINCT FROM (
                SELECT MAX(transaction_for_item.recorded_at)
                FROM repayment_allocations allocation_for_item
                JOIN repayment_transactions transaction_for_item
                    ON transaction_for_item.id
                        = allocation_for_item.repayment_transaction_id
                WHERE allocation_for_item.repayment_schedule_item_id = item.id
              )
    ) THEN
        RAISE EXCEPTION
            'Repayment installment progress does not reconcile to immutable schedule and allocations';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        LEFT JOIN (
            SELECT
                loan_account_id,
                COUNT(*) AS installment_count,
                COALESCE(SUM(principal_paid), 0) AS principal_paid,
                COALESCE(SUM(interest_paid), 0) AS interest_paid,
                COALESCE(SUM(fee_paid), 0) AS fee_paid,
                COALESCE(SUM(total_paid), 0) AS total_paid,
                COALESCE(SUM(principal_outstanding), 0)
                    AS principal_outstanding,
                COALESCE(SUM(interest_outstanding), 0)
                    AS interest_outstanding,
                COALESCE(SUM(fee_outstanding), 0) AS fee_outstanding,
                COALESCE(SUM(total_outstanding), 0) AS total_outstanding,
                COUNT(*) FILTER (WHERE status = 'OVERDUE') AS overdue_count,
                MAX(servicing_evaluation_date) AS maximum_evaluation_date,
                MIN(servicing_evaluation_date) AS minimum_evaluation_date
            FROM repayment_installment_progress
            GROUP BY loan_account_id
        ) progress_totals
            ON progress_totals.loan_account_id = account_row.id
        WHERE COALESCE(progress_totals.installment_count, 0)
                <> account_row.approved_term_months
           OR progress_totals.principal_paid <> account_row.principal_paid
           OR progress_totals.interest_paid <> account_row.interest_paid
           OR progress_totals.fee_paid <> account_row.fee_paid
           OR progress_totals.total_paid <> account_row.total_paid
           OR progress_totals.principal_outstanding
                <> account_row.principal_outstanding
           OR progress_totals.interest_outstanding
                <> account_row.interest_outstanding
           OR progress_totals.fee_outstanding
                <> account_row.fee_outstanding
           OR progress_totals.total_outstanding
                <> account_row.total_outstanding
           OR progress_totals.minimum_evaluation_date
                <> progress_totals.maximum_evaluation_date
           OR account_row.servicing_evaluation_date
                <> progress_totals.maximum_evaluation_date
           OR account_row.status <> CASE
                WHEN account_row.total_outstanding = 0 THEN 'SETTLED'
                WHEN progress_totals.overdue_count > 0 THEN 'OVERDUE'
                ELSE 'ACTIVE'
              END
           OR account_row.last_payment_value_date IS DISTINCT FROM (
                SELECT MAX(transaction_row.payment_value_date)
                FROM repayment_transactions transaction_row
                WHERE transaction_row.loan_account_id = account_row.id
              )
           OR account_row.last_payment_recorded_at IS DISTINCT FROM (
                SELECT MAX(transaction_row.recorded_at)
                FROM repayment_transactions transaction_row
                WHERE transaction_row.loan_account_id = account_row.id
              )
    ) THEN
        RAISE EXCEPTION
            'LoanAccount servicing rollup does not reconcile to installment progress';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_transactions transaction_row
        LEFT JOIN (
            SELECT
                repayment_transaction_id,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'PRINCIPAL'
                ), 0) AS principal_allocated
            FROM repayment_allocations
            GROUP BY repayment_transaction_id
        ) principal
            ON principal.repayment_transaction_id = transaction_row.id
        LEFT JOIN salary_advance_limit_movements release
            ON release.repayment_transaction_id = transaction_row.id
           AND release.movement_type = 'REPAID_RELEASED'
        LEFT JOIN salary_advance_limit_movements conversion
            ON conversion.loan_application_id
                = transaction_row.loan_application_id
           AND conversion.loan_account_id = transaction_row.loan_account_id
           AND conversion.movement_type = 'DISBURSED_TO_USED'
        WHERE (
                COALESCE(principal.principal_allocated, 0) = 0
                AND release.id IS NOT NULL
              )
           OR (
                COALESCE(principal.principal_allocated, 0) > 0
                AND (
                    release.id IS NULL
                    OR release.amount <> principal.principal_allocated
                    OR release.loan_application_id
                        <> transaction_row.loan_application_id
                    OR release.loan_account_id <> transaction_row.loan_account_id
                    OR conversion.id IS NULL
                    OR release.salary_advance_limit_id
                        <> conversion.salary_advance_limit_id
                )
              )
    ) THEN
        RAISE EXCEPTION
            'Salary Advance repayment release does not equal principal allocation';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements movement
        WHERE movement.movement_type = 'REPAID_RELEASED'
          AND NOT EXISTS (
              SELECT 1
              FROM repayment_transactions transaction_row
              WHERE transaction_row.id = movement.repayment_transaction_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements movement
        GROUP BY movement.salary_advance_limit_id
        HAVING COALESCE(SUM(movement.amount) FILTER (
                    WHERE movement.movement_type = 'REPAID_RELEASED'
               ), 0)
            > COALESCE(SUM(movement.amount) FILTER (
                    WHERE movement.movement_type = 'DISBURSED_TO_USED'
              ), 0)
    ) THEN
        RAISE EXCEPTION
            'Salary Advance repayment release exceeds converted principal';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_status_history()
RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        LEFT JOIN (
            SELECT
                loan_account_id,
                COUNT(*) AS transition_count,
                MIN(sequence_number) AS minimum_sequence,
                MAX(sequence_number) AS maximum_sequence,
                MAX(to_status) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_status,
                MAX(servicing_evaluation_date) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_evaluation_date,
                COUNT(*) FILTER (
                    WHERE sequence_number = 1 AND from_status IS NULL
                ) AS initial_count,
                COUNT(*) FILTER (
                    WHERE sequence_number > 1
                      AND from_status IS DISTINCT FROM previous_to_status
                ) AS broken_chain_count
            FROM (
                SELECT
                    transition_row.*,
                    MAX(sequence_number) OVER (
                        PARTITION BY loan_account_id
                    ) AS maximum_for_owner,
                    LAG(to_status) OVER (
                        PARTITION BY loan_account_id
                        ORDER BY sequence_number
                    ) AS previous_to_status
                FROM loan_account_status_transitions transition_row
            ) ordered_transitions
            GROUP BY loan_account_id
        ) history ON history.loan_account_id = account_row.id
        WHERE COALESCE(history.transition_count, 0) = 0
           OR history.minimum_sequence <> 1
           OR history.maximum_sequence <> history.transition_count
           OR history.initial_count <> 1
           OR history.broken_chain_count <> 0
           OR history.latest_status <> account_row.status
           OR history.latest_evaluation_date
                > account_row.servicing_evaluation_date
    ) THEN
        RAISE EXCEPTION
            'LoanAccount status transition history is incomplete or inconsistent';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_installment_progress progress
        LEFT JOIN (
            SELECT
                repayment_schedule_item_id,
                COUNT(*) AS transition_count,
                MIN(sequence_number) AS minimum_sequence,
                MAX(sequence_number) AS maximum_sequence,
                MAX(to_status) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_status,
                MAX(servicing_evaluation_date) FILTER (
                    WHERE sequence_number = maximum_for_owner
                ) AS latest_evaluation_date,
                COUNT(*) FILTER (
                    WHERE sequence_number = 1 AND from_status IS NULL
                ) AS initial_count,
                COUNT(*) FILTER (
                    WHERE sequence_number > 1
                      AND from_status IS DISTINCT FROM previous_to_status
                ) AS broken_chain_count
            FROM (
                SELECT
                    transition_row.*,
                    MAX(sequence_number) OVER (
                        PARTITION BY repayment_schedule_item_id
                    ) AS maximum_for_owner,
                    LAG(to_status) OVER (
                        PARTITION BY repayment_schedule_item_id
                        ORDER BY sequence_number
                    ) AS previous_to_status
                FROM repayment_installment_status_transitions transition_row
            ) ordered_transitions
            GROUP BY repayment_schedule_item_id
        ) history
            ON history.repayment_schedule_item_id
                = progress.repayment_schedule_item_id
        WHERE COALESCE(history.transition_count, 0) = 0
           OR history.minimum_sequence <> 1
           OR history.maximum_sequence <> history.transition_count
           OR history.initial_count <> 1
           OR history.broken_chain_count <> 0
           OR history.latest_status <> progress.status
           OR history.latest_evaluation_date
                > progress.servicing_evaluation_date
    ) THEN
        RAISE EXCEPTION
            'Installment status transition history is incomplete or inconsistent';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_transaction
    AFTER INSERT OR UPDATE OR DELETE ON repayment_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_allocation
    AFTER INSERT OR UPDATE OR DELETE ON repayment_allocations
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_progress
    AFTER INSERT OR UPDATE OR DELETE ON repayment_installment_progress
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_account
    AFTER INSERT OR UPDATE OR DELETE ON loan_accounts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_reconcile_release
    AFTER INSERT OR UPDATE OR DELETE ON salary_advance_limit_movements
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_servicing_reconciliation();

CREATE CONSTRAINT TRIGGER trg_repayment_history_account
    AFTER INSERT OR UPDATE OR DELETE ON loan_accounts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();

CREATE CONSTRAINT TRIGGER trg_repayment_history_account_transition
    AFTER INSERT OR UPDATE OR DELETE ON loan_account_status_transitions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();

CREATE CONSTRAINT TRIGGER trg_repayment_history_progress
    AFTER INSERT OR UPDATE OR DELETE ON repayment_installment_progress
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();

CREATE CONSTRAINT TRIGGER trg_repayment_history_installment_transition
    AFTER INSERT OR UPDATE OR DELETE
    ON repayment_installment_status_transitions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_status_history();
