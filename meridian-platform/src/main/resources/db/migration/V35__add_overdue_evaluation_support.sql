DO $$
DECLARE
    actual_values TEXT[];
    expected_values TEXT[];
    status_constraint_count INTEGER;
BEGIN
    IF to_regclass(current_schema() || '.idx_loan_accounts_overdue_candidates')
            IS NOT NULL THEN
        RAISE EXCEPTION
            'V35 preflight failed: overdue candidate index already exists';
    END IF;

    IF to_regclass(current_schema() || '.loan_accounts') IS NULL THEN
        RAISE EXCEPTION 'V35 preflight failed: loan_accounts is missing';
    END IF;

    SELECT array_agg(
        attribute.attname || ':'
            || pg_catalog.format_type(attribute.atttypid, attribute.atttypmod)
            || ':' || attribute.attnotnull::TEXT
        ORDER BY attribute.attname
    )
    INTO actual_values
    FROM pg_attribute attribute
    JOIN pg_class relation ON relation.oid = attribute.attrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'loan_accounts'
      AND relation.relkind = 'r'
      AND attribute.attnum > 0
      AND NOT attribute.attisdropped
      AND attribute.attname IN (
          'id', 'servicing_evaluation_date', 'status', 'total_outstanding'
      );

    expected_values := ARRAY[
        'id:uuid:true',
        'servicing_evaluation_date:date:true',
        'status:character varying(30):true',
        'total_outstanding:numeric(19,2):true'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount candidate columns do not match V34';
    END IF;

    CREATE TEMP TABLE v35_expected_loan_account_predicates (
        status VARCHAR(30) NOT NULL,
        total_outstanding NUMERIC(19,2) NOT NULL,
        CONSTRAINT chk_loan_accounts_status CHECK (
            status IN ('ACTIVE', 'OVERDUE', 'SETTLED', 'CLOSED')
        ),
        CONSTRAINT chk_loan_accounts_settlement_balance CHECK (
            (status = 'SETTLED' AND total_outstanding = 0)
            OR (status IN ('ACTIVE', 'OVERDUE') AND total_outstanding > 0)
            OR status = 'CLOSED'
        )
    ) ON COMMIT DROP;

    SELECT array_agg(
        constraint_row.conname || ':'
            || regexp_replace(
                lower(pg_get_expr(
                    constraint_row.conbin,
                    constraint_row.conrelid,
                    true
                )),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO actual_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'loan_accounts'
      AND constraint_row.contype = 'c'
      AND constraint_row.conname IN (
          'chk_loan_accounts_status',
          'chk_loan_accounts_settlement_balance'
      );

    SELECT array_agg(
        constraint_row.conname || ':'
            || regexp_replace(
                lower(pg_get_expr(
                    constraint_row.conbin,
                    constraint_row.conrelid,
                    true
                )),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO expected_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v35_expected_loan_account_predicates'
      AND constraint_row.contype = 'c';

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount status predicates do not match V34';
    END IF;

    SELECT count(*)
    INTO status_constraint_count
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'loan_accounts'
      AND constraint_row.contype = 'c'
      AND pg_get_expr(
            constraint_row.conbin,
            constraint_row.conrelid,
            true
          ) ~* '(^|[^a-z_])status([^a-z_]|$)';

    IF status_constraint_count <> 2 THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount status predicates are duplicated or incompatible';
    END IF;

    IF to_regclass(current_schema() || '.repayment_operation_outcomes') IS NULL THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment_operation_outcomes is missing';
    END IF;

    CREATE TEMP TABLE v35_expected_repayment_operation_outcomes (
        repayment_transaction_id UUID,
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
        CONSTRAINT repayment_operation_outcomes_pkey
            PRIMARY KEY (repayment_transaction_id),
        CONSTRAINT chk_repayment_operation_outcome_received
            CHECK (received_amount > 0 AND received_amount = trunc(received_amount)),
        CONSTRAINT chk_repayment_operation_outcome_principal
            CHECK (principal_released >= 0
                AND principal_released = trunc(principal_released)),
        CONSTRAINT chk_repayment_operation_outcome_status
            CHECK (account_status IN ('ACTIVE', 'OVERDUE', 'SETTLED')),
        CONSTRAINT chk_repayment_operation_outcome_json
            CHECK (is_repayment_operation_outcome_json_safe(outcome_json))
    ) ON COMMIT DROP;

    SELECT array_agg(
        attribute.attname || ':'
            || pg_catalog.format_type(attribute.atttypid, attribute.atttypmod)
            || ':' || attribute.attnotnull::TEXT
            || ':' || attribute.atthasdef::TEXT
        ORDER BY attribute.attnum
    )
    INTO actual_values
    FROM pg_attribute attribute
    JOIN pg_class relation ON relation.oid = attribute.attrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'repayment_operation_outcomes'
      AND relation.relkind = 'r'
      AND attribute.attnum > 0
      AND NOT attribute.attisdropped;

    SELECT array_agg(
        attribute.attname || ':'
            || pg_catalog.format_type(attribute.atttypid, attribute.atttypmod)
            || ':' || attribute.attnotnull::TEXT
            || ':' || attribute.atthasdef::TEXT
        ORDER BY attribute.attnum
    )
    INTO expected_values
    FROM pg_attribute attribute
    JOIN pg_class relation ON relation.oid = attribute.attrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v35_expected_repayment_operation_outcomes'
      AND attribute.attnum > 0
      AND NOT attribute.attisdropped;

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment outcome columns do not match V34';
    END IF;

    SELECT array_agg(
        constraint_row.conname || ':' || constraint_row.contype::TEXT || ':'
            || constraint_row.condeferrable::TEXT || ':'
            || constraint_row.condeferred::TEXT || ':'
            || regexp_replace(
                lower(pg_get_constraintdef(constraint_row.oid, true)),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO actual_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'repayment_operation_outcomes'
      AND constraint_row.contype IN ('p', 'c');

    SELECT array_agg(
        constraint_row.conname || ':' || constraint_row.contype::TEXT || ':'
            || constraint_row.condeferrable::TEXT || ':'
            || constraint_row.condeferred::TEXT || ':'
            || regexp_replace(
                lower(pg_get_constraintdef(constraint_row.oid, true)),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO expected_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    WHERE relation.relnamespace = pg_my_temp_schema()
      AND relation.relname = 'v35_expected_repayment_operation_outcomes'
      AND constraint_row.contype IN ('p', 'c');

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment outcome constraints do not match V34';
    END IF;

    SELECT array_agg(
        constraint_row.conname || ':' || constraint_row.contype::TEXT || ':'
            || constraint_row.condeferrable::TEXT || ':'
            || constraint_row.condeferred::TEXT || ':'
            || regexp_replace(
                lower(pg_get_constraintdef(constraint_row.oid, true)),
                '[[:space:]]+', '', 'g'
            )
        ORDER BY constraint_row.conname
    )
    INTO actual_values
    FROM pg_constraint constraint_row
    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = current_schema()
      AND relation.relname = 'repayment_operation_outcomes'
      AND constraint_row.contype = 'f';

    expected_values := ARRAY[
        'fk_repayment_operation_outcome_account:f:false:false:foreignkey(loan_account_id)referencesloan_accounts(id)',
        'fk_repayment_operation_outcome_application:f:false:false:foreignkey(loan_application_id)referencesloan_applications(id)',
        'fk_repayment_operation_outcome_schedule:f:false:false:foreignkey(repayment_schedule_id)referencesrepayment_schedules(id)',
        'fk_repayment_operation_outcome_transaction:f:false:false:foreignkey(repayment_transaction_id)referencesrepayment_transactions(id)'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: repayment outcome relationships do not match V34';
    END IF;

    SELECT array_agg(
        procedure_row.proname || ':' || procedure_row.pronargs::TEXT || ':'
            || pg_get_function_identity_arguments(procedure_row.oid) || ':'
            || procedure_row.prorettype::regtype::TEXT || ':'
            || language_row.lanname || ':'
            || md5(regexp_replace(
                lower(procedure_row.prosrc), '[[:space:]]+', '', 'g'
            ))
        ORDER BY procedure_row.proname
    )
    INTO actual_values
    FROM pg_proc procedure_row
    JOIN pg_namespace namespace ON namespace.oid = procedure_row.pronamespace
    JOIN pg_language language_row ON language_row.oid = procedure_row.prolang
    WHERE namespace.nspname = current_schema()
      AND procedure_row.proname IN (
          'enforce_loan_account_mutation_boundary',
          'reject_immutable_history_row_mutation',
          'validate_repayment_operation_completeness',
          'validate_repayment_operation_outcome',
          'validate_repayment_operation_outcome_evidence'
      );

    expected_values := ARRAY[
        'enforce_loan_account_mutation_boundary:0::trigger:plpgsql:32c22629b196f3e14c4f1d1d6ac7d3f8',
        'reject_immutable_history_row_mutation:0::trigger:plpgsql:ea83b1cb2b74be9ad02f51828d965552',
        'validate_repayment_operation_completeness:0::trigger:plpgsql:c53a1a934b92ed730466e9d2217da584',
        'validate_repayment_operation_outcome:0::trigger:plpgsql:321bd4ca5a0b56d2e343d8bcb4642580',
        'validate_repayment_operation_outcome_evidence:1:transaction_id_to_validate uuid:void:plpgsql:d554450749965a1d8f6d0cf07758d538'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: LoanAccount or repayment reconciliation functions do not match V34';
    END IF;

    SELECT array_agg(
        trigger_row.tgname || ':' || relation.relname || ':'
            || trigger_row.tgtype::INTEGER::TEXT || ':'
            || (trigger_row.tgconstraint <> 0)::TEXT || ':'
            || trigger_row.tgdeferrable::TEXT || ':'
            || trigger_row.tginitdeferred::TEXT || ':'
            || trigger_row.tgenabled::TEXT || ':' || procedure_row.proname
        ORDER BY trigger_row.tgname
    )
    INTO actual_values
    FROM pg_trigger trigger_row
    JOIN pg_class relation ON relation.oid = trigger_row.tgrelid
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    JOIN pg_proc procedure_row ON procedure_row.oid = trigger_row.tgfoid
    JOIN pg_namespace procedure_namespace
      ON procedure_namespace.oid = procedure_row.pronamespace
    WHERE namespace.nspname = current_schema()
      AND procedure_namespace.nspname = current_schema()
      AND trigger_row.tgname IN (
          'trg_loan_accounts_immutable',
          'trg_repayment_operation_outcomes_immutable',
          'trg_repayment_operation_outcome_reconcile',
          'trg_repayment_operation_transaction_completeness'
      )
      AND NOT trigger_row.tgisinternal;

    expected_values := ARRAY[
        'trg_loan_accounts_immutable:loan_accounts:27:false:false:false:O:enforce_loan_account_mutation_boundary',
        'trg_repayment_operation_outcome_reconcile:repayment_operation_outcomes:5:true:true:true:O:validate_repayment_operation_outcome',
        'trg_repayment_operation_outcomes_immutable:repayment_operation_outcomes:27:false:false:false:O:reject_immutable_history_row_mutation',
        'trg_repayment_operation_transaction_completeness:repayment_transactions:5:true:true:true:O:validate_repayment_operation_completeness'
    ]::TEXT[];

    IF actual_values IS DISTINCT FROM expected_values THEN
        RAISE EXCEPTION
            'V35 preflight failed: required V34 triggers do not match exact deferred semantics';
    END IF;
END;
$$;

CREATE INDEX idx_loan_accounts_overdue_candidates
    ON loan_accounts (servicing_evaluation_date, id)
    WHERE status IN ('ACTIVE', 'OVERDUE')
      AND total_outstanding > 0;
