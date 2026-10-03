package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class LoanReviewStewardshipV65MigrationTest {
    private static final String CONTEXT_SCHEMA = schemaName("context");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V65__add_loan_review_stewardship.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.password", () ->
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "meridian156"));
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }

    @Test
    void backfillsExactRecommendationAndPropagatesOnlyProvenStewardship() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "64");
            UUID officerId = UUID.randomUUID();
            UUID firstApplicationId = UUID.randomUUID();
            UUID startedApplicationId = UUID.randomUUID();
            UUID unresolvedApplicationId = UUID.randomUUID();
            UUID firstCycleId = UUID.randomUUID();
            UUID continuationCycleId = UUID.randomUUID();
            UUID startedCycleId = UUID.randomUUID();
            UUID unresolvedCycleId = UUID.randomUUID();
            seedStaff(schema, officerId);
            seedApplication(schema, firstApplicationId, "UCL-V65-1");
            seedApplication(schema, startedApplicationId, "UCL-V65-START");
            seedApplication(schema, unresolvedApplicationId, "UCL-V65-2");
            seedCycle(schema, firstCycleId, firstApplicationId, 1, "SUPERSEDED");
            seedCycle(schema, continuationCycleId, firstApplicationId, 2, "ACTIVE");
            seedCycle(schema, startedCycleId, startedApplicationId, 1, "ACTIVE");
            seedCycle(schema, unresolvedCycleId, unresolvedApplicationId, 1, "ACTIVE");
            seedTransition(schema, startedApplicationId, 1, null, "SUBMITTED",
                    "SUBMIT_APPLICATION", officerId, "2026-09-01 08:00:00");
            seedTransition(schema, startedApplicationId, 2, "SUBMITTED", "UNDER_REVIEW",
                    "START_REVIEW", officerId, "2026-09-01 08:30:00");
            jdbcTemplate.update("""
                    INSERT INTO %s.review_recommendations (
                        id, loan_application_id, review_cycle_id, loan_officer_user_id,
                        recommendation, submitted_at
                    ) VALUES (?, ?, ?, ?, 'RECOMMEND_APPROVAL', '2026-09-01 09:00:00')
                    """.formatted(schema), UUID.randomUUID(), firstApplicationId, firstCycleId, officerId);

            assertEquals(1, migrateTo(schema, "65"));

            List<UUID> assigned = jdbcTemplate.queryForList(
                    "SELECT assigned_loan_officer_user_id FROM " + schema
                            + ".loan_application_review_cycles WHERE loan_application_id = ? ORDER BY cycle_number",
                    UUID.class,
                    firstApplicationId
            );
            assertEquals(List.of(officerId, officerId), assigned);
            assertEquals(officerId, jdbcTemplate.queryForObject(
                    "SELECT assigned_loan_officer_user_id FROM " + schema
                            + ".loan_application_review_cycles WHERE id = ?",
                    UUID.class,
                    startedCycleId
            ));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT assigned_loan_officer_user_id FROM " + schema
                            + ".loan_application_review_cycles WHERE id = ?",
                    UUID.class,
                    unresolvedCycleId
            ));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
                    Integer.class,
                    schema,
                    "idx_loan_review_cycles_assigned_officer"
            ));
            assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                    "UPDATE " + schema
                            + ".loan_application_review_cycles SET assigned_loan_officer_user_id = ? WHERE id = ?",
                    UUID.randomUUID(),
                    unresolvedCycleId
            ));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationAndSnapshotDescribeTheSameV65Contract() throws IOException {
        String migration = normalized(MIGRATION);
        String snapshot = normalized(CURRENT_SCHEMA);

        assertTrue(migration.contains("assigned_loan_officer_user_id UUID"));
        assertTrue(migration.contains("fk_loan_review_cycles_assigned_loan_officer"));
        assertTrue(migration.contains("idx_loan_review_cycles_assigned_officer"));
        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V69"));
        assertTrue(snapshot.contains("idx_loan_review_cycles_assigned_officer"));
    }

    private void seedStaff(String schema, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO %s.users (
                    id, email, normalized_email, password_hash, user_type, status, display_name
                ) VALUES (?, ?, ?, 'hash', 'STAFF', 'ACTIVE', 'Deni Loan Officer')
                """.formatted(schema), userId, userId + "@meridian.local", userId + "@meridian.local");
    }

    private void seedApplication(String schema, UUID applicationId, String applicationNumber) {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO %s.customers (
                    id, customer_number, status, verification_status, profile_completion_status
                ) VALUES (?, ?, 'ACTIVE', 'VERIFIED', 'COMPLETE')
                """.formatted(schema), customerId, "CUS-" + applicationId);
        UUID productId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".loan_products WHERE product_code = 'UNSECURED_CONSUMER_LOAN'",
                UUID.class
        );
        jdbcTemplate.update("""
                INSERT INTO %s.loan_applications (
                    id, customer_id, loan_product_id, application_number, product_code,
                    product_type, status, requested_amount, requested_term_months, submitted_at
                ) VALUES (?, ?, ?, ?, 'UNSECURED_CONSUMER_LOAN', 'UNSECURED',
                    'UNDER_REVIEW', 5000000, 6, '2026-09-01 08:00:00')
                """.formatted(schema), applicationId, customerId, productId, applicationNumber);
    }

    private void seedCycle(
            String schema,
            UUID cycleId,
            UUID applicationId,
            int cycleNumber,
            String status
    ) {
        jdbcTemplate.update("""
                INSERT INTO %s.loan_application_review_cycles (
                    id, loan_application_id, cycle_number, status, started_at, ended_at
                ) VALUES (?, ?, ?, ?, '2026-09-01 08:30:00',
                    CASE WHEN ? = 'ACTIVE' THEN NULL ELSE CAST('2026-09-01 09:30:00' AS TIMESTAMP) END)
                """.formatted(schema), cycleId, applicationId, cycleNumber, status, status);
    }

    private void seedTransition(
            String schema,
            UUID applicationId,
            int sequenceNumber,
            String fromStatus,
            String toStatus,
            String action,
            UUID actorUserId,
            String occurredAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO %s.loan_application_status_transitions (
                    id, loan_application_id, operation_id, sequence_number, from_status,
                    to_status, action, actor_type, actor_user_id, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'USER', ?, CAST(? AS TIMESTAMP))
                """.formatted(schema), UUID.randomUUID(), applicationId, UUID.randomUUID(),
                sequenceNumber, fromStatus, toStatus, action, actorUserId, occurredAt);
    }

    private int migrateTo(String schema, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate()
                .migrationsExecuted;
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static String schemaName(String suffix) {
        return "meridian_v65_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
