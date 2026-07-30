DO $$
DECLARE
    expected_types CONSTANT TEXT[] := ARRAY[
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION',
        'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT'
    ]::TEXT[];
    actual_types TEXT[];
    matching_constraint_count INTEGER;
    constraint_expression TEXT;
    expected_constraint_expression TEXT;
    expected_type_sql TEXT;
    normalized_constraint_expression TEXT;
    normalized_expected_expression TEXT;
BEGIN
    SELECT count(*)
    INTO matching_constraint_count
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_entity_type'
      AND constraint_row.contype = 'c';

    IF matching_constraint_count <> 1 THEN
        RAISE EXCEPTION
            'V34 preflight failed: expected V33 audit entity constraint is missing or incompatible';
    END IF;

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_entity_type'
      AND constraint_row.contype = 'c';

    SELECT array_agg(matched.value[1] ORDER BY matched.value[1])
    INTO actual_types
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    CROSS JOIN LATERAL regexp_matches(
        pg_get_constraintdef(constraint_row.oid),
        '''([A-Z][A-Z0-9_]*)''', 'g'
    ) AS matched(value)
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_entity_type'
      AND constraint_row.contype = 'c';

    IF actual_types IS NULL
            OR cardinality(actual_types) <> cardinality(expected_types)
            OR EXISTS (
                SELECT value FROM unnest(expected_types) value
                EXCEPT SELECT value FROM unnest(actual_types) value
            )
            OR EXISTS (
                SELECT value FROM unnest(actual_types) value
                EXCEPT SELECT value FROM unnest(expected_types) value
            ) THEN
        RAISE EXCEPTION
            'V34 preflight failed: audit entity constraint does not match V33';
    END IF;

    SELECT string_agg(quote_literal(expected.entity_type), ', '
        ORDER BY expected.ordinality)
    INTO expected_type_sql
    FROM unnest(expected_types) WITH ORDINALITY
        AS expected(entity_type, ordinality);

    EXECUTE format(
        'CREATE TEMP TABLE v34_expected_audit_entity_constraint ('
        'entity_type VARCHAR(80) NOT NULL, '
        'CONSTRAINT chk_v34_expected_audit_entity '
        'CHECK (entity_type IN (%s))'
        ') ON COMMIT DROP',
        expected_type_sql
    );

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO expected_constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v34_expected_audit_entity_constraint'
      AND constraint_row.conname = 'chk_v34_expected_audit_entity'
      AND constraint_row.contype = 'c';

    normalized_constraint_expression := regexp_replace(
        regexp_replace(
            constraint_expression,
            '''[A-Z][A-Z0-9_]*''',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '[[:space:]]+',
        '',
        'g'
    );
    normalized_expected_expression := regexp_replace(
        regexp_replace(
            expected_constraint_expression,
            '''[A-Z][A-Z0-9_]*''',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '[[:space:]]+',
        '',
        'g'
    );

    normalized_constraint_expression := replace(
        regexp_replace(
            translate(normalized_constraint_expression, '()', ''),
            '''__ENTITY_TYPE__''::charactervarying(::text)?',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '::text[]',
        ''
    );
    normalized_expected_expression := replace(
        regexp_replace(
            translate(normalized_expected_expression, '()', ''),
            '''__ENTITY_TYPE__''::charactervarying(::text)?',
            '''__ENTITY_TYPE__''',
            'g'
        ),
        '::text[]',
        ''
    );

    IF normalized_constraint_expression IS DISTINCT FROM normalized_expected_expression THEN
        RAISE EXCEPTION
            'V34 preflight failed: incompatible audit entity constraint predicate';
    END IF;

    DROP TABLE v34_expected_audit_entity_constraint;
END;
$$;
ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_entity_type;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT', 'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION',
        'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT',
        'REPAYMENT_TRANSACTION', 'LOAN_ACCOUNT'
    ));

