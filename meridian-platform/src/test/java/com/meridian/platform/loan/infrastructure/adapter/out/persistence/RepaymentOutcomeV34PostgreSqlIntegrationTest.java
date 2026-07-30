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
        "meridian.document.orphan-reconciliation.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class RepaymentOutcomeV34PostgreSqlIntegrationTest {
    private static final Path V34 = Path.of(
            "src/main/resources/db/migration/"
                    + "V34__add_repayment_operation_outcome_snapshot.sql"
    );
    private static final String VALID_OUTCOME_JSON = """
            {
              "repaymentTransactionId":"00000000-0000-0000-0000-000000000101",
              "loanApplicationId":"00000000-0000-0000-0000-000000000102",
              "loanAccountId":"00000000-0000-0000-0000-000000000103",
              "repaymentScheduleId":"00000000-0000-0000-0000-000000000104",
              "receivedAmount":1,
              "paymentValueDate":"2026-07-28",
              "recordedAt":"2026-07-28T10:00:00",
              "accountBalance":{
                "principalPaid":0,"interestPaid":1,"feePaid":0,"totalPaid":1,
                "principalOutstanding":1000,"interestOutstanding":24,
                "feeOutstanding":0,"totalOutstanding":1024,
                "lastPaymentValueDate":"2026-07-28",
                "lastPaymentRecordedAt":"2026-07-28T10:00:00",
                "servicingEvaluationDate":"2026-07-28"
              },
              "accountStatus":"ACTIVE",
              "accountStatusChanged":false,
              "principalReleased":0,
              "installments":[{
                "progress":{
                  "repaymentScheduleItemId":"00000000-0000-0000-0000-000000000105",
                  "repaymentScheduleId":"00000000-0000-0000-0000-000000000104",
                  "loanAccountId":"00000000-0000-0000-0000-000000000103",
                  "installmentNumber":1,
                  "principalPaid":0,"interestPaid":1,"feePaid":0,"totalPaid":1,
                  "principalOutstanding":500,"interestOutstanding":12,
                  "feeOutstanding":0,"totalOutstanding":512,
                  "status":"PARTIALLY_PAID",
                  "lastPaymentValueDate":"2026-07-28",
                  "lastPaymentRecordedAt":"2026-07-28T10:00:00",
                  "servicingEvaluationDate":"2026-07-28",
                  "updatedAt":"2026-07-28T10:00:00"
                },
                "previousStatus":"NOT_DUE",
                "statusChanged":true
              }]
            }
            """;

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void upgradesV33ToV34WithOnlyOutcomeAndAuditEntitySupport() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "33");
            assertFalse(tableExists(schema, "repayment_operation_outcomes"));
            String before = entityConstraint(schema);
            assertFalse(before.contains("REPAYMENT_TRANSACTION"));

            migrateTo(schema, "34");

            assertEquals("34", latestVersion(schema));
            assertTrue(tableExists(schema, "repayment_operation_outcomes"));
            assertTrue(entityConstraint(schema).contains("REPAYMENT_TRANSACTION"));
            assertTrue(entityConstraint(schema).contains("LOAN_ACCOUNT"));
            assertEquals(1, triggerCount(
                    schema, "trg_repayment_operation_outcomes_immutable"
            ));
            assertEquals(1, triggerCount(
                    schema, "trg_repayment_operation_outcome_reconcile"
            ));
            assertEquals(1, triggerCount(
                    schema, "trg_repayment_operation_transaction_completeness"
            ));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void cleanMigrationThroughV34Succeeds() {
        String schema = schemaName("clean");
        try {
            migrateTo(schema, "34");

            assertEquals("34", latestVersion(schema));
            assertTrue(tableExists(schema, "repayment_operation_outcomes"));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void incompatibleEntityConstraintFailsBeforeV34Mutation() throws Exception {
        String schema = schemaName("preflight");
        try {
            migrateTo(schema, "33");
            String original = entityConstraint(schema);
            jdbc.execute("alter table " + schema
                    + ".audit_events drop constraint chk_audit_events_entity_type");
            jdbc.execute("alter table " + schema
                    + ".audit_events add constraint chk_audit_events_entity_type check (("
                    + expression(original) + ") or entity_type = 'UNEXPECTED')");
            String incompatible = entityConstraint(schema);

            assertThrows(SQLException.class, () -> executeV34(schema));

            assertEquals(incompatible, entityConstraint(schema));
            assertFalse(tableExists(schema, "repayment_operation_outcomes"));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void weakenedEntityPredicateFailsBeforeV34Mutation() throws Exception {
        String schema = schemaName("predicate");
        try {
            migrateTo(schema, "33");
            String original = entityConstraint(schema);
            jdbc.execute("alter table " + schema
                    + ".audit_events drop constraint chk_audit_events_entity_type");
            jdbc.execute("alter table " + schema
                    + ".audit_events add constraint chk_audit_events_entity_type check (("
                    + expression(original) + ") or length(entity_type) > 0)");
            String incompatible = entityConstraint(schema);

            assertThrows(SQLException.class, () -> executeV34(schema));

            assertEquals(incompatible, entityConstraint(schema));
            assertFalse(tableExists(schema, "repayment_operation_outcomes"));
        } finally {
            dropSchema(schema);
        }
    }
    @Test
    void rejectsProhibitedKeysAtEveryDepthAndUnknownMetadata() {
        String schema = schemaName("json_keys");
        try {
            migrateTo(schema, "34");

            assertFalse(safeJson(schema, VALID_OUTCOME_JSON.replaceFirst(
                    "\\{", "{\"externalPaymentReference\":\"hidden\","
            )));
            assertFalse(safeJson(schema, VALID_OUTCOME_JSON.replace(
                    "\"accountBalance\":{",
                    "\"accountBalance\":{\"actorId\":\"hidden\","
            )));
            assertFalse(safeJson(schema, VALID_OUTCOME_JSON.replace(
                    "\"updatedAt\":\"2026-07-28T10:00:00\"",
                    "\"updatedAt\":\"2026-07-28T10:00:00\","
                            + "\"extra\":[{\"canonicalReference\":\"hidden\"}]"
            )));
            assertFalse(safeJson(schema, VALID_OUTCOME_JSON.replaceFirst(
                    "\\{", "{\"metadata\":{},"
            )));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void rejectsDuplicateInstallmentsAndWrongJsonTypes() {
        String schema = schemaName("json_shape");
        try {
            migrateTo(schema, "34");

            assertFalse(safeJson(schema, duplicateOnlyInstallment(VALID_OUTCOME_JSON)));
            assertFalse(safeJson(schema, VALID_OUTCOME_JSON.replace(
                    "\"receivedAmount\":1",
                    "\"receivedAmount\":\"1\""
            )));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void acceptsOnlyTheApprovedOutcomeShape() {
        String schema = schemaName("json_valid");
        try {
            migrateTo(schema, "34");

            assertTrue(safeJson(schema, VALID_OUTCOME_JSON));
        } finally {
            dropSchema(schema);
        }
    }
    private void executeV34(String schema) throws Exception {
        String sql = Files.readString(V34);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute(sql);
        }
    }

    private boolean safeJson(String schema, String json) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select " + schema
                        + ".is_repayment_operation_outcome_json_safe(cast(? as jsonb))",
                Boolean.class,
                json
        ));
    }

    private static String duplicateOnlyInstallment(String json) {
        String marker = "\"installments\":[";
        int start = json.indexOf(marker) + marker.length();
        int end = json.lastIndexOf(']');
        String installment = json.substring(start, end);
        return json.substring(0, start) + installment + "," + installment
                + json.substring(end);
    }
    private String entityConstraint(String schema) {
        return jdbc.queryForObject(
                "select pg_get_constraintdef(constraint_row.oid) "
                        + "from pg_constraint constraint_row "
                        + "join pg_class relation on relation.oid = constraint_row.conrelid "
                        + "join pg_namespace namespace "
                        + "on namespace.oid = relation.relnamespace "
                        + "where namespace.nspname = ? "
                        + "and relation.relname = 'audit_events' "
                        + "and constraint_row.conname = 'chk_audit_events_entity_type'",
                String.class, schema
        );
    }

    private int triggerCount(String schema, String triggerName) {
        return jdbc.queryForObject(
                "select count(*) from pg_trigger trigger_row "
                        + "join pg_class relation on relation.oid = trigger_row.tgrelid "
                        + "join pg_namespace namespace "
                        + "on namespace.oid = relation.relnamespace "
                        + "where namespace.nspname = ? and trigger_row.tgname = ? "
                        + "and not trigger_row.tgisinternal",
                Integer.class, schema, triggerName
        );
    }

    private boolean tableExists(String schema, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from information_schema.tables "
                        + "where table_schema = ? and table_name = ?)",
                Boolean.class, schema, table
        ));
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success "
                        + "order by installed_rank desc limit 1",
                String.class
        );
    }

    private void migrateTo(String schema, String target) {
        Flyway.configure().dataSource(dataSource).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration")
                .target(target).load().migrate();
    }

    private void dropSchema(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String expression(String definition) {
        return definition.substring("CHECK ((".length(), definition.length() - 2);
    }

    private static String schemaName(String suffix) {
        return "md_v34_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }
}
