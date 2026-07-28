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
    void cleanV1ThroughV29AcceptsEveryKnownActionAndRejectsUnknownAction() {
        assertEquals("29", latestVersion(SCHEMA));

        for (BusinessAuditAction action : BusinessAuditAction.values()) {
            insertAuditEvent(SCHEMA, action.name());
        }

        assertEquals(BusinessAuditAction.values().length, jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".audit_events",
                Integer.class
        ));
        assertThrows(DataAccessException.class, () ->
                insertAuditEvent(SCHEMA, "UNKNOWN_AUDIT_ACTION")
        );
    }

    @Test
    void upgradesV28ToV29WithoutChangingUnrelatedSchemaObjects() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "28");
            List<String> columnsBefore = columns(schema);
            List<String> indexesBefore = indexes(schema);
            List<String> constraintsBefore = unrelatedConstraints(schema);

            migrateLatest(schema);

            assertEquals("29", latestVersion(schema));
            assertEquals(columnsBefore, columns(schema));
            assertEquals(indexesBefore, indexes(schema));
            assertEquals(constraintsBefore, unrelatedConstraints(schema));
            insertAuditEvent(schema, "MANUAL_DISBURSEMENT_CONFIRMED");
            assertThrows(DataAccessException.class, () ->
                    insertAuditEvent(schema, "UNKNOWN_AUDIT_ACTION")
            );
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void v29ConstraintRetainsEveryPreviouslyAllowedAction() {
        for (BusinessAuditAction action : BusinessAuditAction.values()) {
            if (action != BusinessAuditAction.MANUAL_DISBURSEMENT_CONFIRMED) {
                insertAuditEvent(SCHEMA, action.name());
            }
        }

        assertTrue(jdbc.queryForObject(
                "select count(*) > 0 from " + SCHEMA
                        + ".audit_events where action = 'LOAN_CONTRACT_READINESS_CONFIRMED'",
                Boolean.class
        ));
    }

    private void insertAuditEvent(String schema, String action) {
        UUID operationId = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + ".audit_events "
                        + "(id,operation_id,sequence_number,actor_type,actor_user_id,"
                        + "entity_type,entity_id,action,payload,occurred_at) "
                        + "values (?,?,1,'USER',?,'LOAN_APPLICATION',?,?,?::jsonb,?)",
                UUID.randomUUID(),
                operationId,
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

    private void migrateLatest(String schema) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static String schemaName(String suffix) {
        return "md_v29_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }
}