CREATE OR REPLACE FUNCTION is_repayment_operation_outcome_json_safe(document JSONB)
RETURNS BOOLEAN AS $$
DECLARE
    installment JSONB;
    progress JSONB;
    schedule_item_id TEXT;
    seen_schedule_item_ids TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF document IS NULL OR jsonb_typeof(document) <> 'object' THEN
        RETURN FALSE;
    END IF;

    IF EXISTS (
        WITH RECURSIVE json_nodes(value) AS (
            SELECT document
            UNION ALL
            SELECT child.value
            FROM json_nodes node
            CROSS JOIN LATERAL (
                SELECT object_value.value
                FROM jsonb_each(
                    CASE WHEN jsonb_typeof(node.value) = 'object'
                        THEN node.value ELSE '{}'::JSONB END
                ) AS object_value(key, value)
                UNION ALL
                SELECT array_value.value
                FROM jsonb_array_elements(
                    CASE WHEN jsonb_typeof(node.value) = 'array'
                        THEN node.value ELSE '[]'::JSONB END
                ) AS array_value(value)
            ) child
        )
        SELECT 1
        FROM json_nodes node
        CROSS JOIN LATERAL jsonb_object_keys(
            CASE WHEN jsonb_typeof(node.value) = 'object'
                THEN node.value ELSE '{}'::JSONB END
        ) prohibited_key
        WHERE lower(prohibited_key) = ANY (ARRAY[
                'externalpaymentreference', 'paymentreference',
                'canonicalreference', 'recordedbyuserid', 'actorid', 'userid',
                'customerid', 'salaryadvancelimitid', 'limitid', 'movementid',
                'auditid', 'historyid', 'encryptionmetadata', 'encryptionkeyid',
                'keyid', 'nonce', 'aad', 'ciphertext', 'fingerprint'
            ]::TEXT[])
           OR lower(prohibited_key) LIKE '%encryption%'
    ) THEN
        RETURN FALSE;
    END IF;

    IF NOT document ?& ARRAY[
            'repaymentTransactionId', 'loanApplicationId', 'loanAccountId',
            'repaymentScheduleId', 'receivedAmount', 'paymentValueDate',
            'recordedAt', 'accountBalance', 'accountStatus',
            'accountStatusChanged', 'principalReleased', 'installments'
        ]
        OR (SELECT COUNT(*) FROM jsonb_object_keys(document)) <> 12
        OR jsonb_typeof(document -> 'repaymentTransactionId') <> 'string'
        OR jsonb_typeof(document -> 'loanApplicationId') <> 'string'
        OR jsonb_typeof(document -> 'loanAccountId') <> 'string'
        OR jsonb_typeof(document -> 'repaymentScheduleId') <> 'string'
        OR jsonb_typeof(document -> 'receivedAmount') <> 'number'
        OR jsonb_typeof(document -> 'paymentValueDate') <> 'string'
        OR jsonb_typeof(document -> 'recordedAt') <> 'string'
        OR jsonb_typeof(document -> 'accountBalance') <> 'object'
        OR jsonb_typeof(document -> 'accountStatus') <> 'string'
        OR jsonb_typeof(document -> 'accountStatusChanged') <> 'boolean'
        OR jsonb_typeof(document -> 'principalReleased') <> 'number'
        OR jsonb_typeof(document -> 'installments') <> 'array'
        OR document ->> 'accountStatus' NOT IN ('ACTIVE', 'OVERDUE', 'SETTLED') THEN
        RETURN FALSE;
    END IF;

    IF NOT (document -> 'accountBalance') ?& ARRAY[
            'principalPaid', 'interestPaid', 'feePaid', 'totalPaid',
            'principalOutstanding', 'interestOutstanding', 'feeOutstanding',
            'totalOutstanding', 'lastPaymentValueDate',
            'lastPaymentRecordedAt', 'servicingEvaluationDate'
        ]
        OR (SELECT COUNT(*) FROM jsonb_object_keys(document -> 'accountBalance')) <> 11
        OR EXISTS (
            SELECT 1
            FROM unnest(ARRAY[
                'principalPaid', 'interestPaid', 'feePaid', 'totalPaid',
                'principalOutstanding', 'interestOutstanding', 'feeOutstanding',
                'totalOutstanding'
            ]::TEXT[]) amount_key
            WHERE jsonb_typeof(document -> 'accountBalance' -> amount_key) <> 'number'
        )
        OR jsonb_typeof(document -> 'accountBalance' -> 'servicingEvaluationDate')
            <> 'string'
        OR jsonb_typeof(document -> 'accountBalance' -> 'lastPaymentValueDate')
            NOT IN ('string', 'null')
        OR jsonb_typeof(document -> 'accountBalance' -> 'lastPaymentRecordedAt')
            NOT IN ('string', 'null') THEN
        RETURN FALSE;
    END IF;

    FOR installment IN
        SELECT value FROM jsonb_array_elements(document -> 'installments')
    LOOP
        IF jsonb_typeof(installment) <> 'object'
            OR NOT installment ?& ARRAY['progress', 'previousStatus', 'statusChanged']
            OR (SELECT COUNT(*) FROM jsonb_object_keys(installment)) <> 3
            OR jsonb_typeof(installment -> 'progress') <> 'object'
            OR jsonb_typeof(installment -> 'previousStatus') <> 'string'
            OR jsonb_typeof(installment -> 'statusChanged') <> 'boolean'
            OR installment ->> 'previousStatus' NOT IN (
                'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
            ) THEN
            RETURN FALSE;
        END IF;

        progress := installment -> 'progress';
        IF NOT progress ?& ARRAY[
                'repaymentScheduleItemId', 'repaymentScheduleId', 'loanAccountId',
                'installmentNumber', 'principalPaid', 'interestPaid', 'feePaid',
                'totalPaid', 'principalOutstanding', 'interestOutstanding',
                'feeOutstanding', 'totalOutstanding', 'status',
                'lastPaymentValueDate', 'lastPaymentRecordedAt',
                'servicingEvaluationDate', 'updatedAt'
            ]
            OR (SELECT COUNT(*) FROM jsonb_object_keys(progress)) <> 17
            OR jsonb_typeof(progress -> 'repaymentScheduleItemId') <> 'string'
            OR jsonb_typeof(progress -> 'repaymentScheduleId') <> 'string'
            OR jsonb_typeof(progress -> 'loanAccountId') <> 'string'
            OR jsonb_typeof(progress -> 'installmentNumber') <> 'number'
            OR jsonb_typeof(progress -> 'status') <> 'string'
            OR progress ->> 'status' NOT IN (
                'NOT_DUE', 'DUE', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'
            )
            OR jsonb_typeof(progress -> 'lastPaymentValueDate')
                NOT IN ('string', 'null')
            OR jsonb_typeof(progress -> 'lastPaymentRecordedAt')
                NOT IN ('string', 'null')
            OR jsonb_typeof(progress -> 'servicingEvaluationDate') <> 'string'
            OR jsonb_typeof(progress -> 'updatedAt') <> 'string'
            OR EXISTS (
                SELECT 1
                FROM unnest(ARRAY[
                    'principalPaid', 'interestPaid', 'feePaid', 'totalPaid',
                    'principalOutstanding', 'interestOutstanding',
                    'feeOutstanding', 'totalOutstanding'
                ]::TEXT[]) amount_key
                WHERE jsonb_typeof(progress -> amount_key) <> 'number'
            ) THEN
            RETURN FALSE;
        END IF;

        schedule_item_id := progress ->> 'repaymentScheduleItemId';
        IF schedule_item_id = ANY (seen_schedule_item_ids) THEN
            RETURN FALSE;
        END IF;
        seen_schedule_item_ids := array_append(
            seen_schedule_item_ids,
            schedule_item_id
        );

        IF ((installment ->> 'statusChanged')::BOOLEAN
                AND installment ->> 'previousStatus' = progress ->> 'status')
            OR (NOT (installment ->> 'statusChanged')::BOOLEAN
                AND installment ->> 'previousStatus' <> progress ->> 'status') THEN
            RETURN FALSE;
        END IF;
    END LOOP;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE TABLE repayment_operation_outcomes (
    repayment_transaction_id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    loan_account_id UUID NOT NULL,
    repayment_schedule_id UUID NOT NULL,
    received_amount NUMERIC(19,2) NOT NULL,
    payment_value_date DATE NOT NULL,
    recorded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    principal_released NUMERIC(19,2) NOT NULL,
    account_status VARCHAR(30) NOT NULL,
    account_status_changed BOOLEAN NOT NULL,
    outcome_json JSONB NOT NULL,
    CONSTRAINT fk_repayment_operation_outcome_transaction
        FOREIGN KEY (repayment_transaction_id)
        REFERENCES repayment_transactions (id),
    CONSTRAINT fk_repayment_operation_outcome_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_repayment_operation_outcome_account
        FOREIGN KEY (loan_account_id) REFERENCES loan_accounts (id),
    CONSTRAINT fk_repayment_operation_outcome_schedule
        FOREIGN KEY (repayment_schedule_id) REFERENCES repayment_schedules (id),
    CONSTRAINT chk_repayment_operation_outcome_received
        CHECK (received_amount > 0 AND received_amount = trunc(received_amount)),
    CONSTRAINT chk_repayment_operation_outcome_principal
        CHECK (principal_released >= 0
            AND principal_released = trunc(principal_released)),
    CONSTRAINT chk_repayment_operation_outcome_status
        CHECK (account_status IN ('ACTIVE', 'OVERDUE', 'SETTLED')),
    CONSTRAINT chk_repayment_operation_outcome_json
        CHECK (is_repayment_operation_outcome_json_safe(outcome_json))
);

