package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class ManualDisbursementAuditV29PostgreSqlIntegrationTest {

    private static final String SCHEMA = schemaName("installed");
    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final LocalDateTime OCCURRED_AT =
            LocalDateTime.of(2026, 7, 28, 10, 0);
    private static final Path V29 = Path.of(
            "src/main/resources/db/migration/V29__add_manual_disbursement_audit_action.sql"
    );

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void installedLatestRetainsEveryKnownAuditActionAndRejectsUnknownAction() {
        assertEquals("49", latestVersion(SCHEMA));
        assertAllKnownActionsAccepted(SCHEMA);
    }

    @Test
    void cleanV1ThroughV29AcceptsEveryKnownActionAndRejectsUnknownAction() {
        String schema = schemaName("clean29");
        try {
            migrateTo(schema, "29");
            assertEquals("29", latestVersion(schema));
            assertActionsThroughV29Accepted(schema);
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void upgradesV28ToV29WithoutChangingUnrelatedSchemaObjects() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "28");
            List<String> columnsBefore = columns(schema);
            List<String> indexesBefore = indexes(schema);
            List<String> constraintsBefore = unrelatedConstraints(schema);

            migrateTo(schema, "29");

            assertEquals("29", latestVersion(schema));
            assertEquals(columnsBefore, columns(schema));
            assertEquals(indexesBefore, indexes(schema));
            assertEquals(constraintsBefore, unrelatedConstraints(schema));
            assertActionsThroughV29Accepted(schema);
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void repeatedV29ExecutionFailsBeforeReplacingTheInstalledConstraint() throws Exception {
        String schema = schemaName("repeat");
        try {
            migrateTo(schema, "29");
            String before = actionConstraint(schema);

            assertThrows(SQLException.class, () -> executeV29(schema));

            assertEquals(before, actionConstraint(schema));
            assertTrue(before.contains("MANUAL_DISBURSEMENT_CONFIRMED"));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void incompatibleSameNameConstraintFailsWithoutReplacement() throws Exception {
        String schema = schemaName("incompatible");
        try {
            migrateTo(schema, "28");
            String expectedDefinition = actionConstraint(schema);
            String expectedExpression = expectedDefinition.substring(
                    "CHECK (".length(), expectedDefinition.length() - 1);
            jdbc.execute("alter table " + schema
                    + ".audit_events drop constraint chk_audit_events_action");
            jdbc.execute("alter table " + schema
                    + ".audit_events add constraint chk_audit_events_action check (("
                    + expectedExpression + ") or action is not null)");
            String before = actionConstraint(schema);

            assertThrows(SQLException.class, () -> executeV29(schema));

            assertEquals(before, actionConstraint(schema));
            assertTrue(before.contains("CUSTOMER_PROFILE_CREATED"));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void extraLiteralActionFailsWithoutReplacingTheConstraint() throws Exception {
        assertPredicateWeakeningRejected(
                "extra_literal",
                "action = 'V29_EXTRA_ACTION'"
        );
    }

    @Test
    void nonLiteralLengthWeakeningFailsWithoutReplacingTheConstraint() throws Exception {
        assertPredicateWeakeningRejected(
                "length_weakening",
                "length(action) = 1"
        );
    }

    @Test
    void reorderedExpectedWhitelistStillUpgrades() throws Exception {
        String schema = schemaName("reordered");
        try {
            migrateTo(schema, "28");
            String expectedDefinition = actionConstraint(schema);
            String expectedExpression = expectedDefinition.substring(
                    "CHECK (".length(), expectedDefinition.length() - 1);
            String reorderedExpression = expectedExpression
                    .replace("'CUSTOMER_PROFILE_CREATED'", "'__V29_SWAP__'")
                    .replace("'CUSTOMER_PROFILE_UPDATED'", "'CUSTOMER_PROFILE_CREATED'")
                    .replace("'__V29_SWAP__'", "'CUSTOMER_PROFILE_UPDATED'");
            replaceActionConstraint(schema, reorderedExpression);

            executeV29(schema);

            assertTrue(actionConstraint(schema).contains("MANUAL_DISBURSEMENT_CONFIRMED"));
        } finally {
            dropSchema(schema);
        }
    }

    private void assertPredicateWeakeningRejected(
            String suffix,
            String extraPredicate
    ) throws Exception {
        String schema = schemaName(suffix);
        try {
            migrateTo(schema, "28");
            String expectedDefinition = actionConstraint(schema);
            String expectedExpression = expectedDefinition.substring(
                    "CHECK (".length(), expectedDefinition.length() - 1);
            replaceActionConstraint(
                    schema,
                    "(" + expectedExpression + ") or " + extraPredicate
            );
            String before = actionConstraint(schema);

            assertThrows(SQLException.class, () -> executeV29(schema));

            assertEquals(before, actionConstraint(schema));
        } finally {
            dropSchema(schema);
        }
    }

    private void replaceActionConstraint(String schema, String constraintExpression) {
        jdbc.execute("alter table " + schema
                + ".audit_events drop constraint chk_audit_events_action");
        jdbc.execute("alter table " + schema
                + ".audit_events add constraint chk_audit_events_action check ("
                + constraintExpression + ")");
    }

    @Test
    void missingExpectedConstraintFailsClearly() throws Exception {
        String schema = schemaName("missing");
        try {
            migrateTo(schema, "28");
            jdbc.execute("alter table " + schema
                    + ".audit_events drop constraint chk_audit_events_action");

            assertThrows(SQLException.class, () -> executeV29(schema));

            assertEquals(0, jdbc.queryForObject(
                    "select count(*) from pg_constraint constraint_row "
                            + "join pg_class relation on relation.oid = constraint_row.conrelid "
                            + "join pg_namespace namespace on namespace.oid = relation.relnamespace "
                            + "where namespace.nspname = ? and relation.relname = 'audit_events' "
                            + "and constraint_row.conname = 'chk_audit_events_action'",
                    Integer.class,
                    schema
            ));
        } finally {
            dropSchema(schema);
        }
    }

    private void assertAllKnownActionsAccepted(String schema) {
        for (BusinessAuditAction action : BusinessAuditAction.values()) {
            insertAuditEvent(schema, action.name());
        }
        assertThrows(DataAccessException.class, () ->
                insertAuditEvent(schema, "UNKNOWN_AUDIT_ACTION")
        );
    }

    private void assertActionsThroughV29Accepted(String schema) {
        for (BusinessAuditAction action : BusinessAuditAction.values()) {
            if (action == BusinessAuditAction
                    .LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED
                    || action == BusinessAuditAction.UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED
                    || action == BusinessAuditAction.COLLATERAL_LOAN_APPLICATION_SUBMITTED
                    || action == BusinessAuditAction.UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED
                    || action == BusinessAuditAction.UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED
                    || action == BusinessAuditAction.COLLATERAL_LOAN_VERIFICATION_STARTED
                    || action == BusinessAuditAction.COLLATERAL_LOAN_VERIFICATION_COMPLETED
                    || action == BusinessAuditAction.LOAN_APPLICATION_CANCELLED
                    || action == BusinessAuditAction.REPAYMENT_RECORDED
                    || action == BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED
                    || action == BusinessAuditAction.LOAN_SETTLEMENT_APPROVED
                    || action == BusinessAuditAction.LOAN_ACCOUNT_CLOSED) {
                continue;
            }
            insertAuditEvent(schema, action.name());
        }
        assertThrows(DataAccessException.class, () -> insertAuditEvent(
                schema,
                BusinessAuditAction.COLLATERAL_LOAN_APPLICATION_SUBMITTED.name()
        ));
        assertThrows(DataAccessException.class, () ->
                insertAuditEvent(schema, "UNKNOWN_AUDIT_ACTION")
        );
    }

    private void executeV29(String schema) throws Exception {
        String sql = Files.readString(V29);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute(sql);
        }
    }

    private String actionConstraint(String schema) {
        return jdbc.queryForObject(
                "select pg_get_constraintdef(constraint_row.oid) "
                        + "from pg_constraint constraint_row "
                        + "join pg_class relation on relation.oid = constraint_row.conrelid "
                        + "join pg_namespace namespace on namespace.oid = relation.relnamespace "
                        + "where namespace.nspname = ? and relation.relname = 'audit_events' "
                        + "and constraint_row.conname = 'chk_audit_events_action'",
                String.class,
                schema
        );
    }

    private void insertAuditEvent(String schema, String action) {
        jdbc.update(
                "insert into " + schema + ".audit_events "
                        + "(id,operation_id,sequence_number,actor_type,actor_user_id,"
                        + "entity_type,entity_id,action,payload,occurred_at) "
                        + "values (?,?,1,'USER',?,'LOAN_APPLICATION',?,?,?::jsonb,?)",
                UUID.randomUUID(),
                UUID.randomUUID(),
                ACCOUNTING_USER_ID,
                UUID.randomUUID(),
                action,
                "{}",
                OCCURRED_AT
        );
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success "
                        + "order by installed_rank desc limit 1",
                String.class
        );
    }

    private List<String> columns(String schema) {
        return rows(jdbc.queryForList(
                "select table_name,column_name,data_type,is_nullable,"
                        + "coalesce(column_default,'') as column_default "
                        + "from information_schema.columns where table_schema = ? "
                        + "order by table_name,ordinal_position",
                schema
        ));
    }

    private List<String> indexes(String schema) {
        return rows(jdbc.queryForList(
                "select tablename,indexname,indexdef from pg_indexes "
                        + "where schemaname = ? order by tablename,indexname",
                schema
        ));
    }

    private List<String> unrelatedConstraints(String schema) {
        return rows(jdbc.queryForList(
                "select relation.relname as table_name,constraint_row.conname,"
                        + "pg_get_constraintdef(constraint_row.oid) as definition "
                        + "from pg_constraint constraint_row "
                        + "join pg_class relation on relation.oid = constraint_row.conrelid "
                        + "join pg_namespace namespace on namespace.oid = relation.relnamespace "
                        + "where namespace.nspname = ? "
                        + "and constraint_row.conname <> 'chk_audit_events_action' "
                        + "order by relation.relname,constraint_row.conname",
                schema
        ));
    }

    private static List<String> rows(List<Map<String, Object>> rows) {
        List<String> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            normalized.add(row.toString());
        }
        return List.copyOf(normalized);
    }

    private void migrateTo(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void dropSchema(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String schemaName(String suffix) {
        return "md_v29_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }
}
