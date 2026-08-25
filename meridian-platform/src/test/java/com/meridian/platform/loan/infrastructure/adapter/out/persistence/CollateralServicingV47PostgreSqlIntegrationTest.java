package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
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
class CollateralServicingV47PostgreSqlIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void upgradesV46ToV47AndPreservesDeferredTriggerBindings() {
        String schema = schema("upgrade");
        try {
            migrate(schema, "46");
            assertEquals("46", latestVersion(schema));
            assertFalse(function(schema,
                    "validate_repayment_operation_outcome_evidence(uuid)")
                    .contains("application_product_code = 'COLLATERAL_LOAN'"));

            migrate(schema, null);

            assertEquals("48", latestVersion(schema));
            assertEquals(9, servicingTriggerCount(schema));
            assertExplicitProductSemantics(schema);
        } finally {
            drop(schema);
        }
    }

    @Test
    void cleanMigrationThroughV47InstallsExplicitProductSemantics() {
        String schema = schema("clean");
        try {
            migrate(schema, null);

            assertEquals("48", latestVersion(schema));
            assertEquals(9, servicingTriggerCount(schema));
            assertExplicitProductSemantics(schema);
        } finally {
            drop(schema);
        }
    }

    @Test
    void preflightRejectsIncompatibleCollateralExposureEvidence() {
        String schema = schema("preflight");
        try {
            migrate(schema, "46");
            UUID applicationId = UUID.randomUUID();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.execute("set local search_path to " + schema + ", public");
                jdbc.execute("set local session_replication_role = replica");
                jdbc.update(
                        "insert into " + schema + ".loan_applications "
                                + "(id,customer_id,loan_product_id,application_number,"
                                + "product_code,product_type,status,requested_amount,"
                                + "requested_term_months,submitted_at) values "
                                + "(?,?,?,'V47-PREFLIGHT','COLLATERAL_LOAN','SECURED',"
                                + "'DISBURSED',1000,6,?)",
                        applicationId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                );
                jdbc.update(
                        "insert into " + schema + ".salary_advance_limit_movements "
                                + "(id,salary_advance_limit_id,loan_application_id,"
                                + "loan_account_id,movement_type,amount,occurred_at) "
                                + "values (?,?,?,?,'DISBURSED_TO_USED',1,?)",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        applicationId,
                        UUID.randomUUID(),
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                );
            });

            RuntimeException failure = assertThrows(
                    RuntimeException.class,
                    () -> migrate(schema, null)
            );
            assertTrue(rootMessage(failure).contains(
                    "V47 preflight failed: incompatible Collateral servicing evidence exists"
            ));
            assertEquals("46", latestVersion(schema));
        } finally {
            drop(schema);
        }
    }

    private void assertExplicitProductSemantics(String schema) {
        String servicing = function(
                schema, "validate_repayment_servicing_reconciliation()"
        );
        String outcome = function(
                schema, "validate_repayment_operation_outcome_evidence(uuid)"
        );
        String closure = function(
                schema, "validate_loan_account_closure_evidence()"
        );

        assertTrue(servicing.contains("SALARY_ADVANCE"));
        assertTrue(servicing.contains("UNSECURED_CONSUMER_LOAN"));
        assertTrue(servicing.contains("COLLATERAL_LOAN"));
        assertTrue(outcome.contains("SALARY_ADVANCE"));
        assertTrue(outcome.contains("UNSECURED_CONSUMER_LOAN"));
        assertTrue(outcome.contains("COLLATERAL_LOAN"));
        assertTrue(outcome.contains("Loan product repayment is not supported"));
        assertTrue(closure.contains("SALARY_ADVANCE"));
        assertTrue(closure.contains("UNSECURED_CONSUMER_LOAN"));
        assertTrue(closure.contains("COLLATERAL_LOAN"));
        assertTrue(closure.contains("Loan product closure is not supported"));
    }

    private int servicingTriggerCount(String schema) {
        return jdbc.queryForObject(
                "select count(distinct trigger_row.tgname) from pg_trigger trigger_row "
                        + "join pg_class table_row on table_row.oid=trigger_row.tgrelid "
                        + "join pg_namespace namespace_row "
                        + "on namespace_row.oid=table_row.relnamespace "
                        + "where namespace_row.nspname=? and not trigger_row.tgisinternal "
                        + "and trigger_row.tgname in ("
                        + "'trg_repayment_reconcile_transaction',"
                        + "'trg_repayment_reconcile_allocation',"
                        + "'trg_repayment_reconcile_progress',"
                        + "'trg_repayment_reconcile_account',"
                        + "'trg_repayment_reconcile_release',"
                        + "'trg_repayment_operation_outcome_reconcile',"
                        + "'trg_repayment_operation_transaction_completeness',"
                        + "'trg_approved_loan_settlement_reconcile',"
                        + "'trg_loan_account_closure_reconcile')",
                Integer.class,
                schema
        );
    }

    private String function(String schema, String signature) {
        return jdbc.queryForObject(
                "select pg_get_functiondef(to_regprocedure(?))",
                String.class,
                schema + "." + signature
        );
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success "
                        + "order by installed_rank desc limit 1",
                String.class
        );
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    private static String schema(String suffix) {
        return "mer_cl_v47_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private void drop(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }
}
