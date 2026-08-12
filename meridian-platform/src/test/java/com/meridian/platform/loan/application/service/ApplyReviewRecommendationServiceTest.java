package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplyReviewRecommendationServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID REVIEW_CYCLE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime RECOMMENDED_AT = LocalDateTime.of(2026, 7, 6, 9, 0);

    private FakeLoanApplicationRepository loanApplicationRepository;
    private ReadyDocumentChecklistPort documentChecklistPort;
    private LoanReviewCycleRepository reviewCycleRepository;
    private CustomerCorrectionWorkflowService correctionWorkflowService;
    private FakeLoanApplicationStatusTransitionRepository transitionRepository;
    private ApplyReviewRecommendationService service;

    @BeforeEach
    void setUp() {
        loanApplicationRepository = new FakeLoanApplicationRepository();
        documentChecklistPort = new ReadyDocumentChecklistPort();
        reviewCycleRepository = org.mockito.Mockito.mock(LoanReviewCycleRepository.class);
        org.mockito.Mockito.when(reviewCycleRepository.findActiveByLoanApplicationIdForUpdate(LOAN_APPLICATION_ID))
                .thenReturn(Optional.of(LoanApplicationReviewCycle.active(
                        REVIEW_CYCLE_ID, LOAN_APPLICATION_ID, 1, RECOMMENDED_AT.minusHours(1))));
        correctionWorkflowService = org.mockito.Mockito.mock(CustomerCorrectionWorkflowService.class);
        transitionRepository = new FakeLoanApplicationStatusTransitionRepository();
        service = new ApplyReviewRecommendationService(
                loanApplicationRepository,
                documentChecklistPort,
                reviewCycleRepository,
                correctionWorkflowService,
                new LoanApplicationStatusTransitionRecorder(transitionRepository)
        );
    }

    @Test
    void recordsRecommendationWhenOperationContextMatchesAuthoritativeRecord() {
        service.applyReviewRecommendation(command(validContext()));

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, loanApplicationRepository.savedApplication.status());
        assertEquals(1, transitionRepository.savedTransitions.size());
        assertEquals(LOAN_OFFICER_USER_ID, transitionRepository.savedTransitions.getFirst().actorUserId());
    }

    @Test
    void uclApprovalRecommendationReusesCommonApprovalPendingTransition() {
        loanApplicationRepository.application = uclApplication();

        service.applyReviewRecommendation(command(
                LoanReviewRecommendationAction.RECOMMEND_APPROVAL,
                validContext()
        ));

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, loanApplicationRepository.savedApplication.status());
    }

    @Test
    void uclRejectionRecommendationReusesCommonApprovalPendingTransition() {
        loanApplicationRepository.application = uclApplication();

        service.applyReviewRecommendation(command(
                LoanReviewRecommendationAction.RECOMMEND_REJECTION,
                validContext()
        ));

        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, loanApplicationRepository.savedApplication.status());
    }

    @Test
    void uclCorrectionRecommendationsUseSharedWorkflow() {
        for (LoanReviewRecommendationAction action : List.of(
                LoanReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION,
                LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION
        )) {
            loanApplicationRepository.application = uclApplication();
            loanApplicationRepository.savedApplication = null;

            service.applyReviewRecommendation(command(action, validContext()));

            assertEquals(LoanApplicationStatus.RETURNED_FOR_REVISION,
                    loanApplicationRepository.savedApplication.status());
        }
        org.mockito.Mockito.verify(correctionWorkflowService, org.mockito.Mockito.times(2))
                .createFromRecommendation(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void rejectsRecommendationWhileDocumentsAreNotProcessingReady() {
        documentChecklistPort.processingReady = false;
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.applyReviewRecommendation(command(validContext()))
        );

        assertEquals("LOAN_REVIEW_DOCUMENTS_NOT_READY", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
    }

    @Test
    void rejectsSystemActorRecommendationOperationContext() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyReviewRecommendation(command(
                        BusinessOperationContext.system(UUID.fromString("abababab-abab-abab-abab-abababababab"), RECOMMENDED_AT)
                ))
        );

        assertEquals("INVALID_OPERATION_CONTEXT", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
    }

    @Test
    void rejectsMismatchedLoanOfficerOperationContext() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyReviewRecommendation(command(BusinessOperationContext.user(
                        UUID.fromString("abababab-abab-abab-abab-abababababab"),
                        UUID.fromString("00000000-0000-0000-0000-000000000303"),
                        RECOMMENDED_AT
                )))
        );

        assertEquals("INVALID_OPERATION_CONTEXT", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
    }

    @Test
    void rejectsMismatchedRecommendedTimestampOperationContext() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.applyReviewRecommendation(command(BusinessOperationContext.user(
                        UUID.fromString("abababab-abab-abab-abab-abababababab"),
                        LOAN_OFFICER_USER_ID,
                        RECOMMENDED_AT.plusSeconds(1)
                )))
        );

        assertEquals("INVALID_OPERATION_CONTEXT", exception.getErrorCode());
        assertNull(loanApplicationRepository.savedApplication);
    }

    private ApplyReviewRecommendationCommand command(BusinessOperationContext operationContext) {
        return command(LoanReviewRecommendationAction.RECOMMEND_APPROVAL, operationContext);
    }

    private ApplyReviewRecommendationCommand command(
            LoanReviewRecommendationAction action,
            BusinessOperationContext operationContext
    ) {
        return new ApplyReviewRecommendationCommand(
                LOAN_APPLICATION_ID,
                RECOMMENDATION_ID,
                REVIEW_CYCLE_ID,
                LOAN_OFFICER_USER_ID,
                action,
                null,
                isCorrection(action) ? CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED : null,
                isCorrection(action) ? uclCorrectionPlan() : null,
                RECOMMENDED_AT,
                operationContext
        );
    }

    private static boolean isCorrection(LoanReviewRecommendationAction action) {
        return action == LoanReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION
                || action == LoanReviewRecommendationAction.REQUEST_STAFF_CORRECTION;
    }

    private static CorrectionPlanRequest uclCorrectionPlan() {
        return new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.CUSTOMER,
                DocumentType.INCOME_PROOF,
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Replace the income proof.",
                null
        )));
    }

    private BusinessOperationContext validContext() {
        return BusinessOperationContext.user(
                UUID.fromString("abababab-abab-abab-abab-abababababab"),
                LOAN_OFFICER_USER_ID,
                RECOMMENDED_AT
        );
    }

    private static LoanApplication loanApplication() {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                CUSTOMER_ID,
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "SA-20260630-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.UNDER_REVIEW,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                RECOMMENDED_AT.minusDays(1)
        );
    }

    private static LoanApplication uclApplication() {
        LoanApplication salaryAdvance = loanApplication();
        return new LoanApplication(
                salaryAdvance.id(),
                salaryAdvance.customerId(),
                salaryAdvance.loanProductId(),
                "UCL-20260630-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                LoanApplicationStatus.UNDER_REVIEW,
                BigDecimal.valueOf(5_000_000).setScale(2),
                6,
                salaryAdvance.submittedAt()
        );
    }

    private static class ReadyDocumentChecklistPort implements LoanDocumentChecklistPort {

        private boolean processingReady = true;

        @Override
        public SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode) {
            return new SubmissionChecklistInitialState(true);
        }

        @Override
        public void createSubmissionChecklist(
                UUID loanApplicationId,
                ProductCode productCode,
                BusinessOperationContext operationContext
        ) {
        }

        @Override
        public boolean isProcessingReady(UUID loanApplicationId) {
            return processingReady;
        }
    }

    private static class FakeLoanApplicationRepository implements LoanApplicationRepository {
        @Override
        public void acquireWorkflowLock(UUID loanApplicationId) {
        }


        @Override
        public void acquireCustomerProductLock(UUID customerId, ProductCode productCode) {
        }

        private LoanApplication application = loanApplication();
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
}
