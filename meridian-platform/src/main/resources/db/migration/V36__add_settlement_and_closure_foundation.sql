DO $$
DECLARE
    required_role_count INTEGER;
    required_function_count INTEGER;
BEGIN
    IF to_regclass(current_schema() || '.approved_loan_settlements') IS NOT NULL
            OR to_regclass(current_schema() || '.loan_account_closures') IS NOT NULL THEN
        RAISE EXCEPTION
            'V36 preflight failed: settlement or closure evidence table already exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'repayment_transactions'
          AND column_name = 'transaction_type'
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: repayment transaction type already exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts
        WHERE status = 'CLOSED'
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: CLOSED LoanAccounts predate closure evidence';
    END IF;

    SELECT COUNT(*) INTO required_role_count
    FROM roles
    WHERE code IN ('APPROVER', 'ACCOUNTING_OFFICER');
    IF required_role_count <> 2 THEN
        RAISE EXCEPTION
            'V36 preflight failed: required settlement and closure roles are missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM permissions
        WHERE code IN ('loan:settlement:approve', 'loan:account:close')
           OR id IN (
                '00000000-0000-0000-0000-000000000244'::UUID,
                '00000000-0000-0000-0000-000000000245'::UUID
           )
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: settlement or closure permission already exists';
    END IF;

    IF to_regclass(current_schema() || '.repayment_operation_outcomes') IS NULL
            OR to_regclass(current_schema() || '.loan_account_status_transitions') IS NULL
            OR to_regclass(current_schema()
                || '.repayment_installment_status_transitions') IS NULL THEN
        RAISE EXCEPTION
            'V36 preflight failed: V33-V35 servicing foundation is incomplete';
    END IF;

    SELECT COUNT(*) INTO required_function_count
    FROM pg_proc procedure_row
    JOIN pg_namespace namespace
      ON namespace.oid = procedure_row.pronamespace
    WHERE namespace.nspname = current_schema()
      AND procedure_row.proname IN (
          'reject_immutable_history_row_mutation',
          'validate_repayment_servicing_reconciliation',
          'validate_repayment_operation_outcome_evidence'
      );
    IF required_function_count <> 3 THEN
        RAISE EXCEPTION
            'V36 preflight failed: required servicing reconciliation functions are missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        JOIN pg_class relation ON relation.oid = constraint_row.conrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = current_schema()
          AND relation.relname = 'loan_accounts'
          AND constraint_row.conname = 'chk_loan_accounts_settlement_balance'
          AND pg_get_expr(
                constraint_row.conbin,
                constraint_row.conrelid,
                true
              ) ~ 'status.*CLOSED'
    ) THEN
        RAISE EXCEPTION
            'V36 preflight failed: LoanAccount settlement predicate does not match V35';
    END IF;
END;
$$;

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000244',
     'loan:settlement:approve',
     'Approve and apply an administrative full-balance settlement'),
    ('00000000-0000-0000-0000-000000000245',
     'loan:account:close',
     'Administratively close an eligible settled Loan Account');

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code = 'loan:settlement:approve'
WHERE role.code = 'APPROVER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code = 'loan:account:close'
WHERE role.code = 'ACCOUNTING_OFFICER';

ALTER TABLE repayment_transactions
    ADD COLUMN transaction_type VARCHAR(30);

UPDATE repayment_transactions
SET transaction_type = 'REPAYMENT';

ALTER TABLE repayment_transactions
    ALTER COLUMN transaction_type SET NOT NULL,
    ADD CONSTRAINT chk_repayment_transactions_type CHECK (
        transaction_type IN ('REPAYMENT', 'APPROVED_SETTLEMENT')
    );

