DO $$
DECLARE
    expected_actions CONSTANT TEXT[] := ARRAY[
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED',
        'DOCUMENT_VERSION_UPLOADED', 'DOCUMENT_REVIEW_ACCEPTED',
        'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED',
        'REVIEW_CYCLE_STATE_CHANGED', 'CORRECTION_REQUEST_CREATED',
        'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED',
        'LOAN_CONTRACT_SUPERSEDED', 'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED', 'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'
    ]::TEXT[];
    actual_actions TEXT[];
    matching_constraint_count INTEGER;
    constraint_expression TEXT;
    expected_constraint_expression TEXT;
    expected_action_sql TEXT;
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
      AND constraint_row.conname = 'chk_audit_events_action'
      AND constraint_row.contype = 'c';

    IF matching_constraint_count <> 1 THEN
        RAISE EXCEPTION
            'V32 preflight failed: expected V31 audit action constraint is missing or incompatible';
    END IF;

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_action'
      AND constraint_row.contype = 'c';

    SELECT array_agg(matched.value[1] ORDER BY matched.value[1])
    INTO actual_actions
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    CROSS JOIN LATERAL regexp_matches(
        pg_get_constraintdef(constraint_row.oid),
        '''([A-Z][A-Z0-9_]*)''',
        'g'
    ) AS matched(value)
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'audit_events'
      AND constraint_row.conname = 'chk_audit_events_action'
      AND constraint_row.contype = 'c';

    IF actual_actions IS NULL
            OR cardinality(actual_actions) <> cardinality(expected_actions)
            OR EXISTS (
                SELECT expected.action
                FROM unnest(expected_actions) AS expected(action)
                EXCEPT
                SELECT actual.action
                FROM unnest(actual_actions) AS actual(action)
            )
            OR EXISTS (
                SELECT actual.action
                FROM unnest(actual_actions) AS actual(action)
                EXCEPT
                SELECT expected.action
                FROM unnest(expected_actions) AS expected(action)
            ) THEN
        RAISE EXCEPTION
            'V32 preflight failed: audit action constraint does not match expected V31 state';
    END IF;

    SELECT string_agg(quote_literal(expected.action), ', ' ORDER BY expected.ordinality)
    INTO expected_action_sql
    FROM unnest(expected_actions) WITH ORDINALITY AS expected(action, ordinality);

    EXECUTE format(
        'CREATE TEMP TABLE v32_expected_audit_action_constraint ('
        'action VARCHAR(100) NOT NULL, '
        'CONSTRAINT chk_v32_expected_audit_action CHECK (action IN (%s))'
        ') ON COMMIT DROP',
        expected_action_sql
    );

    SELECT pg_get_expr(constraint_row.conbin, constraint_row.conrelid)
    INTO expected_constraint_expression
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v32_expected_audit_action_constraint'
      AND constraint_row.conname = 'chk_v32_expected_audit_action'
      AND constraint_row.contype = 'c';

    normalized_constraint_expression := regexp_replace(
        regexp_replace(
            constraint_expression,
            '''[A-Z][A-Z0-9_]*''',
            '''__ACTION__''',
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
            '''__ACTION__''',
            'g'
        ),
        '[[:space:]]+',
        '',
        'g'
    );

    normalized_constraint_expression := replace(
        regexp_replace(
            translate(normalized_constraint_expression, '()', ''),
            '''__ACTION__''::charactervarying(::text)?',
            '''__ACTION__''',
            'g'
        ),
        '::text[]',
        ''
    );
    normalized_expected_expression := replace(
        regexp_replace(
            translate(normalized_expected_expression, '()', ''),
            '''__ACTION__''::charactervarying(::text)?',
            '''__ACTION__''',
            'g'
        ),
        '::text[]',
        ''
    );

    IF normalized_constraint_expression IS DISTINCT FROM normalized_expected_expression THEN
        RAISE EXCEPTION
            'V32 preflight failed: incompatible audit action constraint predicate';
    END IF;

    DROP TABLE v32_expected_audit_action_constraint;
END;
$$;

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED',
        'DOCUMENT_VERSION_UPLOADED', 'DOCUMENT_REVIEW_ACCEPTED',
        'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED',
        'REVIEW_CYCLE_STATE_CHANGED', 'CORRECTION_REQUEST_CREATED',
        'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED',
        'LOAN_CONTRACT_SUPERSEDED', 'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED', 'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED', 'LOAN_ACCOUNT_STATUS_CHANGED'
    ));
