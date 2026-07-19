package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class CustomerCorrectionV23PostgreSqlIntegrationTest {
    private static final String CONTEXT_SCHEMA = schemaName("context");

    private static final UUID LOAN_OFFICER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> CONTEXT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> CONTEXT_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + CONTEXT_SCHEMA);
    }


    @Test
    void upgradesV21AndBackfillsDeterministicReviewCycleStates() {
        String schema = schemaName("backfill");
        try {
            migrateTo(schema, "21");
            UUID submitted = insertApplication(schema, "SUBMITTED");
            UUID underReview = insertApplication(schema, "UNDER_REVIEW");
            UUID approvalPending = insertApplication(schema, "APPROVAL_PENDING");
            UUID approved = insertApplication(schema, "APPROVED");
            UUID returnedToReview = insertApplication(schema, "RETURNED_TO_REVIEW");
            UUID rejected = insertApplication(schema, "REJECTED");
            UUID contractPending = insertApplication(schema, "CONTRACT_PENDING");
            UUID multipleCycles = insertApplication(schema, "APPROVAL_PENDING");

            insertRecommendation(schema, approvalPending, "RECOMMEND_APPROVAL", 1);
            UUID approvedRecommendation = insertRecommendation(schema, approved, "RECOMMEND_APPROVAL", 1);
            insertDecision(schema, approved, approvedRecommendation, "APPROVE", 2);
            UUID returnedRecommendation = insertRecommendation(schema, returnedToReview, "RECOMMEND_APPROVAL", 1);
            insertDecision(schema, returnedToReview, returnedRecommendation, "RETURN_TO_LOAN_OFFICER_REVIEW", 2);
            UUID rejectedRecommendation = insertRecommendation(schema, rejected, "RECOMMEND_REJECTION", 1);
            insertDecision(schema, rejected, rejectedRecommendation, "REJECT", 2);
            UUID contractRecommendation = insertRecommendation(schema, contractPending, "RECOMMEND_APPROVAL", 1);
            insertDecision(schema, contractPending, contractRecommendation, "APPROVE", 2);
            UUID first = insertRecommendation(schema, multipleCycles, "RECOMMEND_APPROVAL", 1);
            insertDecision(schema, multipleCycles, first, "RETURN_TO_LOAN_OFFICER_REVIEW", 2);
            insertRecommendation(schema, multipleCycles, "RECOMMEND_APPROVAL", 3);

            assertEquals(3, migrateLatest(schema));
            assertCycleCount(schema, submitted, 0);
            assertCycle(schema, underReview, 1, "ACTIVE");
            assertCycle(schema, approvalPending, 1, "ACTIVE");
            assertCycle(schema, approved, 1, "COMPLETED");
            assertCycle(schema, returnedToReview, 1, "SUPERSEDED");
            assertCycle(schema, returnedToReview, 2, "ACTIVE");
            assertCycle(schema, rejected, 1, "COMPLETED");
            assertCycle(schema, contractPending, 1, "COMPLETED");
            assertCycle(schema, multipleCycles, 1, "SUPERSEDED");
            assertCycle(schema, multipleCycles, 2, "ACTIVE");

            assertEquals(0, count(schema,
                    "SELECT count(*) FROM review_recommendations WHERE review_cycle_id IS NULL"));
            assertEquals(0, count(schema,
                    "SELECT count(*) FROM document_checklists WHERE loan_application_id IN (?, ?, ?, ?, ?, ?, ?, ?)",
                    submitted, underReview, approvalPending, approved, returnedToReview,
                    rejected, contractPending, multipleCycles) - 8);
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void rejectsAmbiguousMultipleUnresolvedRecommendationsWithClearPreflight() {
        String schema = schemaName("ambiguous");
        try {
            migrateTo(schema, "21");
            UUID applicationId = insertApplication(schema, "APPROVAL_PENDING");
            insertRecommendation(schema, applicationId, "RECOMMEND_APPROVAL", 1);
            insertRecommendation(schema, applicationId, "RECOMMEND_REJECTION", 2);

            FlywayException exception = assertThrows(FlywayException.class, () -> migrateLatest(schema));
            assertTrue(rootMessage(exception).contains("multiple unresolved recommendations"));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void enforcesSingleActiveCycleRecommendationDecisionAndNullSafeTaskTuple() {
        String schema = schemaName("constraints");
        try {
            migrateTo(schema, "23");
            UUID applicationId = insertApplication(schema, "UNDER_REVIEW");
            UUID cycleId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + schema + ".loan_application_review_cycles "
                            + "(id, loan_application_id, cycle_number, status, started_at, created_at, updated_at) "
                            + "VALUES (?, ?, 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    cycleId, applicationId);

            assertThrows(Exception.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".loan_application_review_cycles "
                            + "(loan_application_id, cycle_number, status, started_at, created_at, updated_at) "
                            + "VALUES (?, 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    applicationId));

            UUID recommendationId = insertV23Recommendation(schema, applicationId, cycleId);
            assertThrows(Exception.class, () -> insertV23Recommendation(schema, applicationId, cycleId));
            insertDecision(schema, applicationId, recommendationId, "APPROVE", 2);
            assertThrows(Exception.class, () -> insertDecision(
                    schema, applicationId, recommendationId, "REJECT", 3));
        } finally {
            dropSchema(schema);
        }
    }

    private UUID insertApplication(String schema, String status) {
        UUID applicationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId, "CUS-V23-" + customerId.toString().substring(0, 12)
        );
        UUID productId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".loan_products WHERE product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".loan_applications "
                        + "(id, customer_id, loan_product_id, application_number, product_code, product_type, status, "
                        + "requested_amount, requested_term_months, submitted_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', ?, ?, 1, ?, ?)",
                applicationId, customerId, productId, "SA-V23-" + UUID.randomUUID(), status,
                new BigDecimal("3000000.00"), time(0), time(4)
        );
        return applicationId;
    }

    private UUID insertRecommendation(String schema, UUID applicationId, String action, int minute) {
        UUID recommendationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".review_recommendations "
                        + "(id, loan_application_id, loan_officer_user_id, recommendation, reason, submitted_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                recommendationId, applicationId, LOAN_OFFICER_ID, action,
                "RECOMMEND_APPROVAL".equals(action) ? null : "Safe historical reason.",
                time(minute), time(minute)
        );
        return recommendationId;
    }

    private UUID insertV23Recommendation(String schema, UUID applicationId, UUID cycleId) {
        UUID recommendationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".review_recommendations "
                        + "(id, loan_application_id, review_cycle_id, loan_officer_user_id, recommendation, "
                        + "submitted_at, created_at) VALUES (?, ?, ?, ?, 'RECOMMEND_APPROVAL', ?, ?)",
                recommendationId, applicationId, cycleId, LOAN_OFFICER_ID, time(1), time(1)
        );
        return recommendationId;
    }

    private void insertDecision(
            String schema, UUID applicationId, UUID recommendationId, String action, int minute
    ) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".approval_decisions "
                        + "(id, loan_application_id, review_recommendation_id, approver_user_id, decision, reason, "
                        + "decided_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), applicationId, recommendationId, APPROVER_ID, action,
                "APPROVE".equals(action) ? null : "Safe historical reason.",
                time(minute), time(minute)
        );
    }

    private void assertCycle(String schema, UUID applicationId, int number, String status) {
        assertEquals(1, count(schema,
                "SELECT count(*) FROM loan_application_review_cycles "
                        + "WHERE loan_application_id = ? AND cycle_number = ? AND status = ?",
                applicationId, number, status));
    }

    private void assertCycleCount(String schema, UUID applicationId, int expected) {
        assertEquals(expected, count(schema,
                "SELECT count(*) FROM loan_application_review_cycles WHERE loan_application_id = ?",
                applicationId));
    }

    private int count(String schema, String query, Object... arguments) {
        String qualifiedQuery = query.replaceFirst("FROM ", "FROM " + schema + ".");
        return jdbcTemplate.queryForObject(qualifiedQuery,
                Integer.class, arguments);
    }

    private void migrateTo(String schema, String target) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private int migrateLatest(String schema) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate().migrationsExecuted;
    }

    private void dropSchema(String schema) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    private LocalDateTime time(int minute) {
        return LocalDateTime.of(2026, 7, 19, 8, minute);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static String schemaName(String suffix) {
        return "meridian_v23_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
