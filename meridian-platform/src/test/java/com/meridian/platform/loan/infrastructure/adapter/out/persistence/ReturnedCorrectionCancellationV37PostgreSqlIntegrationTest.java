package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ReturnedCorrectionCancellationV37PostgreSqlIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void cleanMigrationCreatesNarrowImmutableCancellationFoundation() {
        String schema = schema("clean");
        try {
            migrate(schema, "37");

            assertEquals("37", latestVersion(schema));
            assertTrue(tableExists(schema, "loan_application_cancellations"));
            assertTrue(columnExists(schema, "loan_correction_requests", "cancelled_at"));
            assertTrue(constraintContains(
                    schema,
                    "loan_correction_requests",
                    "chk_loan_correction_requests_status",
                    "CANCELLED"
            ));
            assertTrue(constraintContains(
                    schema,
                    "loan_application_status_transitions",
                    "chk_loan_application_status_transitions_action",
                    "CANCEL_APPLICATION"
            ));
            assertTrue(constraintContains(
                    schema,
                    "audit_events",
                    "chk_audit_events_action",
                    "LOAN_APPLICATION_CANCELLED"
            ));
            assertEquals(1, triggerCount(
                    schema,
                    "trg_loan_application_cancellations_immutable"
            ));
            assertEquals(1, triggerCount(
                    schema,
                    "trg_loan_application_cancellations_reconcile"
            ));
            assertEquals(1, customerCancellationPermissionCount(schema));
        } finally {
            drop(schema);
        }
    }

    @Test
    void upgradesV36WithoutRecreatingExistingCancellationPermission() {
        String schema = schema("upgrade");
        try {
            migrate(schema, "36");
            assertFalse(tableExists(schema, "loan_application_cancellations"));
            assertFalse(columnExists(schema, "loan_correction_requests", "cancelled_at"));
            assertEquals(1, customerCancellationPermissionCount(schema));

            migrate(schema, "37");

            assertEquals("37", latestVersion(schema));
            assertTrue(tableExists(schema, "loan_application_cancellations"));
            assertEquals(1, customerCancellationPermissionCount(schema));
        } finally {
            drop(schema);
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

    private boolean constraintContains(
            String schema,
            String table,
            String constraint,
            String value
    ) {
        String definition = jdbc.queryForObject("""
                select pg_get_constraintdef(constraint_row.oid)
                from pg_constraint constraint_row
                join pg_class relation on relation.oid = constraint_row.conrelid
                join pg_namespace namespace on namespace.oid = relation.relnamespace
                where namespace.nspname = ?
                  and relation.relname = ?
                  and constraint_row.conname = ?
                """, String.class, schema, table, constraint);
        return definition != null && definition.contains(value);
    }

    private int triggerCount(String schema, String trigger) {
        return jdbc.queryForObject("""
                select count(*)
                from pg_trigger trigger_row
                join pg_class relation on relation.oid = trigger_row.tgrelid
                join pg_namespace namespace on namespace.oid = relation.relnamespace
                where namespace.nspname = ?
                  and trigger_row.tgname = ?
                  and not trigger_row.tgisinternal
                """, Integer.class, schema, trigger);
    }

    private int customerCancellationPermissionCount(String schema) {
        return jdbc.queryForObject("select count(*) from " + schema
                + ".role_permissions role_permission join " + schema
                + ".roles role on role.id=role_permission.role_id join " + schema
                + ".permissions permission on permission.id=role_permission.permission_id "
                + "where role.code='CUSTOMER' and permission.code='loan:cancel:own'",
                Integer.class);
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success "
                        + "order by installed_rank desc limit 1",
                String.class
        );
    }

    private void drop(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String schema(String suffix) {
        return "meridian_v37_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }
}
