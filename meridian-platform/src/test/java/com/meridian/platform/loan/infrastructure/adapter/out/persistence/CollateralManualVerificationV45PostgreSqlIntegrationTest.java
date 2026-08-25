package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class CollateralManualVerificationV45PostgreSqlIntegrationTest {

    private static final UUID REVIEWER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upgradesV44DataAndBackfillsFirstVerificationSequence() {
        String schema = schema("upgrade");
        UUID verificationId = UUID.randomUUID();
        try {
            migrate(schema, "44");
            Seed seed = seedApplication(schema, verificationId);

            migrate(schema, null);

            assertEquals("49", latestVersion(schema));
            assertEquals(1, jdbc.queryForObject(
                    "select verification_sequence from " + schema
                            + ".collateral_loan_verifications where id = ?",
                    Integer.class,
                    verificationId
            ));
            assertNull(jdbc.queryForObject(
                    "select source_correction_request_id from " + schema
                            + ".collateral_loan_verifications where id = ?",
                    UUID.class,
                    verificationId
            ));
            assertEquals(seed.applicationId(), jdbc.queryForObject(
                    "select loan_application_id from " + schema
                            + ".collateral_loan_verifications where id = ?",
                    UUID.class,
                    verificationId
            ));
            assertCurrentShape(schema);
        } finally {
            drop(schema);
        }
    }

    @Test
    void enforcesCompletionImmutabilitySequenceAndDeferredSourceReconciliation() {
        String schema = schema("integrity");
        UUID firstVerificationId = UUID.randomUUID();
        try {
            migrate(schema, null);
            Seed seed = seedApplication(schema, firstVerificationId);
            complete(schema, firstVerificationId, "VERIFIED", "Initial assessment.");

            assertThrows(DataAccessException.class, () -> complete(
                    schema, firstVerificationId, "FAILED", "Second assessment."
            ));
            assertThrows(DataAccessException.class, () -> execute(schema,
                    "delete from collateral_loan_verifications where id = '"
                            + firstVerificationId + "'"
            ));
            assertThrows(DataAccessException.class, () -> execute(schema,
                    pendingVerificationInsert(
                            UUID.randomUUID(), seed.applicationId(), 1, null,
                            "2026-08-19 09:00:00"
                    )
            ));

            UUID correctionId = UUID.randomUUID();
            UUID secondVerificationId = UUID.randomUUID();
            execute(schema,
                    resubmittedCorrectionInsert(correctionId, seed.applicationId())
                            + pendingVerificationInsert(
                            secondVerificationId, seed.applicationId(), 2, correctionId,
                            "2026-08-19 10:00:00"
                    )
            );
            assertEquals(2, jdbc.queryForObject(
                    "select verification_sequence from " + schema
                            + ".collateral_loan_verifications where id = ?",
                    Integer.class,
                    secondVerificationId
            ));

            complete(schema, secondVerificationId, "VERIFIED", "Corrected evidence assessment.");
            assertThrows(DataAccessException.class, () -> execute(schema,
                    pendingVerificationInsert(
                            UUID.randomUUID(), seed.applicationId(), 3, correctionId,
                            "2026-08-19 11:00:00"
                    )
            ));

            UUID openCorrectionId = UUID.randomUUID();
            assertThrows(DataAccessException.class, () -> execute(schema,
                    openCorrectionInsert(openCorrectionId, seed.applicationId())
                            + pendingVerificationInsert(
                            UUID.randomUUID(), seed.applicationId(), 3, openCorrectionId,
                            "2026-08-19 11:00:00"
                    )
            ));
            assertEquals(2, jdbc.queryForObject(
                    "select count(*) from " + schema
                            + ".collateral_loan_verifications where loan_application_id = ?",
                    Integer.class,
                    seed.applicationId()
            ));
        } finally {
            drop(schema);
        }
    }

    @Test
    void currentSchemaMigrationIsRepeatableWithoutFurtherEffects() {
        String schema = schema("current");
        try {
            migrate(schema, null);
            MigrateResult replay = migrate(schema, null);

            assertEquals("49", latestVersion(schema));
            assertEquals(0, replay.migrationsExecuted);
            assertCurrentShape(schema);
        } finally {
            drop(schema);
        }
    }

    private Seed seedApplication(String schema, UUID verificationId) {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID productId = jdbc.queryForObject(
                "select id from " + schema
                        + ".loan_products where product_code = 'COLLATERAL_LOAN'",
                UUID.class
        );
        execute(schema,
                "insert into customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "values ('" + customerId + "', 'MER-V45-" + customerId
                        + "', 'ACTIVE', 'VERIFIED', 'COMPLETE'); "
                        + "insert into loan_applications "
                        + "(id, customer_id, loan_product_id, application_number, product_code, "
                        + "product_type, status, requested_amount, requested_term_months, submitted_at) "
                        + "values ('" + applicationId + "', '" + customerId + "', '" + productId
                        + "', 'CL-V45-" + applicationId
                        + "', 'COLLATERAL_LOAN', 'SECURED', 'SUBMITTED', 15000000, 12, "
                        + "timestamp '2026-08-19 08:00:00'); "
                        + "insert into collateral_loan_verifications "
                        + legacyOrCurrentVerificationColumns(schema)
                        + " values ('" + verificationId + "', '" + applicationId + "', "
                        + legacyOrCurrentVerificationValues(schema)
        );
        return new Seed(applicationId);
    }

    private String legacyOrCurrentVerificationColumns(String schema) {
        if (columnExists(schema, "collateral_loan_verifications", "verification_sequence")) {
            return "(id, loan_application_id, verification_sequence, source_correction_request_id, "
                    + "product_verification_result, created_at, reviewed_by_user_id, reviewed_at, assessment_note)";
        }
        return "(id, loan_application_id, product_verification_result, created_at)";
    }

    private String legacyOrCurrentVerificationValues(String schema) {
        if (columnExists(schema, "collateral_loan_verifications", "verification_sequence")) {
            return "1, null, 'PENDING_MANUAL_REVIEW', timestamp '2026-08-19 08:00:00', null, null, null);";
        }
        return "'PENDING_MANUAL_REVIEW', timestamp '2026-08-19 08:00:00');";
    }

    private void complete(String schema, UUID verificationId, String result, String note) {
        execute(schema,
                "update collateral_loan_verifications set product_verification_result = '"
                        + result + "', reviewed_by_user_id = '" + REVIEWER_ID
                        + "', reviewed_at = timestamp '2026-08-19 12:00:00', assessment_note = '"
                        + note + "' where id = '" + verificationId + "'"
        );
    }

    private String pendingVerificationInsert(
            UUID verificationId,
            UUID applicationId,
            int sequence,
            UUID correctionId,
            String createdAt
    ) {
        return "insert into collateral_loan_verifications "
                + "(id, loan_application_id, verification_sequence, source_correction_request_id, "
                + "product_verification_result, created_at) values ('" + verificationId + "', '"
                + applicationId + "', " + sequence + ", "
                + (correctionId == null ? "null" : "'" + correctionId + "'")
                + ", 'PENDING_MANUAL_REVIEW', timestamp '" + createdAt + "'); ";
    }

    private String resubmittedCorrectionInsert(UUID correctionId, UUID applicationId) {
        UUID requestId = UUID.randomUUID();
        return "insert into loan_correction_requests "
                + "(id, loan_application_id, source_action, reason_code, created_by_user_id, status, "
                + "resubmission_request_id, created_at, ready_at, resubmitted_at, updated_at) values ('"
                + correctionId + "', '" + applicationId
                + "', 'COMPLETE_PRODUCT_VERIFICATION', 'DOCUMENT_REPLACEMENT_REQUIRED', '"
                + REVIEWER_ID + "', 'RESUBMITTED', '" + requestId
                + "', timestamp '2026-08-19 09:30:00', timestamp '2026-08-19 09:40:00', "
                + "timestamp '2026-08-19 09:50:00', timestamp '2026-08-19 09:50:00'); ";
    }

    private String openCorrectionInsert(UUID correctionId, UUID applicationId) {
        return "insert into loan_correction_requests "
                + "(id, loan_application_id, source_action, reason_code, created_by_user_id, status, "
                + "created_at, updated_at) values ('" + correctionId + "', '" + applicationId
                + "', 'COMPLETE_PRODUCT_VERIFICATION', 'DOCUMENT_REPLACEMENT_REQUIRED', '"
                + REVIEWER_ID + "', 'OPEN', timestamp '2026-08-19 10:30:00', "
                + "timestamp '2026-08-19 10:30:00'); ";
    }

    private void assertCurrentShape(String schema) {
        assertTrue(columnExists(schema, "collateral_loan_verifications", "verification_sequence"));
        assertTrue(columnExists(schema, "collateral_loan_verifications", "source_correction_request_id"));
        assertTrue(columnExists(schema, "collateral_loan_verifications", "reviewed_by_user_id"));
        assertTrue(columnExists(schema, "collateral_loan_verifications", "assessment_note"));
        assertEquals(1, triggerCount(schema, "trg_collateral_verification_cycles_immutable"));
        assertEquals(1, triggerCount(schema, "trg_collateral_verification_cycles_reconcile_source"));
    }

    private void execute(String schema, String statements) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("set local search_path to " + schema + ", public");
                statement.execute(statements);
                connection.commit();
                return null;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        });
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
        return "mer_cl_v45_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private void drop(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private record Seed(UUID applicationId) {
    }
}