CREATE TABLE approved_loan_settlements (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    repayment_transaction_id UUID NOT NULL,
    request_id UUID NOT NULL,
    settlement_amount NUMERIC(19,2) NOT NULL,
    approved_by_user_id UUID NOT NULL,
    approved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_approved_loan_settlement_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_approved_loan_settlement_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_accounts (id),
    CONSTRAINT fk_approved_loan_settlement_transaction
        FOREIGN KEY (repayment_transaction_id) REFERENCES repayment_transactions (id),
    CONSTRAINT fk_approved_loan_settlement_actor
        FOREIGN KEY (approved_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_approved_loan_settlement_account UNIQUE (loan_account_id),
    CONSTRAINT uq_approved_loan_settlement_transaction
        UNIQUE (repayment_transaction_id),
    CONSTRAINT uq_approved_loan_settlement_request UNIQUE (request_id),
    CONSTRAINT chk_approved_loan_settlement_amount CHECK (
        settlement_amount > 0
        AND settlement_amount = trunc(settlement_amount)
    )
);

CREATE INDEX idx_approved_loan_settlements_application
    ON approved_loan_settlements (loan_application_id, approved_at);

CREATE TABLE loan_account_closures (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    request_id UUID NOT NULL,
    closed_by_user_id UUID NOT NULL,
    closed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_account_closure_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_account_closure_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_accounts (id),
    CONSTRAINT fk_loan_account_closure_actor
        FOREIGN KEY (closed_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_loan_account_closure_application UNIQUE (loan_application_id),
    CONSTRAINT uq_loan_account_closure_account UNIQUE (loan_account_id),
    CONSTRAINT uq_loan_account_closure_request UNIQUE (request_id)
);

CREATE TRIGGER trg_approved_loan_settlements_immutable
BEFORE UPDATE OR DELETE ON approved_loan_settlements
FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TRIGGER trg_loan_account_closures_immutable
BEFORE UPDATE OR DELETE ON loan_account_closures
FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

ALTER TABLE loan_accounts
    DROP CONSTRAINT chk_loan_accounts_settlement_balance,
    ADD CONSTRAINT chk_loan_accounts_settlement_balance CHECK (
        (status IN ('SETTLED', 'CLOSED') AND total_outstanding = 0)
        OR (status IN ('ACTIVE', 'OVERDUE') AND total_outstanding > 0)
    );

ALTER TABLE loan_account_status_transitions
    DROP CONSTRAINT chk_loan_account_status_history_statuses,
    DROP CONSTRAINT chk_loan_account_status_history_action,
    ADD CONSTRAINT chk_loan_account_status_history_statuses CHECK (
        (from_status IS NULL
            OR from_status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED'))
        AND to_status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED')
        AND from_status IS DISTINCT FROM to_status
    ),
    ADD CONSTRAINT chk_loan_account_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED',
            'APPROVED_SETTLEMENT',
            'ADMINISTRATIVE_CLOSURE'
        )
    );

ALTER TABLE repayment_installment_status_transitions
    DROP CONSTRAINT chk_repayment_installment_status_history_action,
    ADD CONSTRAINT chk_repayment_installment_status_history_action CHECK (
        action IN (
            'ACTIVATION_INITIALIZED',
            'REPAYMENT_RECORDED',
            'OVERDUE_EVALUATED',
            'APPROVED_SETTLEMENT'
        )
    );

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action,
    DROP CONSTRAINT chk_audit_events_entity_type,
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED',
        'CUSTOMER_PROFILE_COMPLETED', 'CUSTOMER_BANK_ACCOUNT_ADDED',
        'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
        'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED',
        'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED',
        'RESERVATION_RELEASED', 'DOCUMENT_CHECKLIST_CREATED',
        'DOCUMENT_CHECKLIST_ITEM_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED',
        'DOCUMENT_REPLACEMENT_REQUESTED', 'DOCUMENT_UPLOADS_COMPLETED',
        'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED',
        'CORRECTION_RESUBMITTED', 'SALARY_ADVANCE_REVALIDATED',
        'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED', 'LOAN_ACCOUNT_STATUS_CHANGED',
        'LOAN_SETTLEMENT_APPROVED', 'LOAN_ACCOUNT_CLOSED'
    )),
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION',
        'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT',
        'REPAYMENT_TRANSACTION', 'LOAN_ACCOUNT',
        'LOAN_SETTLEMENT', 'LOAN_ACCOUNT_CLOSURE'
    ));

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
        LEFT JOIN loan_account_closures closure_row
            ON closure_row.loan_account_id = account_row.id
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
                WHEN closure_row.loan_account_id IS NOT NULL THEN 'CLOSED'
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

