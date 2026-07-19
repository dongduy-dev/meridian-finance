package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.approval.application.dto.ApprovalDecisionDto;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.dto.ReviewRecommendationRequest;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.application.port.in.SubmitReviewRecommendationUseCase;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.port.in.ExpireApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.ExpiryDiscoveryTrigger;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                SalaryAdvanceWorkflowPostgreSqlIntegrationTest.TestCurrentUserConfiguration.class
        },
        properties = "meridian.loan.offer-expiry.enabled=false"
)
class SalaryAdvanceWorkflowPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_workflow_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID PARTNER_COMPANY_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARTNER_EMPLOYEE_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01");
    private static final UUID IMPORT_BATCH_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID LOAN_OFFICER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final BigDecimal REQUESTED_AMOUNT = money(3_000_000);

    @Autowired
    private StartSalaryAdvanceApplicationUseCase submissionUseCase;

    @Autowired
    private StartLoanApplicationReviewUseCase reviewUseCase;

    @Autowired
    private SubmitReviewRecommendationUseCase recommendationUseCase;

    @Autowired
    private SubmitApprovalDecisionUseCase approvalUseCase;

    @Autowired
    private RespondToApprovedOfferUseCase offerResponseUseCase;

    @Autowired
    private ExpireApprovedOfferUseCase expiryUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ThreadLocalCurrentUserProvider currentUserProvider;

    private Fixture fixture;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @BeforeEach
    void setUp() {
        fixture = createFixture();
        useCustomer();
    }

    @AfterEach
    void clearCurrentUser() {
        currentUserProvider.clear();
    }

    @Test
    void completeSalaryAdvanceHappyPathReachesContractPendingWithRealHistoryAndAudit() {
        UUID loanApplicationId = createCustomerAcceptancePendingApplication();

        useCustomer();
        ApprovedOfferActionResult acceptance = offerResponseUseCase.acceptOffer(loanApplicationId);

        assertEquals(ApprovedOfferActionOutcome.SUCCESS, acceptance.outcome());
        assertEquals("ACCEPTED", acceptance.offer().status());
        assertEquals("CONTRACT_PENDING", applicationStatus(loanApplicationId));
        assertEquals("ACCEPTED", offerStatus(loanApplicationId));
        assertEquals(1, count(
                "SELECT count(*) FROM approved_offer_repayment_items WHERE approved_offer_id = "
                        + "(SELECT id FROM approved_offers WHERE loan_application_id = ?)",
                loanApplicationId
        ));
        assertEquals(REQUESTED_AMOUNT, amount(
                "SELECT reserved_amount FROM salary_advance_limits WHERE customer_id = ? "
                        + "AND customer_partner_employee_link_id = ?",
                fixture.customerId(),
                fixture.linkId()
        ));
        assertEquals(0, releaseMovementCount(loanApplicationId));
        assertEquals(
                List.of(
                        "SUBMIT_APPLICATION",
                        "START_REVIEW",
                        "RECOMMEND_APPROVAL",
                        "APPROVE",
                        "GENERATE_APPROVED_OFFER",
                        "ACCEPT_APPROVED_OFFER"
                ),
                jdbcTemplate.queryForList(
                        "SELECT action FROM loan_application_status_transitions "
                                + "WHERE loan_application_id = ? ORDER BY sequence_number",
                        String.class,
                        loanApplicationId
                )
        );

        List<String> auditActions = auditActions(loanApplicationId);
        assertEquals(9, auditActions.size());
        assertEquals(
                Set.of(
                        "SALARY_ADVANCE_APPLICATION_SUBMITTED",
                        "DOCUMENT_CHECKLIST_CREATED",
                        "SALARY_ADVANCE_LIMIT_INITIALIZED",
                        "SALARY_ADVANCE_LIMIT_RESERVED",
                        "LOAN_REVIEW_STARTED",
                        "REVIEW_RECOMMENDATION_RECORDED",
                        "APPROVAL_DECISION_RECORDED",
                        "APPROVED_OFFER_GENERATED",
                        "APPROVED_OFFER_ACCEPTED"
                ),
                new HashSet<>(auditActions)
        );
    }

    @Test
    void persistedExpiryAcceptAndDeclineAddNoTerminalEffects() {
        UUID loanApplicationId = createCustomerAcceptancePendingApplication();
        LocalDateTime expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM approved_offers WHERE loan_application_id = ?",
                LocalDateTime.class,
                loanApplicationId
        );
        expiryUseCase.expireDueOffer(
                loanApplicationId,
                BusinessOperationContext.system(UUID.randomUUID(), expiresAt),
                ExpiryDiscoveryTrigger.SCHEDULED_SCAN
        );

        int transitionCount = transitionCount(loanApplicationId);
        int releaseCount = releaseMovementCount(loanApplicationId);
        int auditCount = count("SELECT count(*) FROM audit_events");
        LocalDateTime persistedExpiredAt = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM approved_offers WHERE loan_application_id = ?",
                LocalDateTime.class,
                loanApplicationId
        );
        BigDecimal reservedAmount = reservedAmount();

        useCustomer();
        ApprovedOfferActionResult acceptResult = offerResponseUseCase.acceptOffer(loanApplicationId);
        ApprovedOfferActionResult declineResult = offerResponseUseCase.declineOffer(loanApplicationId);

        assertEquals(ApprovedOfferActionOutcome.EXPIRED, acceptResult.outcome());
        assertEquals(ApprovedOfferActionOutcome.EXPIRED, declineResult.outcome());
        assertEquals(persistedExpiredAt, acceptResult.offer().expiredAt());
        assertEquals(persistedExpiredAt, declineResult.offer().expiredAt());
        assertEquals("EXPIRED", applicationStatus(loanApplicationId));
        assertEquals("EXPIRED", offerStatus(loanApplicationId));
        assertEquals(transitionCount, transitionCount(loanApplicationId));
        assertEquals(releaseCount, releaseMovementCount(loanApplicationId));
        assertEquals(1, releaseCount);
        assertEquals(auditCount, count("SELECT count(*) FROM audit_events"));
        assertEquals(reservedAmount, reservedAmount());
        assertEquals(money(0), reservedAmount);
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT accepted_at IS NULL AND declined_at IS NULL "
                        + "FROM approved_offers WHERE loan_application_id = ?",
                Boolean.class,
                loanApplicationId
        )));
    }

    @Test
    void concurrentApproveAndRejectLeaveOneWinningDecisionAndNoLoserResidue() throws Exception {
        UUID loanApplicationId = createApprovalPendingApplication();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<DecisionAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<DecisionAttempt> approve = executor.submit(
                    () -> decide(loanApplicationId, ApprovalDecisionAction.APPROVE, ready, start)
            );
            Future<DecisionAttempt> reject = executor.submit(
                    () -> decide(loanApplicationId, ApprovalDecisionAction.REJECT, ready, start)
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Approval workers did not reach the start barrier.");
            start.countDown();
            attempts = List.of(
                    approve.get(20, TimeUnit.SECONDS),
                    reject.get(20, TimeUnit.SECONDS)
            );
        }

        List<DecisionAttempt> successes = attempts.stream().filter(DecisionAttempt::successful).toList();
        List<DecisionAttempt> failures = attempts.stream().filter(attempt -> !attempt.successful()).toList();
        assertEquals(1, successes.size());
        assertEquals(1, failures.size());
        BusinessStateConflictException conflict = assertInstanceOf(
                BusinessStateConflictException.class,
                failures.getFirst().failure()
        );
        assertEquals("APPROVAL_DECISION_NOT_ALLOWED", conflict.getErrorCode());
        assertEquals(1, count(
                "SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?",
                loanApplicationId
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM audit_events WHERE action = 'APPROVAL_DECISION_RECORDED' "
                        + "AND payload ->> 'loanApplicationId' = ?",
                loanApplicationId.toString()
        ));

        String winningDecision = jdbcTemplate.queryForObject(
                "SELECT decision FROM approval_decisions WHERE loan_application_id = ?",
                String.class,
                loanApplicationId
        );
        assertEquals(successes.getFirst().action().name(), winningDecision);
        if ("APPROVE".equals(winningDecision)) {
            assertApprovedRaceWinner(loanApplicationId);
        } else {
            assertEquals("REJECT", winningDecision);
            assertRejectedRaceWinner(loanApplicationId);
        }
    }

    @Test
    void concurrentAcceptAndScheduledExpiryLeaveOneCoherentTerminalEffect() throws Exception {
        UUID loanApplicationId = createCustomerAcceptancePendingApplication();
        LocalDateTime expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM approved_offers WHERE loan_application_id = ?",
                LocalDateTime.class,
                loanApplicationId
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        OfferRaceAttempt customerAttempt;
        Throwable expiryFailure;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<OfferRaceAttempt> customer = executor.submit(
                    () -> accept(loanApplicationId, ready, start)
            );
            Future<Throwable> expiry = executor.submit(
                    () -> expire(loanApplicationId, expiresAt, ready, start)
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Offer workers did not reach the start barrier.");
            start.countDown();
            customerAttempt = customer.get(20, TimeUnit.SECONDS);
            expiryFailure = expiry.get(20, TimeUnit.SECONDS);
        }

        assertNull(customerAttempt.failure());
        assertNull(expiryFailure);
        String finalOfferStatus = offerStatus(loanApplicationId);
        assertTrue(Set.of("ACCEPTED", "EXPIRED").contains(finalOfferStatus));
        assertEquals(1, count(
                "SELECT count(*) FROM loan_application_status_transitions "
                        + "WHERE loan_application_id = ? "
                        + "AND action IN ('ACCEPT_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER')",
                loanApplicationId
        ));
        assertEquals(1, count(
                "SELECT count(*) FROM audit_events "
                        + "WHERE action IN ('APPROVED_OFFER_ACCEPTED', 'OFFER_EXPIRED') "
                        + "AND payload ->> 'loanApplicationId' = ?",
                loanApplicationId.toString()
        ));

        if ("ACCEPTED".equals(finalOfferStatus)) {
            assertEquals(ApprovedOfferActionOutcome.SUCCESS, customerAttempt.result().outcome());
            assertEquals("CONTRACT_PENDING", applicationStatus(loanApplicationId));
            assertEquals(0, releaseMovementCount(loanApplicationId));
            assertEquals(REQUESTED_AMOUNT, reservedAmount());
            assertEquals(1, auditActionCount(loanApplicationId, "APPROVED_OFFER_ACCEPTED"));
            assertEquals(0, auditActionCount(loanApplicationId, "OFFER_EXPIRED"));
            assertEquals(0, auditActionCount(loanApplicationId, "RESERVATION_RELEASED"));
        } else {
            assertEquals(ApprovedOfferActionOutcome.EXPIRED, customerAttempt.result().outcome());
            assertEquals("EXPIRED", applicationStatus(loanApplicationId));
            assertEquals(1, releaseMovementCount(loanApplicationId));
            assertEquals(money(0), reservedAmount());
            assertEquals(0, auditActionCount(loanApplicationId, "APPROVED_OFFER_ACCEPTED"));
            assertEquals(1, auditActionCount(loanApplicationId, "OFFER_EXPIRED"));
            assertEquals(1, auditActionCount(loanApplicationId, "RESERVATION_RELEASED"));
        }
    }

    private UUID createApprovalPendingApplication() {
        useCustomer();
        SalaryAdvanceApplicationDto application = submissionUseCase.startSalaryAdvanceApplication(
                new SalaryAdvanceApplicationRequest(fixture.linkId(), REQUESTED_AMOUNT, 1)
        );
        useLoanOfficer();
        reviewUseCase.startReview(application.loanApplicationId());
        recommendationUseCase.submitReviewRecommendation(
                application.loanApplicationId(),
                new ReviewRecommendationRequest(
                        ReviewRecommendationAction.RECOMMEND_APPROVAL,
                        null,
                        "Workflow integration test."
                )
        );
        assertEquals("APPROVAL_PENDING", applicationStatus(application.loanApplicationId()));
        return application.loanApplicationId();
    }

    private UUID createCustomerAcceptancePendingApplication() {
        UUID loanApplicationId = createApprovalPendingApplication();
        useApprover();
        ApprovalDecisionDto decision = approvalUseCase.submitApprovalDecision(
                loanApplicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );
        assertEquals("APPROVE", decision.action());
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", applicationStatus(loanApplicationId));
        assertEquals("PENDING", offerStatus(loanApplicationId));
        return loanApplicationId;
    }

    private DecisionAttempt decide(
            UUID loanApplicationId,
            ApprovalDecisionAction action,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            await(start);
            useApprover();
            approvalUseCase.submitApprovalDecision(
                    loanApplicationId,
                    new ApprovalDecisionRequest(
                            action,
                            action == ApprovalDecisionAction.REJECT ? "Policy rejection." : null,
                            null
                    )
            );
            return DecisionAttempt.success(action);
        } catch (Throwable throwable) {
            return DecisionAttempt.failure(action, throwable);
        } finally {
            currentUserProvider.clear();
        }
    }

    private OfferRaceAttempt accept(
            UUID loanApplicationId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            await(start);
            useCustomer();
            return OfferRaceAttempt.success(offerResponseUseCase.acceptOffer(loanApplicationId));
        } catch (Throwable throwable) {
            return OfferRaceAttempt.failure(throwable);
        } finally {
            currentUserProvider.clear();
        }
    }

    private Throwable expire(
            UUID loanApplicationId,
            LocalDateTime expiresAt,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            await(start);
            expiryUseCase.expireDueOffer(
                    loanApplicationId,
                    BusinessOperationContext.system(UUID.randomUUID(), expiresAt),
                    ExpiryDiscoveryTrigger.SCHEDULED_SCAN
            );
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void assertApprovedRaceWinner(UUID loanApplicationId) {
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", applicationStatus(loanApplicationId));
        assertEquals(1, count(
                "SELECT count(*) FROM approved_offers WHERE loan_application_id = ?",
                loanApplicationId
        ));
        assertEquals(0, releaseMovementCount(loanApplicationId));
        assertEquals(REQUESTED_AMOUNT, reservedAmount());
        assertEquals(1, transitionActionCount(loanApplicationId, "APPROVE"));
        assertEquals(1, transitionActionCount(loanApplicationId, "GENERATE_APPROVED_OFFER"));
        assertEquals(0, transitionActionCount(loanApplicationId, "REJECT"));
        assertEquals(1, auditActionCount(loanApplicationId, "APPROVED_OFFER_GENERATED"));
        assertEquals(0, auditActionCount(loanApplicationId, "RESERVATION_RELEASED"));
    }

    private void assertRejectedRaceWinner(UUID loanApplicationId) {
        assertEquals("REJECTED", applicationStatus(loanApplicationId));
        assertEquals(0, count(
                "SELECT count(*) FROM approved_offers WHERE loan_application_id = ?",
                loanApplicationId
        ));
        assertEquals(1, releaseMovementCount(loanApplicationId));
        assertEquals(money(0), reservedAmount());
        assertEquals(0, transitionActionCount(loanApplicationId, "APPROVE"));
        assertEquals(0, transitionActionCount(loanApplicationId, "GENERATE_APPROVED_OFFER"));
        assertEquals(1, transitionActionCount(loanApplicationId, "REJECT"));
        assertEquals(0, auditActionCount(loanApplicationId, "APPROVED_OFFER_GENERATED"));
        assertEquals(1, auditActionCount(loanApplicationId, "RESERVATION_RELEASED"));
    }

    private Fixture createFixture() {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        String unique = customerId.toString().replace("-", "");

        jdbcTemplate.update(
                "INSERT INTO customers (id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId,
                "CUS-W-" + unique.substring(0, 12)
        );
        jdbcTemplate.update(
                """
                        INSERT INTO customer_profiles (
                            id, customer_id, full_name, identity_reference_ciphertext,
                            identity_reference_fingerprint, identity_reference_last_four,
                            phone_number, residential_address, employment_status, employer_name,
                            terms_consent_accepted, data_processing_consent_accepted
                        ) VALUES (?, ?, 'Workflow Test Customer', ?, ?, '1234', '0900000000',
                                  'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)
                        """,
                UUID.randomUUID(),
                customerId,
                "cipher-" + unique,
                "fingerprint-" + unique
        );
        jdbcTemplate.update(
                """
                        INSERT INTO customer_bank_accounts (
                            id, customer_id, bank_code, bank_name_snapshot, account_holder_name,
                            account_number_ciphertext, account_number_fingerprint, account_number_last_four,
                            status, primary_account
                        ) VALUES (?, ?, 'TEST', 'Test Bank', 'Workflow Test Customer', ?, ?, '5678',
                                  'ACTIVE', TRUE)
                        """,
                UUID.randomUUID(),
                customerId,
                "bank-cipher-" + unique,
                "bank-fingerprint-" + unique
        );
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id, email, normalized_email, password_hash, user_type, status, display_name, customer_id
                        ) VALUES (?, ?, ?, 'not-used', 'CUSTOMER', 'ACTIVE', 'Workflow Test Customer', ?)
                        """,
                customerUserId,
                "workflow-" + unique + "@meridian.test",
                "workflow-" + unique + "@meridian.test",
                customerId
        );
        jdbcTemplate.update(
                """
                        INSERT INTO customer_partner_employee_links (
                            id, customer_id, partner_company_id, partner_employee_id, source_import_batch_id,
                            verification_outcome, link_status, verified_identity_ref, verified_employee_code,
                            last_verified_at, last_refreshed_at
                        ) VALUES (?, ?, ?, ?, ?, 'MATCHED_ACTIVE', 'VERIFIED', ?, 'MER-EMP-001',
                                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                linkId,
                customerId,
                PARTNER_COMPANY_ID,
                PARTNER_EMPLOYEE_ID,
                IMPORT_BATCH_ID,
                "test-identity-" + linkId
        );
        return new Fixture(customerId, customerUserId, linkId);
    }

    private List<String> auditActions(UUID loanApplicationId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT action
                        FROM audit_events
                        WHERE operation_id IN (
                            SELECT operation_id
                            FROM audit_events
                            WHERE entity_id = ?
                               OR payload ->> 'loanApplicationId' = ?
                        )
                        ORDER BY occurred_at, sequence_number
                        """,
                String.class,
                loanApplicationId,
                loanApplicationId.toString()
        );
    }

    private int transitionCount(UUID loanApplicationId) {
        return count(
                "SELECT count(*) FROM loan_application_status_transitions WHERE loan_application_id = ?",
                loanApplicationId
        );
    }

    private int transitionActionCount(UUID loanApplicationId, String action) {
        return count(
                "SELECT count(*) FROM loan_application_status_transitions "
                        + "WHERE loan_application_id = ? AND action = ?",
                loanApplicationId,
                action
        );
    }

    private int auditActionCount(UUID loanApplicationId, String action) {
        return count(
                "SELECT count(*) FROM audit_events WHERE action = ? "
                        + "AND (entity_id = ? OR payload ->> 'loanApplicationId' = ?)",
                action,
                loanApplicationId,
                loanApplicationId.toString()
        );
    }

    private int releaseMovementCount(UUID loanApplicationId) {
        return count(
                "SELECT count(*) FROM salary_advance_limit_movements "
                        + "WHERE loan_application_id = ? AND movement_type = 'RESERVATION_RELEASED'",
                loanApplicationId
        );
    }

    private BigDecimal reservedAmount() {
        return amount(
                "SELECT reserved_amount FROM salary_advance_limits "
                        + "WHERE customer_id = ? AND customer_partner_employee_link_id = ?",
                fixture.customerId(),
                fixture.linkId()
        );
    }

    private String applicationStatus(UUID loanApplicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM loan_applications WHERE id = ?",
                String.class,
                loanApplicationId
        );
    }

    private String offerStatus(UUID loanApplicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM approved_offers WHERE loan_application_id = ?",
                String.class,
                loanApplicationId
        );
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private BigDecimal amount(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, arguments);
    }

    private void useCustomer() {
        currentUserProvider.use(new AuthenticatedUser(
                fixture.customerUserId(),
                "workflow-customer@meridian.test",
                "CUSTOMER",
                fixture.customerId(),
                Set.of("CUSTOMER"),
                Set.of("loan:submit", "loan:read:own", "loan:offer:respond:own")
        ));
    }

    private void useLoanOfficer() {
        currentUserProvider.use(new AuthenticatedUser(
                LOAN_OFFICER_USER_ID,
                "loan.officer@meridian.local",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review", "approval:recommend")
        ));
    }

    private void useApprover() {
        currentUserProvider.use(new AuthenticatedUser(
                APPROVER_USER_ID,
                "approver@meridian.local",
                "STAFF",
                null,
                Set.of("APPROVER"),
                Set.of("loan:read", "approval:decide")
        ));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent start barrier timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting start barrier.", exception);
        }
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private record Fixture(UUID customerId, UUID customerUserId, UUID linkId) {
    }

    private record DecisionAttempt(
            ApprovalDecisionAction action,
            Throwable failure
    ) {

        private static DecisionAttempt success(ApprovalDecisionAction action) {
            return new DecisionAttempt(action, null);
        }

        private static DecisionAttempt failure(ApprovalDecisionAction action, Throwable failure) {
            return new DecisionAttempt(action, failure);
        }

        private boolean successful() {
            return failure == null;
        }
    }

    private record OfferRaceAttempt(ApprovedOfferActionResult result, Throwable failure) {

        private static OfferRaceAttempt success(ApprovedOfferActionResult result) {
            return new OfferRaceAttempt(result, null);
        }

        private static OfferRaceAttempt failure(Throwable failure) {
            return new OfferRaceAttempt(null, failure);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCurrentUserConfiguration {

        @Bean
        @Primary
        ThreadLocalCurrentUserProvider threadLocalCurrentUserProvider() {
            return new ThreadLocalCurrentUserProvider();
        }
    }

    static class ThreadLocalCurrentUserProvider implements CurrentUserProvider {

        private final ThreadLocal<AuthenticatedUser> currentUser = new ThreadLocal<>();

        void use(AuthenticatedUser authenticatedUser) {
            currentUser.set(authenticatedUser);
        }

        void clear() {
            currentUser.remove();
        }

        @Override
        public AuthenticatedUser currentUser() {
            AuthenticatedUser authenticatedUser = currentUser.get();
            if (authenticatedUser == null) {
                throw new IllegalStateException("Test current user was not configured.");
            }
            return authenticatedUser;
        }
    }
}
