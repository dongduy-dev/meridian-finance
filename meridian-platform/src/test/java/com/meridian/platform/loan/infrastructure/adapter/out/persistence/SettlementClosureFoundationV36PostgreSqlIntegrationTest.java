package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

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
class SettlementClosureFoundationV36PostgreSqlIntegrationTest {

    private static final Path V36 = Path.of(
            "src/main/resources/db/migration/"
                    + "V36__add_settlement_and_closure_foundation.sql"
    );

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void cleanMigrationCreatesEvidencePermissionsAndDeferredIntegrity() {
        String schema = schema("clean");
        try {
            migrate(schema, "36");

            assertEquals("36", latestVersion(schema));
            assertTrue(tableExists(schema, "approved_loan_settlements"));
            assertTrue(tableExists(schema, "loan_account_closures"));
            assertTrue(columnExists(schema, "repayment_transactions",
                    "transaction_type"));
            assertEquals(1, permissionGrantCount(
                    schema, "loan:settlement:approve", "APPROVER"));
            assertEquals(1, permissionGrantCount(
                    schema, "loan:account:close", "ACCOUNTING_OFFICER"));
            assertEquals(0, permissionGrantCount(
                    schema, "loan:settlement:approve", "ACCOUNTING_OFFICER"));
            assertEquals(0, permissionGrantCount(
                    schema, "loan:account:close", "APPROVER"));
            assertEquals(1, triggerCount(
                    schema, "trg_approved_loan_settlements_immutable"));
            assertEquals(1, triggerCount(
                    schema, "trg_approved_loan_settlement_reconcile"));
            assertEquals(1, triggerCount(
                    schema, "trg_loan_account_closures_immutable"));
            assertEquals(1, triggerCount(
                    schema, "trg_loan_account_closure_reconcile"));
            assertTrue(constraintContains(
                    schema,
                    "loan_accounts",
                    "chk_loan_accounts_settlement_balance",
                    "CLOSED"
            ));
            assertTrue(constraintContains(
                    schema,
                    "loan_account_status_transitions",
                    "chk_loan_account_status_history_action",
                    "ADMINISTRATIVE_CLOSURE"
            ));
            assertTrue(constraintContains(
                    schema,
                    "repayment_installment_status_transitions",
                    "chk_repayment_installment_status_history_action",
                    "APPROVED_SETTLEMENT"
            ));
        } finally {
            drop(schema);
        }
    }

    @Test
    void upgradesV35WithoutMutatingExistingServicingEvidence() {
        String schema = schema("upgrade");
        try {
            migrate(schema, "35");
            assertFalse(tableExists(schema, "approved_loan_settlements"));
            assertFalse(columnExists(schema, "repayment_transactions",
                    "transaction_type"));

            migrate(schema, "36");

            assertEquals("36", latestVersion(schema));
            assertTrue(tableExists(schema, "approved_loan_settlements"));
            assertEquals(1, triggerCount(
                    schema, "trg_repayment_operation_outcome_reconcile"));
            assertEquals(1, triggerCount(
                    schema, "trg_repayment_reconcile_account"));
        } finally {
            drop(schema);
        }
    }

    @Test
    void incompatibleV35FailsBeforeAnyV36Mutation() throws Exception {
        String schema = schema("drift");
        try {
            migrate(schema, "35");
            jdbc.execute("alter table " + schema + ".loan_accounts "
                    + "drop constraint chk_loan_accounts_settlement_balance");
            jdbc.execute("alter table " + schema + ".loan_accounts "
                    + "add constraint chk_loan_accounts_settlement_balance "
                    + "check (total_outstanding >= 0)");

            assertThrows(SQLException.class, () -> executeV36(schema));

            assertFalse(tableExists(schema, "approved_loan_settlements"));
            assertFalse(columnExists(schema, "repayment_transactions",
                    "transaction_type"));
            assertEquals(0, permissionCount(schema,
                    "loan:settlement:approve"));
            assertEquals("35", latestVersion(schema));
        } finally {
            drop(schema);
        }
    }

    private void executeV36(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute(Files.readString(V36));
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private boolean tableExists(String schema, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass(?) is not null",
                Boolean.class,
                schema + "." + table
        ));
    }

    private boolean columnExists(String schema, String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from information_schema.columns "
                        + "where table_schema=? and table_name=? and column_name=?)",
                Boolean.class,
                schema,
                table,
                column
        ));
    }

    private int permissionCount(String schema, String permission) {
        return jdbc.queryForObject(
                "select count(*) from " + schema + ".permissions where code=?",
                Integer.class,
                permission
        );
    }

    private int permissionGrantCount(
            String schema,
            String permission,
            String role
    ) {
        return jdbc.queryForObject(
                "select count(*) from " + schema + ".role_permissions grant_row "
                        + "join " + schema + ".permissions permission "
                        + "on permission.id=grant_row.permission_id "
                        + "join " + schema + ".roles role "
                        + "on role.id=grant_row.role_id "
                        + "where permission.code=? and role.code=?",
                Integer.class,
                permission,
                role
        );
    }

    private int triggerCount(String schema, String trigger) {
        return jdbc.queryForObject(
                "select count(*) from pg_trigger trigger_row "
                        + "join pg_class relation on relation.oid=trigger_row.tgrelid "
                        + "join pg_namespace namespace "
                        + "on namespace.oid=relation.relnamespace "
                        + "where namespace.nspname=? and trigger_row.tgname=? "
                        + "and not trigger_row.tgisinternal",
                Integer.class,
                schema,
                trigger
        );
    }

    private boolean constraintContains(
            String schema,
            String table,
            String constraint,
            String expected
    ) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select pg_get_constraintdef(constraint_row.oid) like ? "
                        + "from pg_constraint constraint_row "
                        + "join pg_class relation on relation.oid=constraint_row.conrelid "
                        + "join pg_namespace namespace "
                        + "on namespace.oid=relation.relnamespace "
                        + "where namespace.nspname=? and relation.relname=? "
                        + "and constraint_row.conname=?",
                Boolean.class,
                "%" + expected + "%",
                schema,
                table,
                constraint
        ));
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema + ".flyway_schema_history "
                        + "where success order by installed_rank desc limit 1",
                String.class
        );
    }

    private void drop(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String schema(String suffix) {
        return "md_v36_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }
}
