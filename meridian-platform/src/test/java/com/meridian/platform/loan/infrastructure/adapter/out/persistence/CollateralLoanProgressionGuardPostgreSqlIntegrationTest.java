package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.approval.application.dto.ApprovalDecisionRequest;
import com.meridian.platform.approval.application.port.in.SubmitApprovalDecisionUseCase;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.QueryApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.service.ApplyApprovalDecisionService;
import com.meridian.platform.loan.application.service.ApplyReviewRecommendationService;
import com.meridian.platform.loan.application.service.StartLoanApplicationReviewService;
import com.meridian.platform.loan.domain.model.CollateralType;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                CollateralLoanOriginationPostgreSqlIntegrationTest.CollateralTestConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class CollateralLoanProgressionGuardPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_collateral_guard_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String STORAGE_ROOT = Path.of(
            "target", "collateral-guard-documents-" + UUID.randomUUID()
    ).toAbsolutePath().toString();
    private static final UUID LOAN_OFFICER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID APPROVER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");

    @Autowired private StartCollateralLoanApplicationUseCase origination;
    @Autowired private StartLoanApplicationReviewService startReview;
    @Autowired private ApplyReviewRecommendationService applyRecommendation;
    @Autowired private ApplyApprovalDecisionService applyApproval;
    @Autowired private SubmitApprovalDecisionUseCase submitApprovalDecision;
    @Autowired private QueryApprovedOfferUseCase queryApprovedOffer;
    @Autowired private RespondToApprovedOfferUseCase respondToApprovedOffer;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CollateralLoanOriginationPostgreSqlIntegrationTest.MutableCurrentUserProvider currentUser;

    private UUID customerId;
    private UUID userId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
        registry.add("meridian.document.storage-root", () -> STORAGE_ROOT);
    }

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        userId = UUID.randomUUID();
        createReadyCustomer();
        currentUser.use(userId, customerId);
    }

    @Test
    void pendingVerificationBlocksReviewRecommendationAndApprovalWithoutPartialEffects() {
        CollateralLoanApplicationDto application = origination.startCollateralLoanApplication(request());
        UUID applicationId = application.loanApplicationId();

        jdbcTemplate.update("UPDATE loan_applications SET status = 'SUBMITTED' WHERE id = ?", applicationId);
        assertEquals("PRODUCT_VERIFICATION_PENDING", assertThrows(
                BusinessRuleViolationException.class,
                () -> startReview.startReview(applicationId)
        ).getErrorCode());
        assertStatusAndNoDownstreamEffects(applicationId, "SUBMITTED");

        jdbcTemplate.update("UPDATE loan_applications SET status = 'UNDER_REVIEW' WHERE id = ?", applicationId);
        LocalDateTime recommendationTime = LocalDateTime.parse("2026-08-13T10:00:00");
        assertEquals("PRODUCT_VERIFICATION_PENDING", assertThrows(
                BusinessRuleViolationException.class,
                () -> applyRecommendation.applyReviewRecommendation(new ApplyReviewRecommendationCommand(
                        applicationId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        userId,
                        LoanReviewRecommendationAction.RECOMMEND_APPROVAL,
                        "Synthetic progression guard proof",
                        null,
                        null,
                        recommendationTime,
                        BusinessOperationContext.user(UUID.randomUUID(), userId, recommendationTime)
                ))
        ).getErrorCode());
        assertStatusAndNoDownstreamEffects(applicationId, "UNDER_REVIEW");

        jdbcTemplate.update("UPDATE loan_applications SET status = 'APPROVAL_PENDING' WHERE id = ?", applicationId);
        LocalDateTime decisionTime = LocalDateTime.parse("2026-08-13T10:30:00");
        assertEquals("PRODUCT_VERIFICATION_PENDING", assertThrows(
                BusinessRuleViolationException.class,
                () -> applyApproval.applyApprovalDecision(new ApplyApprovalDecisionCommand(
                        applicationId,
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        userId,
                        LoanApprovalDecisionAction.APPROVE,
                        "Synthetic progression guard proof",
                        null,
                        null,
                        decisionTime,
                        BusinessOperationContext.user(UUID.randomUUID(), userId, decisionTime)
                ))
        ).getErrorCode());
        assertStatusAndNoDownstreamEffects(applicationId, "APPROVAL_PENDING");
    }

    @Test
    void verificationCycleCannotBeDeletedToBypassReviewGate() {
        CollateralLoanApplicationDto application = origination.startCollateralLoanApplication(request());
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        "DELETE FROM collateral_loan_verifications WHERE loan_application_id = ?",
                        application.loanApplicationId()
                )
        );
        assertEquals(1, count(
                "SELECT count(*) FROM collateral_loan_verifications WHERE loan_application_id = ?",
                application.loanApplicationId()
        ));
    }

    @Test
    void verifiedCollateralApprovalCreatesExactOfferAndAcceptanceStopsAtContractPending() {
        UUID applicationId = approvalPendingFixture();
        useApprover();

        submitApprovalDecision.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );

        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", status(applicationId));
        ApprovedOfferDto offer = ownOffer(applicationId);
        assertEquals(new BigDecimal("25000000.00"), offer.approvedPrincipal());
        assertEquals(12, offer.approvedTermMonths());
        assertEquals("FLAT_ORIGINAL_PRINCIPAL", offer.interestCalculationMethod());
        assertEquals(new BigDecimal("0.015000"), offer.flatMonthlyInterestRate());
        assertEquals(new BigDecimal("4500000.00"), offer.totalInterest());
        assertEquals(new BigDecimal("0.00"), offer.feeAmount());
        assertEquals(new BigDecimal("29500000.00"), offer.totalRepaymentAmount());
        assertEquals("MONTHLY_INSTALLMENT", offer.repaymentMethod());
        assertEquals(12, offer.repaymentItems().size());
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", applicationId));

        List<Map<String, Object>> financialSnapshot = offerFinancialSnapshot(applicationId);
        List<Map<String, Object>> itemSnapshot = offerItemSnapshot(applicationId);
        useCustomer();
        respondToApprovedOffer.acceptOffer(applicationId);
        respondToApprovedOffer.acceptOffer(applicationId);

        assertEquals("CONTRACT_PENDING", status(applicationId));
        assertEquals(financialSnapshot, offerFinancialSnapshot(applicationId));
        assertEquals(itemSnapshot, offerItemSnapshot(applicationId));
        assertEquals(0, count("SELECT count(*) FROM loan_contracts WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM loan_accounts WHERE loan_application_id = ?", applicationId));
        assertEquals("OFFER_ACTION_CONFLICT", assertThrows(
                BusinessStateConflictException.class,
                () -> respondToApprovedOffer.declineOffer(applicationId)
        ).getErrorCode());
    }

    @Test
    void collateralDeclineIsIdempotentAndPreservesOfferWithoutSalaryAdvanceEffects() {
        UUID applicationId = approvalPendingFixture();
        useApprover();
        submitApprovalDecision.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );
        List<Map<String, Object>> financialSnapshot = offerFinancialSnapshot(applicationId);
        List<Map<String, Object>> itemSnapshot = offerItemSnapshot(applicationId);

        useCustomer();
        respondToApprovedOffer.declineOffer(applicationId);
        respondToApprovedOffer.declineOffer(applicationId);

        assertEquals("CUSTOMER_DECLINED", status(applicationId));
        assertEquals(financialSnapshot, offerFinancialSnapshot(applicationId));
        assertEquals(itemSnapshot, offerItemSnapshot(applicationId));
        assertEquals(0, count("SELECT count(*) FROM salary_advance_limit_movements "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals("OFFER_ACTION_CONFLICT", assertThrows(
                BusinessStateConflictException.class,
                () -> respondToApprovedOffer.acceptOffer(applicationId)
        ).getErrorCode());
    }

    @Test
    void collateralOfferReadRejectsForeignCustomerOwnership() {
        UUID applicationId = approvalPendingFixture();
        useApprover();
        submitApprovalDecision.submitApprovalDecision(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null)
        );

        currentUser.use(UUID.randomUUID(), UUID.randomUUID());
        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> queryApprovedOffer.getApprovedOffer(applicationId)
        );
        assertEquals("ACCESS_DENIED", exception.getErrorCode());
    }

    @Test
    void concurrentCollateralApprovalsCreateOneDecisionAndOneOffer() throws Exception {
        UUID applicationId = approvalPendingFixture();
        useApprover();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Boolean> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> approveAfter(applicationId, ready, start));
            Future<Boolean> second = executor.submit(() -> approveAfter(applicationId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", status(applicationId));
        assertEquals(1, count("SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?",
                applicationId));
        assertEquals(1, count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?",
                applicationId));
        assertEquals(12, count("SELECT count(*) FROM approved_offer_repayment_items item "
                + "JOIN approved_offers offer ON offer.id = item.approved_offer_id "
                + "WHERE offer.loan_application_id = ?", applicationId));
    }

    @ParameterizedTest
    @EnumSource(
            value = ApprovalDecisionAction.class,
            names = {"REJECT", "RETURN_TO_LOAN_OFFICER_REVIEW"}
    )
    void concurrentApprovalAndCompetingDecisionProduceOneAuthoritativeOutcome(
            ApprovalDecisionAction competingAction
    ) throws Exception {
        UUID applicationId = approvalPendingFixture();
        useApprover();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ApprovalDecisionRequest competingRequest = new ApprovalDecisionRequest(
                competingAction,
                "Competing authorized decision.",
                null
        );

        List<Boolean> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> approval = executor.submit(() -> decisionAfter(
                    applicationId,
                    new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null),
                    ready,
                    start
            ));
            Future<Boolean> competing = executor.submit(() -> decisionAfter(
                    applicationId,
                    competingRequest,
                    ready,
                    start
            ));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            outcomes = List.of(
                    approval.get(20, TimeUnit.SECONDS),
                    competing.get(20, TimeUnit.SECONDS)
            );
        }

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        String competingStatus = competingAction == ApprovalDecisionAction.REJECT
                ? "REJECTED"
                : "RETURNED_TO_REVIEW";
        String finalStatus = status(applicationId);
        assertTrue(Set.of("CUSTOMER_ACCEPTANCE_PENDING", competingStatus).contains(finalStatus));
        assertEquals(1, count("SELECT count(*) FROM approval_decisions WHERE loan_application_id = ?",
                applicationId));
        int expectedOffers = finalStatus.equals("CUSTOMER_ACCEPTANCE_PENDING") ? 1 : 0;
        assertEquals(expectedOffers, count(
                "SELECT count(*) FROM approved_offers WHERE loan_application_id = ?",
                applicationId
        ));
        assertEquals(expectedOffers * 12, count(
                "SELECT count(*) FROM approved_offer_repayment_items item "
                        + "JOIN approved_offers offer ON offer.id = item.approved_offer_id "
                        + "WHERE offer.loan_application_id = ?",
                applicationId
        ));
    }

    private void assertStatusAndNoDownstreamEffects(UUID applicationId, String expectedStatus) {
        assertEquals(expectedStatus, jdbcTemplate.queryForObject(
                "SELECT status FROM loan_applications WHERE id = ?", String.class, applicationId
        ));
        assertEquals(0, count("SELECT count(*) FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ?", applicationId));
    }

    private UUID approvalPendingFixture() {
        CollateralLoanApplicationDto application = origination.startCollateralLoanApplication(request());
        UUID applicationId = application.loanApplicationId();
        LocalDateTime now = jdbcTemplate.queryForObject(
                "SELECT created_at FROM collateral_loan_verifications WHERE loan_application_id = ?",
                LocalDateTime.class,
                applicationId
        );
        jdbcTemplate.update(
                "UPDATE collateral_loan_verifications SET product_verification_result = 'VERIFIED', "
                        + "reviewed_by_user_id = ?, reviewed_at = ?, assessment_note = ? "
                        + "WHERE loan_application_id = ?",
                LOAN_OFFICER_USER_ID,
                now,
                "Verified ownership evidence.",
                applicationId
        );
        jdbcTemplate.update("UPDATE loan_applications SET status = 'APPROVAL_PENDING' WHERE id = ?", applicationId);
        UUID reviewCycleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO loan_application_review_cycles "
                        + "(id, loan_application_id, cycle_number, status, started_at) "
                        + "VALUES (?, ?, 1, 'ACTIVE', ?)",
                reviewCycleId,
                applicationId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO review_recommendations "
                        + "(id, loan_application_id, review_cycle_id, loan_officer_user_id, recommendation, submitted_at) "
                        + "VALUES (?, ?, ?, ?, 'RECOMMEND_APPROVAL', ?)",
                UUID.randomUUID(),
                applicationId,
                reviewCycleId,
                LOAN_OFFICER_USER_ID,
                now
        );
        return applicationId;
    }

    private ApprovedOfferDto ownOffer(UUID applicationId) {
        useCustomer();
        return queryApprovedOffer.getApprovedOffer(applicationId);
    }

    private void useCustomer() {
        currentUser.use(userId, customerId);
    }

    private void useApprover() {
        currentUser.use(APPROVER_USER_ID, null);
    }

    private boolean approveAfter(
            UUID applicationId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return decisionAfter(
                applicationId,
                new ApprovalDecisionRequest(ApprovalDecisionAction.APPROVE, null, null),
                ready,
                start
        );
    }

    private boolean decisionAfter(
            UUID applicationId,
            ApprovalDecisionRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return false;
            }
            submitApprovalDecision.submitApprovalDecision(applicationId, request);
            return true;
        } catch (RuntimeException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<Map<String, Object>> offerFinancialSnapshot(UUID applicationId) {
        return jdbcTemplate.queryForList(
                "SELECT source_loan_product_policy_id, approved_principal, approved_term_months, "
                        + "interest_calculation_method, flat_monthly_interest_rate, total_interest, "
                        + "fee_amount, total_repayment_amount, repayment_method, generated_at, expires_at "
                        + "FROM approved_offers WHERE loan_application_id = ?",
                applicationId
        );
    }

    private List<Map<String, Object>> offerItemSnapshot(UUID applicationId) {
        return jdbcTemplate.queryForList(
                "SELECT item.id, item.installment_number, item.principal_due, item.interest_due, "
                        + "item.fee_due, item.total_due FROM approved_offer_repayment_items item "
                        + "JOIN approved_offers offer ON offer.id = item.approved_offer_id "
                        + "WHERE offer.loan_application_id = ? ORDER BY item.installment_number",
                applicationId
        );
    }

    private String status(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM loan_applications WHERE id = ?",
                String.class,
                applicationId
        );
    }

    private CollateralLoanApplicationRequest request() {
        return new CollateralLoanApplicationRequest(
                new BigDecimal("25000000"),
                12,
                new CollateralDetailsRequest(
                        CollateralType.CAR,
                        "Customer vehicle",
                        new BigDecimal("50000000"),
                        "Customer-submitted ownership statement",
                        "Normal used condition"
                )
        );
    }

    private void createReadyCustomer() {
        String suffix = customerId.toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId, "CL-GUARD-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_profiles "
                        + "(id, customer_id, full_name, identity_reference_ciphertext, "
                        + "identity_reference_fingerprint, identity_reference_last_four, phone_number, "
                        + "residential_address, employment_status, employer_name, "
                        + "terms_consent_accepted, data_processing_consent_accepted) "
                        + "VALUES (?, ?, 'Collateral Guard Customer', 'protected-test-value', ?, '1234', "
                        + "'0900000000', 'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)",
                UUID.randomUUID(), customerId, "identity-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_bank_accounts "
                        + "(id, customer_id, bank_code, bank_name_snapshot, account_holder_name, "
                        + "account_number_ciphertext, account_number_fingerprint, account_number_last_four, "
                        + "status, primary_account) "
                        + "VALUES (?, ?, 'TEST', 'Test Bank', 'Collateral Guard Customer', "
                        + "'protected-test-account', ?, '5678', 'ACTIVE', TRUE)",
                UUID.randomUUID(), customerId, "account-" + suffix);
        jdbcTemplate.update("INSERT INTO users "
                        + "(id, email, normalized_email, password_hash, user_type, status, display_name, customer_id) "
                        + "VALUES (?, ?, ?, 'test-password-hash', 'CUSTOMER', 'ACTIVE', "
                        + "'Collateral Guard Customer', ?)",
                userId, "guard-" + suffix + "@meridian.test", "guard-" + suffix + "@meridian.test", customerId);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }
}
