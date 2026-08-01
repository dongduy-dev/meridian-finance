package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class OverdueEvaluationV35PostgreSqlIntegrationTest {

    private static final Path V35 = Path.of(
            "src/main/resources/db/migration/V35__add_overdue_evaluation_support.sql");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void cleanMigrationThroughV35CreatesOnlyTheCandidateIndex() {
        String schema = schema("clean");
        try {
            migrate(schema, "35");
            assertEquals("35", latestVersion(schema));
            assertTrue(indexExists(schema));
            assertEquals(0, count(schema, "loan_accounts"));
            assertEquals(0, count(schema, "loan_account_status_transitions"));
        } finally {
            drop(schema);
        }
    }

    @Test
    void upgradesV34ToV35WithoutReplacingV34Reconciliation() {
        String schema = schema("upgrade");
        try {
            migrate(schema, "34");
            assertFalse(indexExists(schema));
            assertEquals(1, triggerCount(schema,
                    "trg_repayment_operation_transaction_completeness"));
            assertEquals(1, triggerCount(schema,
                    "trg_repayment_operation_outcome_reconcile"));
            migrate(schema, "35");
            assertTrue(indexExists(schema));
            assertEquals(1, triggerCount(schema,
                    "trg_repayment_operation_transaction_completeness"));
        } finally {
            drop(schema);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompatibleV34Structures")
    void incompatibleV34StructureFailsBeforeAnyV35Mutation(Drift drift) throws Exception {
        String schema = schema(drift.label());
        try {
            migrate(schema, "34");
            drift.statements().forEach(statement -> jdbc.execute(statement.formatted(schema)));

            assertThrows(SQLException.class, () -> executeV35(schema));
            assertFalse(indexExists(schema));
            assertEquals("34", latestVersion(schema));
            assertEquals(0, count(schema, "loan_accounts"));
            assertEquals(0, count(schema, "loan_account_status_transitions"));
            assertEquals(0, count(schema, "audit_events"));
        } finally {
            drop(schema);
        }
    }

    static Stream<Drift> incompatibleV34Structures() {
        return Stream.concat(Stream.of(
                drift("missing_status_constraint",
                        "alter table %1$s.loan_accounts "
                                + "drop constraint chk_loan_accounts_status"),
                drift("weakened_status_constraint",
                        "alter table %1$s.loan_accounts "
                                + "drop constraint chk_loan_accounts_status",
                        "alter table %1$s.loan_accounts "
                                + "add constraint chk_loan_accounts_status "
                                + "check (status is not null)"),
                drift("weakened_settlement_constraint",
                        "alter table %1$s.loan_accounts "
                                + "drop constraint chk_loan_accounts_settlement_balance",
                        "alter table %1$s.loan_accounts "
                                + "add constraint chk_loan_accounts_settlement_balance "
                                + "check (total_outstanding >= 0)"),
                drift("missing_outcome_reconcile_trigger",
                        "drop trigger trg_repayment_operation_outcome_reconcile "
                                + "on %1$s.repayment_operation_outcomes"),
                drift("renamed_outcome_reconcile_trigger",
                        "alter trigger trg_repayment_operation_outcome_reconcile "
                                + "on %1$s.repayment_operation_outcomes "
                                + "rename to trg_repayment_operation_outcome_changed"),
                drift("outcome_trigger_wrong_function",
                        "drop trigger trg_repayment_operation_outcome_reconcile "
                                + "on %1$s.repayment_operation_outcomes",
                        "create constraint trigger trg_repayment_operation_outcome_reconcile "
                                + "after insert on %1$s.repayment_operation_outcomes "
                                + "deferrable initially deferred for each row execute function "
                                + "%1$s.validate_repayment_operation_completeness()")
        ), incompatibleTriggerAndOutcomeDrifts());
    }

    private static Stream<Drift> incompatibleTriggerAndOutcomeDrifts() {
        return Stream.of(
                drift("outcome_trigger_not_deferrable",
                        "drop trigger trg_repayment_operation_outcome_reconcile "
                                + "on %1$s.repayment_operation_outcomes",
                        "create constraint trigger trg_repayment_operation_outcome_reconcile "
                                + "after insert on %1$s.repayment_operation_outcomes "
                                + "for each row execute function "
                                + "%1$s.validate_repayment_operation_outcome()"),
                drift("outcome_trigger_wrong_event",
                        "drop trigger trg_repayment_operation_outcome_reconcile "
                                + "on %1$s.repayment_operation_outcomes",
                        "create constraint trigger trg_repayment_operation_outcome_reconcile "
                                + "after update on %1$s.repayment_operation_outcomes "
                                + "deferrable initially deferred for each row execute function "
                                + "%1$s.validate_repayment_operation_outcome()"),
                drift("outcome_trigger_wrong_table",
                        "drop trigger trg_repayment_operation_outcome_reconcile "
                                + "on %1$s.repayment_operation_outcomes",
                        "create constraint trigger trg_repayment_operation_outcome_reconcile "
                                + "after insert on %1$s.repayment_transactions "
                                + "deferrable initially deferred for each row execute function "
                                + "%1$s.validate_repayment_operation_outcome()"),
                drift("missing_transaction_completeness_trigger",
                        "drop trigger trg_repayment_operation_transaction_completeness "
                                + "on %1$s.repayment_transactions"),
                drift("altered_required_outcome_column",
                        "alter table %1$s.repayment_operation_outcomes "
                                + "alter column account_status drop not null"),
                drift("altered_required_outcome_primary_key",
                        "alter table %1$s.repayment_operation_outcomes "
                                + "drop constraint repayment_operation_outcomes_pkey")
        );
    }

    private void executeV35(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute(Files.readString(V35));
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private boolean indexExists(String schema) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pg_indexes where schemaname = ? "
                        + "and indexname = 'idx_loan_accounts_overdue_candidates')",
                Boolean.class, schema));
    }

    private int triggerCount(String schema, String trigger) {
        return jdbc.queryForObject(
                "select count(*) from pg_trigger trigger_row "
                        + "join pg_class relation on relation.oid=trigger_row.tgrelid "
                        + "join pg_namespace namespace on namespace.oid=relation.relnamespace "
                        + "where namespace.nspname=? and trigger_row.tgname=? "
                        + "and not trigger_row.tgisinternal",
                Integer.class, schema, trigger);
    }

    private int count(String schema, String table) {
        return jdbc.queryForObject("select count(*) from " + schema + "." + table,
                Integer.class);
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject("select version from " + schema
                + ".flyway_schema_history where success "
                + "order by installed_rank desc limit 1", String.class);
    }

    private void drop(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String schema(String suffix) {
        String compactSuffix = suffix.substring(0, Math.min(suffix.length(), 16));
        return "md_v35_" + compactSuffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private static Drift drift(String label, String... statements) {
        return new Drift(label, List.of(statements));
    }

    private record Drift(String label, List<String> statements) {
        @Override
        public String toString() {
            return label;
        }
    }
}
