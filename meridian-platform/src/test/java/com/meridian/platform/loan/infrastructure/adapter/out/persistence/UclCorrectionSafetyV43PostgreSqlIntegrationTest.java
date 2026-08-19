package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class UclCorrectionSafetyV43PostgreSqlIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upgradesV37ToCurrent() {
        assertUpgradeFrom("37", false);
    }

    @Test
    void upgradesV38DataAndBackfillsFirstVerificationSequence() {
        assertUpgradeFrom("38", true);
    }

    @Test
    void upgradesV39DecisionEvidenceAndBackfillsFirstVerificationSequence() {
        assertUpgradeFrom("39", true);
    }

    @Test
    void currentSchemaMigrationIsRepeatableWithoutFurtherEffects() {
        String schema = schema("current");
        try {
            migrate(schema, null);
            MigrateResult replay = migrate(schema, null);

            assertEquals("46", latestVersion(schema));
            assertEquals(0, replay.migrationsExecuted);
            assertCurrentShape(schema);
        } finally {
            drop(schema);
        }
    }

    private void assertUpgradeFrom(String version, boolean seedLegacyVerification) {
        String schema = schema("from_" + version);
        UUID verificationId = null;
        try {
            migrate(schema, version);
            if (seedLegacyVerification) {
                verificationId = seedLegacyVerification(schema, version);
            }

            migrate(schema, null);

            assertEquals("46", latestVersion(schema));
            assertCurrentShape(schema);
            if (verificationId != null) {
                assertEquals(1, jdbc.queryForObject(
                        "select verification_sequence from " + schema
                                + ".unsecured_consumer_loan_verifications where id = ?",
                        Integer.class,
                        verificationId
                ));
                assertNull(jdbc.queryForObject(
                        "select source_correction_request_id from " + schema
                                + ".unsecured_consumer_loan_verifications where id = ?",
                        UUID.class,
                        verificationId
                ));
            }
        } finally {
            drop(schema);
        }
    }

    private UUID seedLegacyVerification(String schema, String version) {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID productId = jdbc.queryForObject(
                "select id from " + schema
                        + ".loan_products where product_code = 'UNSECURED_CONSUMER_LOAN'",
                UUID.class
        );
        StringBuilder seed = new StringBuilder()
                .append("begin; set local search_path to ").append(schema).append(", public; ")
                .append("insert into customers ")
                .append("(id, customer_number, status, verification_status, ")
                .append("profile_completion_status) values ('").append(customerId)
                .append("', 'MER-V43-").append(customerId)
                .append("', 'ACTIVE', 'VERIFIED', 'COMPLETE'); ")
                .append("insert into loan_applications ")
                .append("(id, customer_id, loan_product_id, application_number, product_code, ")
                .append("product_type, status, requested_amount, requested_term_months, submitted_at) ")
                .append("values ('").append(applicationId).append("', '").append(customerId)
                .append("', '").append(productId).append("', 'UCL-V43-").append(applicationId)
                .append("', 'UNSECURED_CONSUMER_LOAN', 'UNSECURED', 'SUBMITTED', ")
                .append("5000000, 6, timestamp '2026-08-12 00:00:00'); ");
        if ("38".equals(version)) {
            seed.append("insert into unsecured_consumer_loan_verifications ")
                    .append("(id, loan_application_id, product_verification_result, created_at) ")
                    .append("values ('").append(verificationId).append("', '")
                    .append(applicationId)
                    .append("', 'PENDING_MANUAL_REVIEW', timestamp '2026-08-12 00:00:00'); ");
        } else {
            seed.append("insert into unsecured_consumer_loan_verifications ")
                    .append("(id, loan_application_id, product_verification_result, created_at, ")
                    .append("reviewed_by_user_id, reviewed_at, assessment_note) ")
                    .append("values ('").append(verificationId).append("', '")
                    .append(applicationId)
                    .append("', 'VERIFIED', timestamp '2026-08-12 00:00:00', ")
                    .append("'00000000-0000-0000-0000-000000000302', ")
                    .append("timestamp '2026-08-12 01:00:00', 'Legacy verified evidence'); ");
        }
        seed.append("commit;");
        jdbc.execute(seed.toString());
        return verificationId;
    }

    private void assertCurrentShape(String schema) {
        assertTrue(columnExists(schema, "unsecured_consumer_loan_verifications",
                "verification_sequence"));
        assertTrue(columnExists(schema, "unsecured_consumer_loan_verifications",
                "source_correction_request_id"));
        assertEquals("YES", jdbc.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = ? and table_name = 'loan_application_cancellations' "
                        + "and column_name = 'reservation_release_movement_id'",
                String.class,
                schema
        ));
        assertEquals(1, triggerCount(schema, "trg_ucl_verification_cycles_immutable"));
        assertEquals(1, triggerCount(schema, "trg_ucl_verification_cycles_reconcile_source"));
    }

    private MigrateResult migrate(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load().migrate();
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

    private int triggerCount(String schema, String trigger) {
        return jdbc.queryForObject(
                "select count(*) from pg_trigger trigger_row "
                        + "join pg_class relation on relation.oid = trigger_row.tgrelid "
                        + "join pg_namespace namespace on namespace.oid = relation.relnamespace "
                        + "where namespace.nspname=? and trigger_row.tgname=? "
                        + "and not trigger_row.tgisinternal",
                Integer.class,
                schema,
                trigger
        );
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success order by installed_rank desc limit 1",
                String.class
        );
    }

    private String schema(String suffix) {
        return "meridian_ucl_v43_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private void drop(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }
}