CREATE OR REPLACE FUNCTION validate_repayment_operation_outcome_evidence(
    transaction_id_to_validate UUID
)
RETURNS VOID AS $$
DECLARE
    transaction_row repayment_transactions%ROWTYPE;
    outcome_row repayment_operation_outcomes%ROWTYPE;
    outcome_count INTEGER;
    principal_total NUMERIC(19,2);
    release_count INTEGER;
    release_total NUMERIC(19,2);
    repayment_audit_count INTEGER;
    correct_repayment_audit_count INTEGER;
    account_audit_count INTEGER;
    correct_account_audit_count INTEGER;
    account_transition_count INTEGER;
    correct_account_transition_count INTEGER;
    schedule_item_count INTEGER;
    settlement_row approved_loan_settlements%ROWTYPE;
    expected_operation_action VARCHAR(40);
    expected_operation_audit_action VARCHAR(80);
    expected_operation_entity_type VARCHAR(80);
    expected_operation_entity_id UUID;
BEGIN
    SELECT COUNT(*) INTO outcome_count
    FROM repayment_operation_outcomes
    WHERE repayment_transaction_id = transaction_id_to_validate;
    IF outcome_count <> 1 THEN
        RAISE EXCEPTION 'Repayment transaction requires exactly one operation outcome';
    END IF;

    SELECT * INTO outcome_row
    FROM repayment_operation_outcomes
    WHERE repayment_transaction_id = transaction_id_to_validate;

    SELECT * INTO transaction_row
    FROM repayment_transactions
    WHERE id = transaction_id_to_validate;
    IF NOT FOUND
            OR transaction_row.loan_application_id <> outcome_row.loan_application_id
            OR transaction_row.loan_account_id <> outcome_row.loan_account_id
            OR transaction_row.repayment_schedule_id <> outcome_row.repayment_schedule_id
            OR transaction_row.received_amount <> outcome_row.received_amount
            OR transaction_row.payment_value_date <> outcome_row.payment_value_date
            OR transaction_row.recorded_at <> outcome_row.recorded_at THEN
        RAISE EXCEPTION 'Repayment outcome identity conflicts with transaction evidence';
    END IF;

    IF transaction_row.transaction_type = 'APPROVED_SETTLEMENT' THEN
        SELECT * INTO settlement_row
        FROM approved_loan_settlements
        WHERE repayment_transaction_id = transaction_id_to_validate;

        IF NOT FOUND
                OR settlement_row.loan_application_id
                    <> transaction_row.loan_application_id
                OR settlement_row.loan_account_id <> transaction_row.loan_account_id
                OR settlement_row.request_id <> transaction_row.request_id
                OR settlement_row.settlement_amount <> transaction_row.received_amount
                OR settlement_row.approved_by_user_id
                    <> transaction_row.recorded_by_user_id
                OR settlement_row.approved_at <> transaction_row.recorded_at
                OR outcome_row.account_status <> 'SETTLED'
                OR NOT outcome_row.account_status_changed THEN
            RAISE EXCEPTION
                'Approved settlement identity conflicts with payment or outcome evidence';
        END IF;

        expected_operation_action := 'APPROVED_SETTLEMENT';
        expected_operation_audit_action := 'LOAN_SETTLEMENT_APPROVED';
        expected_operation_entity_type := 'LOAN_SETTLEMENT';
        expected_operation_entity_id := settlement_row.id;
    ELSE
        IF transaction_row.transaction_type <> 'REPAYMENT'
                OR EXISTS (
                    SELECT 1
                    FROM approved_loan_settlements settlement
                    WHERE settlement.repayment_transaction_id
                        = transaction_id_to_validate
                ) THEN
            RAISE EXCEPTION
                'Repayment transaction type conflicts with settlement evidence';
        END IF;

        expected_operation_action := 'REPAYMENT_RECORDED';
        expected_operation_audit_action := 'REPAYMENT_RECORDED';
        expected_operation_entity_type := 'REPAYMENT_TRANSACTION';
        expected_operation_entity_id := transaction_id_to_validate;
    END IF;

    SELECT COALESCE(SUM(amount), 0) INTO principal_total
    FROM repayment_allocations
    WHERE repayment_transaction_id = transaction_id_to_validate
      AND component = 'PRINCIPAL';
    IF principal_total <> outcome_row.principal_released THEN
        RAISE EXCEPTION 'Repayment outcome principal conflicts with allocations';
    END IF;

    SELECT COUNT(*), COALESCE(SUM(amount), 0)
    INTO release_count, release_total
    FROM salary_advance_limit_movements
    WHERE repayment_transaction_id = transaction_id_to_validate
      AND movement_type = 'REPAID_RELEASED';
    IF (principal_total = 0 AND release_count <> 0)
            OR (principal_total > 0
                AND (release_count <> 1 OR release_total <> principal_total)) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with exposure release evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = expected_operation_entity_type
          AND entity_id = expected_operation_entity_id
          AND actor_user_id = transaction_row.recorded_by_user_id
    )
    INTO repayment_audit_count, correct_repayment_audit_count
    FROM audit_events
    WHERE operation_id = transaction_id_to_validate
      AND action = expected_operation_audit_action;
    IF repayment_audit_count <> 1 OR correct_repayment_audit_count <> 1 THEN
        RAISE EXCEPTION 'Repayment outcome requires exact repayment audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_ACCOUNT'
          AND entity_id = outcome_row.loan_account_id
    )
    INTO account_audit_count, correct_account_audit_count
    FROM audit_events
    WHERE operation_id = transaction_id_to_validate
      AND action = 'LOAN_ACCOUNT_STATUS_CHANGED';
    IF account_audit_count <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END)
            OR correct_account_audit_count
                <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with account audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE loan_account_id = outcome_row.loan_account_id
          AND action = expected_operation_action
          AND actor_type = 'USER'
          AND actor_user_id = transaction_row.recorded_by_user_id
          AND occurred_at = transaction_row.recorded_at
          AND to_status = outcome_row.account_status
    )
    INTO account_transition_count, correct_account_transition_count
    FROM loan_account_status_transitions
    WHERE operation_id = transaction_id_to_validate;
    IF account_transition_count
            <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END)
            OR correct_account_transition_count
                <> (CASE WHEN outcome_row.account_status_changed THEN 1 ELSE 0 END) THEN
        RAISE EXCEPTION 'Repayment outcome conflicts with account transition evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(outcome_row.outcome_json -> 'installments') item
        LEFT JOIN repayment_installment_status_transitions transition_row
          ON transition_row.operation_id = transaction_id_to_validate
         AND transition_row.repayment_schedule_item_id =
                (item -> 'progress' ->> 'repaymentScheduleItemId')::UUID
        WHERE (
            (item ->> 'statusChanged')::BOOLEAN
            AND (
                transition_row.id IS NULL
                OR transition_row.from_status <> item ->> 'previousStatus'
                OR transition_row.to_status <> item -> 'progress' ->> 'status'
                OR transition_row.action <> expected_operation_action
                OR transition_row.actor_type <> 'USER'
                OR transition_row.actor_user_id
                    <> transaction_row.recorded_by_user_id
                OR transition_row.occurred_at <> transaction_row.recorded_at
            )
        ) OR (
            NOT (item ->> 'statusChanged')::BOOLEAN
            AND transition_row.id IS NOT NULL
        )
    ) OR EXISTS (
        SELECT 1
        FROM repayment_installment_status_transitions transition_row
        WHERE transition_row.operation_id = transaction_id_to_validate
          AND NOT EXISTS (
              SELECT 1
              FROM jsonb_array_elements(
                  outcome_row.outcome_json -> 'installments'
              ) item
              WHERE (item -> 'progress' ->> 'repaymentScheduleItemId')::UUID
                    = transition_row.repayment_schedule_item_id
          )
    ) THEN
        RAISE EXCEPTION
            'Repayment outcome conflicts with item-specific installment transition evidence';
    END IF;

    SELECT COUNT(*) INTO schedule_item_count
    FROM repayment_schedule_items
    WHERE repayment_schedule_id = outcome_row.repayment_schedule_id;
    IF jsonb_array_length(outcome_row.outcome_json -> 'installments')
                <> schedule_item_count
            OR (outcome_row.outcome_json ->> 'repaymentTransactionId')::UUID
                <> outcome_row.repayment_transaction_id
            OR (outcome_row.outcome_json ->> 'loanApplicationId')::UUID
                <> outcome_row.loan_application_id
            OR (outcome_row.outcome_json ->> 'loanAccountId')::UUID
                <> outcome_row.loan_account_id
            OR (outcome_row.outcome_json ->> 'repaymentScheduleId')::UUID
                <> outcome_row.repayment_schedule_id
            OR (outcome_row.outcome_json ->> 'receivedAmount')::NUMERIC
                <> outcome_row.received_amount
            OR (outcome_row.outcome_json ->> 'paymentValueDate')::DATE
                <> outcome_row.payment_value_date
            OR (outcome_row.outcome_json ->> 'recordedAt')::TIMESTAMP
                <> outcome_row.recorded_at
            OR (outcome_row.outcome_json ->> 'principalReleased')::NUMERIC
                <> outcome_row.principal_released
            OR outcome_row.outcome_json ->> 'accountStatus'
                <> outcome_row.account_status
            OR (outcome_row.outcome_json ->> 'accountStatusChanged')::BOOLEAN
                <> outcome_row.account_status_changed THEN
        RAISE EXCEPTION 'Repayment outcome JSON conflicts with typed evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_accounts account_row
        WHERE account_row.id = outcome_row.loan_account_id
          AND (
              account_row.status <> outcome_row.account_status
              OR account_row.principal_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'principalPaid')::NUMERIC
              OR account_row.interest_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'interestPaid')::NUMERIC
              OR account_row.fee_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'feePaid')::NUMERIC
              OR account_row.total_paid <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'totalPaid')::NUMERIC
              OR account_row.principal_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'principalOutstanding')::NUMERIC
              OR account_row.interest_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'interestOutstanding')::NUMERIC
              OR account_row.fee_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'feeOutstanding')::NUMERIC
              OR account_row.total_outstanding <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'totalOutstanding')::NUMERIC
              OR account_row.last_payment_value_date IS DISTINCT FROM
                    NULLIF(outcome_row.outcome_json -> 'accountBalance'
                        ->> 'lastPaymentValueDate', '')::DATE
              OR account_row.last_payment_recorded_at IS DISTINCT FROM
                    NULLIF(outcome_row.outcome_json -> 'accountBalance'
                        ->> 'lastPaymentRecordedAt', '')::TIMESTAMP
              OR account_row.servicing_evaluation_date <>
                    (outcome_row.outcome_json -> 'accountBalance'
                        ->> 'servicingEvaluationDate')::DATE
          )
    ) THEN
        RAISE EXCEPTION 'Repayment outcome account balance is inconsistent';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(outcome_row.outcome_json -> 'installments')
            WITH ORDINALITY AS outcome_item(value, ordinality)
        LEFT JOIN repayment_installment_progress progress_row
          ON progress_row.repayment_schedule_item_id =
                (outcome_item.value -> 'progress'
                    ->> 'repaymentScheduleItemId')::UUID
        LEFT JOIN repayment_schedule_items schedule_item
          ON schedule_item.id = progress_row.repayment_schedule_item_id
        WHERE progress_row.repayment_schedule_item_id IS NULL
           OR progress_row.repayment_schedule_id <>
                (outcome_item.value -> 'progress'
                    ->> 'repaymentScheduleId')::UUID
           OR progress_row.repayment_schedule_id <> outcome_row.repayment_schedule_id
           OR progress_row.loan_account_id <>
                (outcome_item.value -> 'progress' ->> 'loanAccountId')::UUID
           OR progress_row.loan_account_id <> outcome_row.loan_account_id
           OR schedule_item.repayment_schedule_id <> outcome_row.repayment_schedule_id
           OR progress_row.installment_number <>
                (outcome_item.value -> 'progress' ->> 'installmentNumber')::INTEGER
           OR progress_row.installment_number <> outcome_item.ordinality
           OR progress_row.principal_paid <>
                (outcome_item.value -> 'progress' ->> 'principalPaid')::NUMERIC
           OR progress_row.interest_paid <>
                (outcome_item.value -> 'progress' ->> 'interestPaid')::NUMERIC
           OR progress_row.fee_paid <>
                (outcome_item.value -> 'progress' ->> 'feePaid')::NUMERIC
           OR progress_row.total_paid <>
                (outcome_item.value -> 'progress' ->> 'totalPaid')::NUMERIC
           OR progress_row.principal_outstanding <>
                (outcome_item.value -> 'progress'
                    ->> 'principalOutstanding')::NUMERIC
           OR progress_row.interest_outstanding <>
                (outcome_item.value -> 'progress'
                    ->> 'interestOutstanding')::NUMERIC
           OR progress_row.fee_outstanding <>
                (outcome_item.value -> 'progress' ->> 'feeOutstanding')::NUMERIC
           OR progress_row.total_outstanding <>
                (outcome_item.value -> 'progress' ->> 'totalOutstanding')::NUMERIC
           OR progress_row.status <>
                outcome_item.value -> 'progress' ->> 'status'
           OR progress_row.last_payment_value_date IS DISTINCT FROM
                NULLIF(outcome_item.value -> 'progress'
                    ->> 'lastPaymentValueDate', '')::DATE
           OR progress_row.last_payment_recorded_at IS DISTINCT FROM
                NULLIF(outcome_item.value -> 'progress'
                    ->> 'lastPaymentRecordedAt', '')::TIMESTAMP
           OR progress_row.servicing_evaluation_date <>
                (outcome_item.value -> 'progress'
                    ->> 'servicingEvaluationDate')::DATE
           OR progress_row.updated_at <>
                (outcome_item.value -> 'progress' ->> 'updatedAt')::TIMESTAMP
    ) THEN
        RAISE EXCEPTION 'Repayment outcome installment progress is inconsistent';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_approved_loan_settlement()
