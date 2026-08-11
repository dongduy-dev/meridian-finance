package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceOfferPolicyRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanOfferPolicy;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyApprovalDecisionServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DECISION_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID APPROVER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LINK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LIMIT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID POLICY_ID = UUID.fromString("12121212-1212-1212-1212-121212121212");
    private static final UUID UCL_POLICY_ID = UUID.fromString("13131313-1313-1313-1313-131313131313");
    private static final LocalDateTime DECIDED_AT = LocalDateTime.of(2026, 7, 6, 9, 30);

    private FakeLoanApplicationRepository loanApplicationRepository;
    private LoanReviewCycleRepository reviewCycleRepository;
    private FakeApprovedOfferRepository approvedOfferRepository;
    private FakeSalaryAdvanceOfferPolicyRepository offerPolicyRepository;
    private FakeUnsecuredConsumerLoanOfferPolicyRepository unsecuredConsumerLoanOfferPolicyRepository;
    private FakeSalaryAdvanceVerificationRepository verificationRepository;
    private FakeSalaryAdvanceLimitRepository limitRepository;
    private FakeSalaryAdvanceLimitMovementRepository movementRepository;
    private FakeLoanApplicationStatusTransitionRepository transitionRepository;
    private FakeBusinessAuditPublisher auditPublisher;
    private ApplyApprovalDecisionService service;

    @BeforeEach
    void setUp() {
        loanApplicationRepository = new FakeLoanApplicationRepository();
        reviewCycleRepository = org.mockito.Mockito.mock(LoanReviewCycleRepository.class);
        org.mockito.Mockito.when(reviewCycleRepository.findActiveByLoanApplicationIdForUpdate(LOAN_APPLICATION_ID))
                .thenReturn(Optional.of(LoanApplicationReviewCycle.active(
                        UUID.fromString("abababab-abab-abab-abab-abababababab"),
                        LOAN_APPLICATION_ID, 1, DECIDED_AT.minusHours(1))));
        org.mockito.Mockito.when(reviewCycleRepository.nextCycleNumber(LOAN_APPLICATION_ID)).thenReturn(2);
        org.mockito.Mockito.when(reviewCycleRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        approvedOfferRepository = new FakeApprovedOfferRepository();
        offerPolicyRepository = new FakeSalaryAdvanceOfferPolicyRepository();
        unsecuredConsumerLoanOfferPolicyRepository = new FakeUnsecuredConsumerLoanOfferPolicyRepository();
        verificationRepository = new FakeSalaryAdvanceVerificationRepository();
        limitRepository = new FakeSalaryAdvanceLimitRepository();
        movementRepository = new FakeSalaryAdvanceLimitMovementRepository();
        transitionRepository = new FakeLoanApplicationStatusTransitionRepository();
        auditPublisher = new FakeBusinessAuditPublisher();
        SalaryAdvanceReservationReleaseService releaseService = new SalaryAdvanceReservationReleaseService(
                verificationRepository,
                limitRepository,
                movementRepository,
                auditPublisher
        );
        service = new ApplyApprovalDecisionService(
                loanApplicationRepository,
                reviewCycleRepository,
                approvedOfferRepository,
                offerPolicyRepository,
                unsecuredConsumerLoanOfferPolicyRepository,
                releaseService,
                new LoanApplicationStatusTransitionRecorder(transitionRepository),
                auditPublisher
        );
    }

    @Test
    void approvalGeneratesSalaryAdvanceOfferAndMovesToCustomerAcceptancePending() {
        LoanApplicationReviewDto result = service.applyApprovalDecision(command(LoanApprovalDecisionAction.APPROVE));

        assertEquals(LOAN_APPLICATION_ID, result.loanApplicationId());
        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", result.status());
        assertEquals(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                loanApplicationRepository.savedApplication.status());
        assertNotNull(approvedOfferRepository.savedOffer);
        assertEquals(LOAN_APPLICATION_ID, approvedOfferRepository.savedOffer.loanApplicationId());
        assertEquals(POLICY_ID, approvedOfferRepository.savedOffer.sourceLoanProductPolicyId());
        assertEquals(limit(3_000_000), approvedOfferRepository.savedOffer.financialTerms().approvedPrincipal());
        assertEquals(1, approvedOfferRepository.savedOffer.financialTerms().approvedTermMonths());
        assertEquals(limit(36_000), approvedOfferRepository.savedOffer.financialTerms().totalInterest());
        assertEquals(limit(3_036_000), approvedOfferRepository.savedOffer.financialTerms().totalRepaymentAmount());
        assertEquals(DECIDED_AT, approvedOfferRepository.savedOffer.generatedAt());
        assertEquals(DECIDED_AT.plusDays(7), approvedOfferRepository.savedOffer.expiresAt());
        assertEquals(1, approvedOfferRepository.savedOffer.repaymentItems().size());
        assertTrue(movementRepository.savedMovements.isEmpty());
        assertEquals(2, transitionRepository.savedTransitions.size());
        assertEquals(LoanApplicationStatus.APPROVED, transitionRepository.savedTransitions.get(0).toStatus());
        assertEquals(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING, transitionRepository.savedTransitions.get(1).toStatus());
        assertEquals(BusinessAuditAction.APPROVED_OFFER_GENERATED, auditPublisher.lastEvent().entries().getFirst().action());
    }

    @Test
    void approvalRollsBackWhenSalaryAdvancePolicyIsMissing() {
        offerPolicyRepository.policy = Optional.empty();

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyApprovalDecision(command(LoanApprovalDecisionAction.APPROVE))
        );

        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
        assertNull(approvedOfferRepository.savedOffer);
        assertNull(loanApplicationRepository.savedApplication);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void rejectionReleasesSalaryAdvanceReservationExactlyOnce() {
        LoanApplicationReviewDto result = service.applyApprovalDecision(command(LoanApprovalDecisionAction.REJECT));

        assertEquals("REJECTED", result.status());
        assertEquals(LoanApplicationStatus.REJECTED, loanApplicationRepository.savedApplication.status());
        assertEquals(limit(0), limitRepository.savedLimit.reservedAmount());
        assertEquals(limit(6_000_000), limitRepository.savedLimit.availableAmount());
        assertEquals(1, movementRepository.savedMovements.size());
        assertEquals(SalaryAdvanceLimitMovementType.RESERVATION_RELEASED,
                movementRepository.savedMovements.get(0).movementType());
        assertEquals(LOAN_APPLICATION_ID, movementRepository.savedMovements.get(0).loanApplicationId());
        assertEquals(limit(3_000_000), movementRepository.savedMovements.get(0).amount());
        assertEquals(1, transitionRepository.savedTransitions.size());
        assertEquals(LoanApplicationStatus.REJECTED, transitionRepository.savedTransitions.getFirst().toStatus());
        assertEquals(BusinessAuditAction.RESERVATION_RELEASED, auditPublisher.lastEvent().entries().getFirst().action());
    }

    @Test
    void rejectionSkipsReleaseWhenReleaseMovementAlreadyExists() {
        movementRepository.releaseMovementExists = true;

        LoanApplicationReviewDto result = service.applyApprovalDecision(command(LoanApprovalDecisionAction.REJECT));

        assertEquals("REJECTED", result.status());
        assertNull(limitRepository.savedLimit);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void appliesReturnToReviewDecision() {
        LoanApplicationReviewDto result = service.applyApprovalDecision(
                command(LoanApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW)
        );

        assertEquals("RETURNED_TO_REVIEW", result.status());
        assertEquals(LoanApplicationStatus.RETURNED_TO_REVIEW, loanApplicationRepository.savedApplication.status());
        assertNull(approvedOfferRepository.savedOffer);
    }

    @Test
    void uclApprovalGeneratesExactMonthlyInstallmentOffer() {
        loanApplicationRepository.application = uclApplication(LoanApplicationStatus.APPROVAL_PENDING);

        LoanApplicationReviewDto result = service.applyApprovalDecision(
                command(LoanApprovalDecisionAction.APPROVE)
        );

        assertEquals("CUSTOMER_ACCEPTANCE_PENDING", result.status());
        assertEquals(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                loanApplicationRepository.savedApplication.status());
        assertNotNull(approvedOfferRepository.savedOffer);
        assertEquals(UCL_POLICY_ID, approvedOfferRepository.savedOffer.sourceLoanProductPolicyId());
        assertEquals(limit(5_000_000), approvedOfferRepository.savedOffer.financialTerms().approvedPrincipal());
        assertEquals(6, approvedOfferRepository.savedOffer.financialTerms().approvedTermMonths());
        assertEquals(InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                approvedOfferRepository.savedOffer.financialTerms().interestCalculationMethod());
        assertEquals(new BigDecimal("0.018000"),
                approvedOfferRepository.savedOffer.financialTerms().flatMonthlyInterestRate());
        assertEquals(limit(540_000), approvedOfferRepository.savedOffer.financialTerms().totalInterest());
        assertEquals(limit(0), approvedOfferRepository.savedOffer.financialTerms().feeAmount());
        assertEquals(limit(5_540_000), approvedOfferRepository.savedOffer.financialTerms().totalRepaymentAmount());
        assertEquals(RepaymentMethod.MONTHLY_INSTALLMENT,
                approvedOfferRepository.savedOffer.financialTerms().repaymentMethod());
        assertEquals(6, approvedOfferRepository.savedOffer.repaymentItems().size());
        assertEquals(DECIDED_AT.plusDays(7), approvedOfferRepository.savedOffer.expiresAt());
        assertEquals(2, transitionRepository.savedTransitions.size());
        assertTrue(movementRepository.savedMovements.isEmpty());
        assertNull(limitRepository.savedLimit);
        assertEquals(BusinessAuditAction.APPROVED_OFFER_GENERATED,
                auditPublisher.lastEvent().entries().getFirst().action());
    }

    @Test
    void uclApprovalFailsWithoutPersistingWhenPolicyIsMissing() {
        loanApplicationRepository.application = uclApplication(LoanApplicationStatus.APPROVAL_PENDING);
        unsecuredConsumerLoanOfferPolicyRepository.policy = Optional.empty();

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyApprovalDecision(command(LoanApprovalDecisionAction.APPROVE))
        );

        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
        assertNull(approvedOfferRepository.savedOffer);
        assertTrue(transitionRepository.savedTransitions.isEmpty());
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void uclCorrectionDecisionFailsClosed() {
        loanApplicationRepository.application = uclApplication(LoanApplicationStatus.APPROVAL_PENDING);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.applyApprovalDecision(
                        command(LoanApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION)
                )
        );

        assertEquals("UCL_CORRECTION_NOT_READY", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
        assertNull(approvedOfferRepository.savedOffer);
        assertTrue(transitionRepository.savedTransitions.isEmpty());
    }

    @Test
    void uclRejectionUsesGenericDecisionWithoutSalaryAdvanceRelease() {
        loanApplicationRepository.application = uclApplication(LoanApplicationStatus.APPROVAL_PENDING);

        LoanApplicationReviewDto result = service.applyApprovalDecision(command(LoanApprovalDecisionAction.REJECT));

        assertEquals("REJECTED", result.status());
        assertEquals(LoanApplicationStatus.REJECTED, loanApplicationRepository.savedApplication.status());
        assertNull(approvedOfferRepository.savedOffer);
        assertTrue(movementRepository.savedMovements.isEmpty());
        assertNull(limitRepository.savedLimit);
    }

    @Test
    void uclReturnToReviewUsesGenericDecision() {
        loanApplicationRepository.application = uclApplication(LoanApplicationStatus.APPROVAL_PENDING);

        LoanApplicationReviewDto result = service.applyApprovalDecision(
                command(LoanApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW)
        );

        assertEquals("RETURNED_TO_REVIEW", result.status());
        assertNull(approvedOfferRepository.savedOffer);
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void rejectsDecisionWhenLoanApplicationIsNotApprovalPending() {
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.UNDER_REVIEW);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.applyApprovalDecision(command(LoanApprovalDecisionAction.APPROVE))
        );

        assertEquals("APPROVAL_DECISION_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(movementRepository.savedMovements.isEmpty());
        assertNull(approvedOfferRepository.savedOffer);
    }

    @Test
    void rejectsSystemActorApprovalOperationContext() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyApprovalDecision(command(
                        LoanApprovalDecisionAction.APPROVE,
                        BusinessOperationContext.system(UUID.fromString("abababab-abab-abab-abab-abababababab"), DECIDED_AT)
                ))
        );

        assertEquals("INVALID_OPERATION_CONTEXT", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
        assertNull(approvedOfferRepository.savedOffer);
    }

    @Test
    void rejectsMismatchedApproverOperationContext() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyApprovalDecision(command(
                        LoanApprovalDecisionAction.APPROVE,
                        BusinessOperationContext.user(
                                UUID.fromString("abababab-abab-abab-abab-abababababab"),
                                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                                DECIDED_AT
                        )
                ))
        );

        assertEquals("INVALID_OPERATION_CONTEXT", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
        assertNull(approvedOfferRepository.savedOffer);
    }

    @Test
    void rejectsMismatchedDecisionTimestampOperationContext() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyApprovalDecision(command(
                        LoanApprovalDecisionAction.APPROVE,
                        BusinessOperationContext.user(
                                UUID.fromString("abababab-abab-abab-abab-abababababab"),
                                APPROVER_USER_ID,
                                DECIDED_AT.plusSeconds(1)
                        )
                ))
        );

        assertEquals("INVALID_OPERATION_CONTEXT", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
        assertNull(approvedOfferRepository.savedOffer);
    }
    private ApplyApprovalDecisionCommand command(LoanApprovalDecisionAction action) {
        return command(
                action,
                BusinessOperationContext.user(
                        UUID.fromString("abababab-abab-abab-abab-abababababab"),
                        APPROVER_USER_ID,
                        DECIDED_AT
                )
        );
    }

    private ApplyApprovalDecisionCommand command(
            LoanApprovalDecisionAction action,
            BusinessOperationContext operationContext
    ) {
        return new ApplyApprovalDecisionCommand(
                LOAN_APPLICATION_ID,
                DECISION_ID,
                RECOMMENDATION_ID,
                APPROVER_USER_ID,
                action,
                action == LoanApprovalDecisionAction.REJECT ? "Business-facing reason" : null,
                DECIDED_AT,
                operationContext
        );
    }

    private LoanApplication loanApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                CUSTOMER_ID,
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "SA-20260630-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                limit(3_000_000),
                1,
                LocalDateTime.now()
        );
    }

    private LoanApplication uclApplication(LoanApplicationStatus status) {
        LoanApplication salaryAdvance = loanApplication(status);
        return new LoanApplication(
                salaryAdvance.id(),
                salaryAdvance.customerId(),
                salaryAdvance.loanProductId(),
                "UCL-20260630-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                limit(5_000_000),
                6,
                salaryAdvance.submittedAt()
        );
    }

    private BigDecimal limit(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private class FakeLoanApplicationRepository implements LoanApplicationRepository {
        @Override
        public void acquireWorkflowLock(UUID loanApplicationId) {
        }


        @Override
        public void acquireCustomerProductLock(UUID customerId, ProductCode productCode) {
        }

        private LoanApplication application = loanApplication(LoanApplicationStatus.APPROVAL_PENDING);
        private LoanApplication savedApplication;

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplication = loanApplication;
            application = loanApplication;
            return loanApplication;
        }

        @Override
        public Optional<LoanApplication> findById(UUID loanApplicationId) {
            return Optional.ofNullable(application)
                    .filter(value -> value.id().equals(loanApplicationId));
        }

        @Override
        public Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId) {
            return findById(loanApplicationId);
        }

        @Override
        public boolean existsByCustomerIdAndProductCodeAndStatusIn(
                UUID customerId,
                ProductCode productCode,
                Set<LoanApplicationStatus> statuses
        ) {
            return false;
        }

        @Override
        public long nextApplicationNumberSequence() {
            return 1L;
        }
    }

    private class FakeApprovedOfferRepository implements ApprovedOfferRepository {

        private ApprovedOffer savedOffer;

        @Override
        public ApprovedOffer save(ApprovedOffer approvedOffer) {
            savedOffer = approvedOffer;
            return approvedOffer;
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationId(UUID loanApplicationId) {
            return Optional.ofNullable(savedOffer)
                    .filter(value -> value.loanApplicationId().equals(loanApplicationId));
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
            return findByLoanApplicationId(loanApplicationId);
        }

        @Override
        public List<UUID> findExpiredPendingLoanApplicationIds(LocalDateTime now, int batchSize) {
            return List.of();
        }
    }

    private class FakeSalaryAdvanceOfferPolicyRepository implements SalaryAdvanceOfferPolicyRepository {

        private Optional<SalaryAdvanceOfferPolicy> policy = Optional.of(new SalaryAdvanceOfferPolicy(
                POLICY_ID,
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.012000"),
                BigDecimal.ZERO.setScale(2),
                RepaymentMethod.ON_SALARY_DATE,
                7,
                Set.of(1, 2, 3)
        ));

        @Override
        public Optional<SalaryAdvanceOfferPolicy> findActiveDefaultPolicy() {
            return policy;
        }
    }

    private static class FakeUnsecuredConsumerLoanOfferPolicyRepository
            implements UnsecuredConsumerLoanOfferPolicyRepository {

        private Optional<UnsecuredConsumerLoanOfferPolicy> policy = Optional.of(
                new UnsecuredConsumerLoanOfferPolicy(
                        UCL_POLICY_ID,
                        InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                        new BigDecimal("0.018000"),
                        BigDecimal.ZERO.setScale(2),
                        RepaymentMethod.MONTHLY_INSTALLMENT,
                        7,
                        Set.of(3, 6, 9, 12)
                )
        );

        @Override
        public Optional<UnsecuredConsumerLoanOfferPolicy> findActiveDefaultPolicy() {
            return policy;
        }
    }

    private class FakeSalaryAdvanceVerificationRepository implements SalaryAdvanceVerificationRepository {

        @Override
        public SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification) {
            return salaryAdvanceVerification;
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationId(UUID loanApplicationId) {
            if (!LOAN_APPLICATION_ID.equals(loanApplicationId)) {
                return Optional.empty();
            }

            return Optional.of(new SalaryAdvanceVerification(
                    UUID.randomUUID(),
                    LOAN_APPLICATION_ID,
                    CUSTOMER_ID,
                    LINK_ID,
                    LIMIT_ID,
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01"),
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"),
                    SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                    ProductVerificationResult.VERIFIED,
                    limit(6_000_000),
                    limit(0),
                    limit(3_000_000),
                    limit(3_000_000),
                    LocalDateTime.now()
            ));
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
            return findByLoanApplicationId(loanApplicationId);
        }
    }

    private class FakeSalaryAdvanceLimitRepository implements SalaryAdvanceLimitRepository {

        private SalaryAdvanceLimit currentLimit = new SalaryAdvanceLimit(
                LIMIT_ID,
                CUSTOMER_ID,
                LINK_ID,
                limit(6_000_000),
                limit(0),
                limit(3_000_000),
                limit(3_000_000),
                SalaryAdvanceLimitStatus.ACTIVE,
                LocalDateTime.now()
        );
        private SalaryAdvanceLimit savedLimit;

        @Override
        public void acquireCustomerLinkLock(UUID customerId, UUID customerPartnerEmployeeLinkId) {
        }

        @Override
        public Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                UUID customerId,
                UUID customerPartnerEmployeeLinkId
        ) {
            return Optional.ofNullable(currentLimit)
                    .filter(value -> value.customerId().equals(customerId))
                    .filter(value -> value.customerPartnerEmployeeLinkId().equals(customerPartnerEmployeeLinkId));
        }

        @Override
        public SalaryAdvanceLimit save(SalaryAdvanceLimit salaryAdvanceLimit) {
            savedLimit = salaryAdvanceLimit;
            currentLimit = salaryAdvanceLimit;
            return salaryAdvanceLimit;
        }
    }


    private static class FakeLoanApplicationStatusTransitionRepository implements LoanApplicationStatusTransitionRepository {

        private final List<LoanApplicationStatusTransition> savedTransitions = new ArrayList<>();

        @Override
        public int nextSequenceNumber(UUID loanApplicationId) {
            return savedTransitions.size() + 1;
        }

        @Override
        public LoanApplicationStatusTransition save(LoanApplicationStatusTransition transition) {
            savedTransitions.add(transition);
            return transition;
        }
    }

    private static class FakeBusinessAuditPublisher implements BusinessAuditPublisher {

        private final List<BusinessAuditEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publish(BusinessAuditEvent event) {
            publishedEvents.add(event);
        }

        private BusinessAuditEvent lastEvent() {
            return publishedEvents.getLast();
        }
    }
    private static class FakeSalaryAdvanceLimitMovementRepository implements SalaryAdvanceLimitMovementRepository {

        private final List<SalaryAdvanceLimitMovement> savedMovements = new ArrayList<>();
        private boolean releaseMovementExists;

        @Override
        public SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement) {
            savedMovements.add(salaryAdvanceLimitMovement);
            releaseMovementExists = salaryAdvanceLimitMovement.movementType()
                    == SalaryAdvanceLimitMovementType.RESERVATION_RELEASED;
            return salaryAdvanceLimitMovement;
        }

        @Override
        public boolean existsByLoanApplicationIdAndMovementType(
                UUID loanApplicationId,
                SalaryAdvanceLimitMovementType movementType
        ) {
            return releaseMovementExists && movementType == SalaryAdvanceLimitMovementType.RESERVATION_RELEASED;
        }
    }
}