CREATE INDEX idx_repayment_operation_outcomes_account_recorded
    ON repayment_operation_outcomes (loan_account_id, recorded_at,
        repayment_transaction_id);

CREATE TRIGGER trg_repayment_operation_outcomes_immutable
    BEFORE UPDATE OR DELETE ON repayment_operation_outcomes
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

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
        WHERE entity_type = 'REPAYMENT_TRANSACTION'
          AND entity_id = transaction_id_to_validate
    )
    INTO repayment_audit_count, correct_repayment_audit_count
    FROM audit_events
    WHERE operation_id = transaction_id_to_validate
      AND action = 'REPAYMENT_RECORDED';
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
          AND action = 'REPAYMENT_RECORDED'
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
                OR transition_row.action <> 'REPAYMENT_RECORDED'
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

CREATE OR REPLACE FUNCTION validate_repayment_operation_outcome()
RETURNS trigger AS $$
BEGIN
    PERFORM validate_repayment_operation_outcome_evidence(
        NEW.repayment_transaction_id
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION validate_repayment_operation_completeness()
RETURNS trigger AS $$
BEGIN
    PERFORM validate_repayment_operation_outcome_evidence(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_repayment_operation_outcome_reconcile
    AFTER INSERT ON repayment_operation_outcomes
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_operation_outcome();

CREATE CONSTRAINT TRIGGER trg_repayment_operation_transaction_completeness
    AFTER INSERT ON repayment_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_repayment_operation_completeness();