RETURNS trigger AS $$
BEGIN
    PERFORM validate_repayment_operation_outcome_evidence(
        COALESCE(NEW.repayment_transaction_id, OLD.repayment_transaction_id)
    );
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_approved_loan_settlement_reconcile
AFTER INSERT ON approved_loan_settlements
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_approved_loan_settlement();

CREATE OR REPLACE FUNCTION validate_loan_account_closure_evidence()
RETURNS trigger AS $$
DECLARE
    closure_row loan_account_closures%ROWTYPE;
    account_row loan_accounts%ROWTYPE;
    settled_transition loan_account_status_transitions%ROWTYPE;
    closure_transition_count INTEGER;
    correct_closure_transition_count INTEGER;
    closure_audit_count INTEGER;
    correct_closure_audit_count INTEGER;
    account_audit_count INTEGER;
    correct_account_audit_count INTEGER;
BEGIN
    SELECT * INTO closure_row
    FROM loan_account_closures
    WHERE id = COALESCE(NEW.id, OLD.id);

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT * INTO account_row
    FROM loan_accounts
    WHERE id = closure_row.loan_account_id;

    IF NOT FOUND
            OR account_row.loan_application_id <> closure_row.loan_application_id
            OR account_row.status <> 'CLOSED'
            OR account_row.total_outstanding <> 0
            OR account_row.updated_at <> closure_row.closed_at THEN
        RAISE EXCEPTION
            'LoanAccount closure evidence conflicts with the administrative account state';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_installment_progress progress
        WHERE progress.loan_account_id = closure_row.loan_account_id
          AND (
              progress.status <> 'PAID'
              OR progress.principal_outstanding <> 0
              OR progress.interest_outstanding <> 0
              OR progress.fee_outstanding <> 0
              OR progress.total_outstanding <> 0
          )
    ) THEN
        RAISE EXCEPTION
            'LoanAccount closure requires fully paid installment servicing progress';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE loan_account_id = closure_row.loan_account_id
          AND from_status = 'SETTLED'
          AND to_status = 'CLOSED'
          AND action = 'ADMINISTRATIVE_CLOSURE'
          AND actor_type = 'USER'
          AND actor_user_id = closure_row.closed_by_user_id
          AND servicing_evaluation_date = account_row.servicing_evaluation_date
          AND occurred_at = closure_row.closed_at
    )
    INTO closure_transition_count, correct_closure_transition_count
    FROM loan_account_status_transitions
    WHERE operation_id = closure_row.id;

    IF closure_transition_count <> 1 OR correct_closure_transition_count <> 1 THEN
        RAISE EXCEPTION
            'LoanAccount closure requires exact status transition evidence';
    END IF;

    SELECT transition_row.* INTO settled_transition
    FROM loan_account_status_transitions transition_row
    WHERE transition_row.loan_account_id = closure_row.loan_account_id
      AND transition_row.to_status = 'SETTLED'
      AND transition_row.sequence_number = (
          SELECT MAX(prior.sequence_number)
          FROM loan_account_status_transitions prior
          WHERE prior.loan_account_id = closure_row.loan_account_id
            AND prior.sequence_number < (
                SELECT sequence_number
                FROM loan_account_status_transitions
                WHERE operation_id = closure_row.id
            )
      );

    IF NOT FOUND
            OR settled_transition.action NOT IN (
                'REPAYMENT_RECORDED', 'APPROVED_SETTLEMENT'
            )
            OR NOT EXISTS (
                SELECT 1
                FROM repayment_operation_outcomes outcome_row
                WHERE outcome_row.repayment_transaction_id
                    = settled_transition.operation_id
                  AND outcome_row.loan_account_id = closure_row.loan_account_id
                  AND outcome_row.account_status = 'SETTLED'
                  AND outcome_row.account_status_changed
            )
            OR (
                settled_transition.action = 'APPROVED_SETTLEMENT'
                AND NOT EXISTS (
                    SELECT 1
                    FROM approved_loan_settlements settlement
                    WHERE settlement.repayment_transaction_id
                        = settled_transition.operation_id
                      AND settlement.loan_account_id
                        = closure_row.loan_account_id
                )
            ) THEN
        RAISE EXCEPTION
            'LoanAccount closure requires consistent contractual payoff or approved settlement evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_applications application_row
        JOIN loan_products product_row
          ON product_row.id = application_row.loan_product_id
        WHERE application_row.id = closure_row.loan_application_id
          AND product_row.product_code = 'SALARY_ADVANCE'
          AND (
              COALESCE((
                  SELECT SUM(movement.amount)
                  FROM salary_advance_limit_movements movement
                  WHERE movement.loan_account_id = closure_row.loan_account_id
                    AND movement.movement_type = 'DISBURSED_TO_USED'
              ), 0) <> account_row.approved_principal
              OR COALESCE((
                  SELECT SUM(movement.amount)
                  FROM salary_advance_limit_movements movement
                  WHERE movement.loan_account_id = closure_row.loan_account_id
                    AND movement.movement_type = 'REPAID_RELEASED'
              ), 0) <> account_row.approved_principal
          )
    ) THEN
        RAISE EXCEPTION
            'Salary Advance closure requires full principal exposure release';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_ACCOUNT_CLOSURE'
          AND entity_id = closure_row.id
          AND actor_user_id = closure_row.closed_by_user_id
    )
    INTO closure_audit_count, correct_closure_audit_count
    FROM audit_events
    WHERE operation_id = closure_row.id
      AND action = 'LOAN_ACCOUNT_CLOSED';

    IF closure_audit_count <> 1 OR correct_closure_audit_count <> 1 THEN
        RAISE EXCEPTION
            'LoanAccount closure requires exact administrative audit evidence';
    END IF;

    SELECT COUNT(*), COUNT(*) FILTER (
        WHERE entity_type = 'LOAN_ACCOUNT'
          AND entity_id = closure_row.loan_account_id
          AND actor_user_id = closure_row.closed_by_user_id
    )
    INTO account_audit_count, correct_account_audit_count
    FROM audit_events
    WHERE operation_id = closure_row.id
      AND action = 'LOAN_ACCOUNT_STATUS_CHANGED';

    IF account_audit_count <> 1 OR correct_account_audit_count <> 1 THEN
        RAISE EXCEPTION
            'LoanAccount closure requires exact account-status audit evidence';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_account_closure_reconcile
AFTER INSERT ON loan_account_closures
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_loan_account_closure_evidence();
