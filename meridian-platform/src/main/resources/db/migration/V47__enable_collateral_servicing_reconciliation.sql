-- Extend the common repayment, outcome, and closure reconciliation to Collateral Loan
-- without weakening the existing Salary Advance or UCL product semantics.

DO $$
DECLARE
    installed_trigger_count INTEGER;
BEGIN
    IF to_regprocedure('validate_repayment_servicing_reconciliation()') IS NULL
            OR to_regprocedure(
                'validate_repayment_operation_outcome_evidence(uuid)'
            ) IS NULL
            OR to_regprocedure('validate_loan_account_closure_evidence()') IS NULL THEN
        RAISE EXCEPTION
            'V47 preflight failed: product-aware servicing reconciliation is incomplete';
    END IF;

    SELECT COUNT(DISTINCT trigger_row.tgname)
    INTO installed_trigger_count
    FROM pg_trigger trigger_row
    JOIN pg_class table_row ON table_row.oid = trigger_row.tgrelid
    JOIN pg_namespace namespace_row ON namespace_row.oid = table_row.relnamespace
    WHERE namespace_row.nspname = current_schema()
      AND NOT trigger_row.tgisinternal
      AND trigger_row.tgname IN (
          'trg_repayment_reconcile_transaction',
          'trg_repayment_reconcile_allocation',
          'trg_repayment_reconcile_progress',
          'trg_repayment_reconcile_account',
          'trg_repayment_reconcile_release',
          'trg_repayment_operation_outcome_reconcile',
          'trg_repayment_operation_transaction_completeness',
          'trg_approved_loan_settlement_reconcile',
          'trg_loan_account_closure_reconcile'
      );

    IF installed_trigger_count <> 9 THEN
        RAISE EXCEPTION
            'V47 preflight failed: deferred servicing reconciliation triggers are incomplete';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM repayment_transactions transaction_row
        JOIN loan_applications application_row
          ON application_row.id = transaction_row.loan_application_id
        WHERE application_row.product_code = 'COLLATERAL_LOAN'
    ) OR EXISTS (
        SELECT 1
        FROM loan_account_closures closure_row
        JOIN loan_applications application_row
          ON application_row.id = closure_row.loan_application_id
        WHERE application_row.product_code = 'COLLATERAL_LOAN'
    ) OR EXISTS (
        SELECT 1
        FROM salary_advance_limit_movements movement_row
        JOIN loan_applications application_row
          ON application_row.id = movement_row.loan_application_id
        WHERE application_row.product_code = 'COLLATERAL_LOAN'
          AND movement_row.movement_type IN (
              'DISBURSED_TO_USED', 'REPAID_RELEASED'
          )
    ) THEN
        RAISE EXCEPTION
            'V47 preflight failed: incompatible Collateral servicing evidence exists';
    END IF;
