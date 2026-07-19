package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.service.SubmitApprovalDecisionService;
import com.meridian.platform.approval.application.service.SubmitReviewRecommendationService;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "meridian.loan.offer-expiry.enabled=false")
class ApprovalAuditHistoryRollbackIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 11, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubmitApprovalDecisionService submitApprovalDecisionService;

    @Autowired
    private SubmitReviewRecommendationService submitReviewRecommendationService;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvalTransactionRollsBackWhenMandatoryTransitionHistoryPersistenceFails() {
        UUID loanApplicationId = insertApprovalPendingLoanApplication();
        UUID recommendationId = insertReviewRecommendation(loanApplicationId);
        insertTransitionOverflowSeed(loanApplicationId);
        authenticateApprover();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> submitApprovalDecisionService.submitApprovalDecision(
                loanApplicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        ));
        assertTrue(containsMessage(exception, "sequenceNumber exceeds smallint range."));

        assertEquals(0, countRows("approval_decisions", "loan_application_id", loanApplicationId));
        assertEquals("APPROVAL_PENDING", jdbcTemplate.queryForObject(
                "select status from " + table("loan_applications") + " where id = ?",
                String.class,
                loanApplicationId
        ));
        assertEquals(0, countRows("approved_offers", "loan_application_id", loanApplicationId));
        assertEquals(1, countRows("loan_application_status_transitions", "loan_application_id", loanApplicationId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from " + table("audit_events"),
                Integer.class
        ));
        assertEquals(1, countRows("review_recommendations", "id", recommendationId));
    }

    @ParameterizedTest
    @EnumSource(
            value = ReviewRecommendationAction.class,
            names = {"RETURN_TO_CUSTOMER_REVISION", "REQUEST_STAFF_CORRECTION"}
    )
    void revisionRecommendationRollsBackWhenCorrectionContinuationFails(ReviewRecommendationAction action) {
        UUID loanApplicationId = insertLoanApplication("UNDER_REVIEW", "SA-RB-");
        UUID cycleId = insertActiveReviewCycle(loanApplicationId);
        int auditCountBefore = countAllRows("audit_events");
        authenticateLoanOfficer();
        CorrectionResponsibility responsibility =
                action == ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION
                        ? CorrectionResponsibility.CUSTOMER
                        : CorrectionResponsibility.STAFF;

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> submitReviewRecommendationService.submitReviewRecommendation(
                        loanApplicationId,
                        new ReviewRecommendationRequest(
                                action,
                                null,
                                "Restricted rollback note.",
                                cycleId,
                                CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED,
                                new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                                        CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                                        responsibility,
                                        DocumentType.RECENT_PAYSLIP,
                                        true,
                                        null,
                                        null,
                                        responsibility == CorrectionResponsibility.CUSTOMER
                                                ? "Upload a recent payslip." : null,
                                        responsibility == CorrectionResponsibility.STAFF
                                                ? "Upload a recent payslip." : null
                                )))
                        )
                )
        );

        assertEquals("DOCUMENT_CHECKLIST_NOT_FOUND", exception.getErrorCode());
        assertEquals(0, countRows("review_recommendations", "loan_application_id", loanApplicationId));
        assertEquals(0, countRows("loan_correction_requests", "loan_application_id", loanApplicationId));
        assertEquals("UNDER_REVIEW", currentStatus(loanApplicationId));
        assertEquals("ACTIVE", cycleStatus(cycleId));
        assertEquals(0, countRows("loan_application_status_transitions", "loan_application_id", loanApplicationId));
        assertEquals(auditCountBefore, countAllRows("audit_events"));
    }

    @Test
    void mixedCorrectionDecisionRollsBackWhenCorrectionContinuationFails() {
        UUID loanApplicationId = insertApprovalPendingLoanApplication();
        UUID cycleId = activeCycleId(loanApplicationId);
        UUID recommendationId = insertReviewRecommendation(loanApplicationId);
        UUID checklistItemId = UUID.randomUUID();
        UUID baselineVersionId = UUID.randomUUID();
        int auditCountBefore = countAllRows("audit_events");
        authenticateApprover();

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> submitApprovalDecisionService.submitApprovalDecision(
                        loanApplicationId,
                        new ApprovalDecisionRequest(
                                ApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION,
                                null,
                                "Restricted rollback note.",
                                cycleId,
                                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                                new CorrectionPlanRequest(List.of(
                                        new CorrectionTaskRequest(
                                                CorrectionScope.DOCUMENT_REPLACEMENT,
                                                CorrectionResponsibility.CUSTOMER,
                                                DocumentType.RECENT_PAYSLIP,
                                                false,
                                                checklistItemId,
                                                baselineVersionId,
                                                "Upload a clearer payslip.",
                                                null
                                        ),
                                        new CorrectionTaskRequest(
                                                CorrectionScope.DOCUMENT_REVIEW,
                                                CorrectionResponsibility.STAFF,
                                                DocumentType.RECENT_PAYSLIP,
                                                false,
                                                checklistItemId,
                                                baselineVersionId,
                                                null,
                                                "Review the replacement payslip."
                                        )
                                ))
                        )
                )
        );

        assertEquals("DOCUMENT_CHECKLIST_NOT_FOUND", exception.getErrorCode());
        assertEquals(0, countRows("approval_decisions", "loan_application_id", loanApplicationId));
        assertEquals(0, countRows("loan_correction_requests", "loan_application_id", loanApplicationId));
        assertEquals("APPROVAL_PENDING", currentStatus(loanApplicationId));
        assertEquals("ACTIVE", cycleStatus(cycleId));
        assertEquals(0, countRows("loan_application_status_transitions", "loan_application_id", loanApplicationId));
        assertEquals(0, countRows("approved_offers", "loan_application_id", loanApplicationId));
        assertEquals(auditCountBefore, countAllRows("audit_events"));
        assertEquals(1, countRows("review_recommendations", "id", recommendationId));
    }

    private boolean containsMessage(Throwable exception, String expectedMessage) {
        Throwable current = exception;
        while (current != null) {
            if (expectedMessage.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private UUID insertApprovalPendingLoanApplication() {
        UUID id = UUID.randomUUID();
        UUID customerId = insertCustomer();
        jdbcTemplate.update(
                """
                        insert into %s.loan_applications (
                            id,
                            customer_id,
                            loan_product_id,
                            application_number,
                            product_code,
                            product_type,
                            status,
                            requested_amount,
                            requested_term_months,
                            submitted_at
                        ) values (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', 'APPROVAL_PENDING', ?, 1, ?)
                        """.formatted(TEST_SCHEMA),
                id,
                customerId,
                salaryAdvanceProductId(),
                "SA-ROLLBACK-" + id,
                BigDecimal.valueOf(3_000_000).setScale(2),
                NOW.minusDays(1)
        );
        insertActiveReviewCycle(id);
        return id;
    }

    private UUID insertLoanApplication(String status, String applicationNumberPrefix) {
        UUID id = UUID.randomUUID();
        UUID customerId = insertCustomer();
        jdbcTemplate.update(
                """
                        insert into %s.loan_applications (
                            id,
                            customer_id,
                            loan_product_id,
                            application_number,
                            product_code,
                            product_type,
                            status,
                            requested_amount,
                            requested_term_months,
                            submitted_at
                        ) values (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', ?, ?, 1, ?)
                        """.formatted(TEST_SCHEMA),
                id,
                customerId,
                salaryAdvanceProductId(),
                applicationNumberPrefix + id,
                status,
                BigDecimal.valueOf(3_000_000).setScale(2),
                NOW.minusDays(1)
        );
        return id;
    }

    private UUID insertCustomer() {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into %s.customers (
                            id,
                            customer_number,
                            status,
                            verification_status,
                            profile_completion_status
                        ) values (?, ?, 'ACTIVE', 'UNVERIFIED', 'INCOMPLETE')
                        """.formatted(TEST_SCHEMA),
                customerId,
                "CUST-" + customerId
        );
        return customerId;
    }

    private UUID insertReviewRecommendation(UUID loanApplicationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into %s.review_recommendations (
                            id,
                            loan_application_id,
                            review_cycle_id,
                            loan_officer_user_id,
                            recommendation,
                            submitted_at
                        ) values (?, ?, ?, ?, 'RECOMMEND_APPROVAL', ?)
                        """.formatted(TEST_SCHEMA),
                id,
                loanApplicationId,
                activeCycleId(loanApplicationId),
                LOAN_OFFICER_USER_ID,
                NOW.minusHours(1)
        );
        return id;
    }

    private UUID insertActiveReviewCycle(UUID loanApplicationId) {
        UUID cycleId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into %s.loan_application_review_cycles (
                            id, loan_application_id, cycle_number, status, started_at
                        ) values (?, ?, 1, 'ACTIVE', ?)
                        """.formatted(TEST_SCHEMA),
                cycleId,
                loanApplicationId,
                NOW.minusHours(2)
        );
        return cycleId;
    }

    private UUID activeCycleId(UUID loanApplicationId) {
        return jdbcTemplate.queryForObject(
                "select id from " + table("loan_application_review_cycles")
                        + " where loan_application_id = ? and status = 'ACTIVE'",
                UUID.class,
                loanApplicationId
        );
    }

    private String cycleStatus(UUID cycleId) {
        return jdbcTemplate.queryForObject(
                "select status from " + table("loan_application_review_cycles") + " where id = ?",
                String.class,
                cycleId
        );
    }

    private void insertTransitionOverflowSeed(UUID loanApplicationId) {
        jdbcTemplate.update(
                """
                        insert into %s.loan_application_status_transitions (
                            id,
                            loan_application_id,
                            operation_id,
                            sequence_number,
                            from_status,
                            to_status,
                            action,
                            actor_type,
                            actor_user_id,
                            occurred_at
                        ) values (?, ?, ?, 32767, 'SUBMITTED', 'UNDER_REVIEW', 'START_REVIEW', 'USER', ?, ?)
                        """.formatted(TEST_SCHEMA),
                UUID.randomUUID(),
                loanApplicationId,
                UUID.randomUUID(),
                LOAN_OFFICER_USER_ID,
                NOW.minusHours(2)
        );
    }

    private void authenticateLoanOfficer() {
        AuthenticatedUser loanOfficer = new AuthenticatedUser(
                LOAN_OFFICER_USER_ID,
                "loan.officer@meridian.local",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("approval:recommend")
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(loanOfficer, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private void authenticateApprover() {
        AuthenticatedUser approver = new AuthenticatedUser(
                APPROVER_USER_ID,
                "approver@meridian.local",
                "STAFF",
                null,
                Set.of("APPROVER"),
                Set.of("approval:decide")
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(approver, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private int countRows(String tableName, String columnName, UUID value) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table(tableName) + " where " + columnName + " = ?",
                Integer.class,
                value
        );
    }

    private int countAllRows(String tableName) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table(tableName),
                Integer.class
        );
    }

    private String currentStatus(UUID loanApplicationId) {
        return jdbcTemplate.queryForObject(
                "select status from " + table("loan_applications") + " where id = ?",
                String.class,
                loanApplicationId
        );
    }

    private UUID salaryAdvanceProductId() {
        return jdbcTemplate.queryForObject(
                "select id from " + table("loan_products") + " where product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
    }

    private String table(String tableName) {
        return TEST_SCHEMA + "." + tableName;
    }
}