END;
$$;

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
        JOIN loan_applications application_row
          ON application_row.id = transaction_row.loan_application_id
        LEFT JOIN (
            SELECT repayment_transaction_id,
                COALESCE(SUM(amount) FILTER (
                    WHERE component = 'PRINCIPAL'
                ), 0) AS principal_allocated
            FROM repayment_allocations
            GROUP BY repayment_transaction_id
        ) principal
          ON principal.repayment_transaction_id = transaction_row.id
        WHERE application_row.product_code NOT IN (
                  'SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN',
                  'COLLATERAL_LOAN'
              )
           OR (
                application_row.product_code IN (
                    'UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN'
                )
                AND EXISTS (
                    SELECT 1
                    FROM salary_advance_limit_movements release
                    WHERE release.repayment_transaction_id = transaction_row.id
                      AND release.movement_type = 'REPAID_RELEASED'
                )
              )
           OR (
                application_row.product_code = 'SALARY_ADVANCE'
                AND (
                    (
                        COALESCE(principal.principal_allocated, 0) = 0
                        AND EXISTS (
                            SELECT 1
                            FROM salary_advance_limit_movements release
                            WHERE release.repayment_transaction_id = transaction_row.id
                              AND release.movement_type = 'REPAID_RELEASED'
                        )
                    )
                    OR (
                        COALESCE(principal.principal_allocated, 0) > 0
                        AND (
                            (
                                SELECT COUNT(*)
                                FROM salary_advance_limit_movements release
                                WHERE release.repayment_transaction_id = transaction_row.id
                                  AND release.movement_type = 'REPAID_RELEASED'
                            ) <> 1
                            OR (
                                SELECT COALESCE(SUM(release.amount), 0)
                                FROM salary_advance_limit_movements release
                                WHERE release.repayment_transaction_id = transaction_row.id
                                  AND release.movement_type = 'REPAID_RELEASED'
                            ) <> principal.principal_allocated
                            OR NOT EXISTS (
                                SELECT 1
                                FROM salary_advance_limit_movements release
                                JOIN salary_advance_limit_movements conversion
                                  ON conversion.loan_application_id
                                        = transaction_row.loan_application_id
                                 AND conversion.loan_account_id
                                        = transaction_row.loan_account_id
                                 AND conversion.movement_type = 'DISBURSED_TO_USED'
                                 AND conversion.salary_advance_limit_id
                                        = release.salary_advance_limit_id
                                WHERE release.repayment_transaction_id = transaction_row.id
                                  AND release.movement_type = 'REPAID_RELEASED'
                                  AND release.loan_application_id
                                        = transaction_row.loan_application_id
                                  AND release.loan_account_id
                                        = transaction_row.loan_account_id
                            )
                        )
                    )
                )
              )
    ) THEN
        RAISE EXCEPTION
            'Product repayment exposure evidence does not reconcile';
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
    application_product_code VARCHAR(50);
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

    SELECT application_row.product_code INTO application_product_code
    FROM loan_applications application_row
    WHERE application_row.id = transaction_row.loan_application_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Repayment outcome product identity is unavailable';
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

    SELECT COUNT(*), COALESCE(SUM(amount), 0)
    INTO release_count, release_total
    FROM salary_advance_limit_movements
    WHERE repayment_transaction_id = transaction_id_to_validate
      AND movement_type = 'REPAID_RELEASED';

    IF application_product_code = 'SALARY_ADVANCE' THEN
        IF principal_total <> outcome_row.principal_released
                OR (principal_total = 0 AND release_count <> 0)
                OR (principal_total > 0
                    AND (release_count <> 1 OR release_total <> principal_total)) THEN
            RAISE EXCEPTION
                'Salary Advance repayment outcome conflicts with exposure release evidence';
        END IF;
    ELSIF application_product_code = 'UNSECURED_CONSUMER_LOAN' THEN
        IF outcome_row.principal_released <> 0 OR release_count <> 0 THEN
            RAISE EXCEPTION
                'Unsecured Consumer Loan repayment must not release product exposure';
        END IF;
    ELSIF application_product_code = 'COLLATERAL_LOAN' THEN
        IF outcome_row.principal_released <> 0 OR release_count <> 0 THEN
            RAISE EXCEPTION
                'Collateral Loan repayment must not release product exposure';
        END IF;
    ELSE
        RAISE EXCEPTION 'Loan product repayment is not supported';
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
    application_product_code VARCHAR(50);
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

    SELECT application_row.product_code INTO application_product_code
    FROM loan_applications application_row
    WHERE application_row.id = closure_row.loan_application_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'LoanAccount closure product identity is unavailable';
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

    IF application_product_code = 'SALARY_ADVANCE' THEN
        IF COALESCE((
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
            ), 0) <> account_row.approved_principal THEN
            RAISE EXCEPTION
                'Salary Advance closure requires full principal exposure release';
        END IF;
    ELSIF application_product_code = 'UNSECURED_CONSUMER_LOAN' THEN
        IF EXISTS (
            SELECT 1
            FROM salary_advance_limit_movements movement
            WHERE movement.loan_application_id = closure_row.loan_application_id
              AND movement.movement_type IN (
                  'DISBURSED_TO_USED', 'REPAID_RELEASED'
              )
        ) THEN
            RAISE EXCEPTION
                'Unsecured Consumer Loan closure must not contain Salary exposure evidence';
        END IF;
    ELSIF application_product_code = 'COLLATERAL_LOAN' THEN
        IF EXISTS (
            SELECT 1
            FROM salary_advance_limit_movements movement
            WHERE movement.loan_application_id = closure_row.loan_application_id
              AND movement.movement_type IN (
                  'DISBURSED_TO_USED', 'REPAID_RELEASED'
              )
        ) THEN
            RAISE EXCEPTION
                'Collateral Loan closure must not contain Salary exposure evidence';
        END IF;
    ELSE
        RAISE EXCEPTION 'Loan product closure is not supported';
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
