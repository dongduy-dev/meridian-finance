package com.meridian.platform.loan.application.service.unsecured;

import com.meridian.platform.loan.application.service.CustomerCorrectionWorkflowService;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.CompleteUnsecuredConsumerLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanManualVerificationOutcome;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageUnsecuredConsumerLoanVerificationServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 9, 0);

    private LoanApplicationRepository applicationRepository;
    private UnsecuredConsumerLoanVerificationRepository verificationRepository;
    private LoanDocumentChecklistPort documentChecklistPort;
    private CustomerCorrectionWorkflowService correctionWorkflowService;
    private LoanApplicationStatusTransitionRecorder transitionRecorder;
    private BusinessAuditPublisher auditPublisher;
    private ManageUnsecuredConsumerLoanVerificationService service;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(LoanApplicationRepository.class);
        verificationRepository = mock(UnsecuredConsumerLoanVerificationRepository.class);
        documentChecklistPort = mock(LoanDocumentChecklistPort.class);
        correctionWorkflowService = mock(CustomerCorrectionWorkflowService.class);
        transitionRecorder = mock(LoanApplicationStatusTransitionRecorder.class);
        auditPublisher = mock(BusinessAuditPublisher.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                REVIEWER_ID,
                "loan.officer@meridian.local",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review")
        ));
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.SUBMITTED)));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationRepository.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(pendingVerification()));
        when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentChecklistPort.isProcessingReady(APPLICATION_ID)).thenReturn(true);

        service = new ManageUnsecuredConsumerLoanVerificationService(
                applicationRepository,
                verificationRepository,
                documentChecklistPort,
                correctionWorkflowService,
                transitionRecorder,
                auditPublisher,
                currentUserProvider,
                Clock.fixed(Instant.parse("2026-08-11T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void startsManualVerificationWithoutCompletingTheDecision() {
        UnsecuredConsumerLoanVerificationDto result = service.startManualVerification(APPLICATION_ID);

        assertEquals("VERIFICATION_PENDING", result.status());
        assertEquals("PENDING_MANUAL_REVIEW", result.productVerificationResult());
        assertNull(result.reviewedAt());
        verify(applicationRepository).acquireWorkflowLock(APPLICATION_ID);
        verify(verificationRepository, never()).save(any());
        verify(transitionRecorder).record(any(), any(), org.mockito.ArgumentMatchers.isNull());
        verify(auditPublisher).publish(any());
    }

    @Test
    void rejectsStartForAnotherProduct() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(salaryAdvance(LoanApplicationStatus.SUBMITTED)));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("UCL_VERIFICATION_NOT_APPLICABLE", exception.getErrorCode());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void rejectsStartFromWrongApplicationStatus() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("PRODUCT_VERIFICATION_START_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void rejectsStartWithoutUclVerificationEvidence() {
        when(verificationRepository.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID)).thenReturn(Optional.empty());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("UCL_VERIFICATION_REQUIRED", exception.getErrorCode());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void rejectsStartWhileDocumentsAreNotProcessingReady() {
        when(documentChecklistPort.isProcessingReady(APPLICATION_ID)).thenReturn(false);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("UCL_VERIFICATION_DOCUMENTS_NOT_READY", exception.getErrorCode());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void completesManualVerificationWithAuthoritativeReviewerTimeAndNormalizedNote() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));

        UnsecuredConsumerLoanVerificationDto result = service.completeManualVerification(
                APPLICATION_ID,
                new CompleteUnsecuredConsumerLoanVerificationRequest("  Evidence is consistent.  ")
        );

        assertEquals("SUBMITTED", result.status());
        assertEquals("VERIFIED", result.productVerificationResult());
        assertEquals(NOW, result.reviewedAt());
        ArgumentCaptor<UnsecuredConsumerLoanVerification> verificationCaptor =
                ArgumentCaptor.forClass(UnsecuredConsumerLoanVerification.class);
        verify(verificationRepository).save(verificationCaptor.capture());
        assertEquals(REVIEWER_ID, verificationCaptor.getValue().reviewedByUserId());
        assertEquals(NOW, verificationCaptor.getValue().reviewedAt());
        assertEquals("Evidence is consistent.", verificationCaptor.getValue().assessmentNote());
        verify(transitionRecorder).record(any(), any(), org.mockito.ArgumentMatchers.isNull());
        verify(auditPublisher).publish(any());
    }

    @Test
    void failedVerificationIsTerminalWithoutCreatingCorrection() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));

        UnsecuredConsumerLoanVerificationDto result = service.completeManualVerification(
                APPLICATION_ID,
                new CompleteUnsecuredConsumerLoanVerificationRequest(
                        UnsecuredConsumerLoanManualVerificationOutcome.FAILED,
                        "Identity and employment evidence could not be verified.",
                        null,
                        null
                )
        );

        assertEquals("VERIFICATION_FAILED", result.status());
        assertEquals("FAILED", result.productVerificationResult());
        verify(correctionWorkflowService, never()).createFromProductVerification(
                any(), any(), any(), any()
        );
    }

    @Test
    void moreInformationCreatesCorrectionAndReturnsApplicationForRevision() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));
        CorrectionPlanRequest plan = uclReplacementPlan();

        UnsecuredConsumerLoanVerificationDto result = service.completeManualVerification(
                APPLICATION_ID,
                new CompleteUnsecuredConsumerLoanVerificationRequest(
                        UnsecuredConsumerLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION,
                        "The income evidence must be replaced.",
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        plan
                )
        );

        assertEquals("RETURNED_FOR_REVISION", result.status());
        assertEquals("REQUIRES_MORE_INFORMATION", result.productVerificationResult());
        verify(correctionWorkflowService).createFromProductVerification(
                any(),
                org.mockito.ArgumentMatchers.eq(CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED),
                org.mockito.ArgumentMatchers.same(plan),
                any()
        );
    }

    @Test
    void moreInformationRequiresCorrectionReasonAndPlan() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.completeManualVerification(
                        APPLICATION_ID,
                        new CompleteUnsecuredConsumerLoanVerificationRequest(
                                UnsecuredConsumerLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION,
                                "More evidence is required.",
                                null,
                                null
                        )
                )
        );

        assertEquals("INVALID_CORRECTION_PLAN", exception.getErrorCode());
        verify(applicationRepository, never()).acquireWorkflowLock(any());
    }

    @Test
    void terminalOutcomeRejectsCorrectionFields() {
        for (UnsecuredConsumerLoanManualVerificationOutcome outcome : List.of(
                UnsecuredConsumerLoanManualVerificationOutcome.VERIFIED,
                UnsecuredConsumerLoanManualVerificationOutcome.FAILED
        )) {
            BusinessRuleViolationException exception = assertThrows(
                    BusinessRuleViolationException.class,
                    () -> service.completeManualVerification(
                            APPLICATION_ID,
                            new CompleteUnsecuredConsumerLoanVerificationRequest(
                                    outcome,
                                    "Terminal assessment.",
                                    CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                                    uclReplacementPlan()
                            )
                    )
            );
            assertEquals("INVALID_CORRECTION_PLAN", exception.getErrorCode());
        }
        verify(applicationRepository, never()).acquireWorkflowLock(any());
    }

    @Test
    void rejectsBlankAssessmentNote() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.completeManualVerification(
                        APPLICATION_ID,
                        new CompleteUnsecuredConsumerLoanVerificationRequest("   ")
                )
        );

        assertEquals("UCL_VERIFICATION_ASSESSMENT_REQUIRED", exception.getErrorCode());
        verify(verificationRepository, never()).save(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void documentsBecomingUnreadyBlocksCompletion() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));
        when(documentChecklistPort.isProcessingReady(APPLICATION_ID)).thenReturn(false);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.completeManualVerification(
                        APPLICATION_ID,
                        new CompleteUnsecuredConsumerLoanVerificationRequest("Evidence is consistent.")
                )
        );

        assertEquals("UCL_VERIFICATION_DOCUMENTS_NOT_READY", exception.getErrorCode());
        verify(verificationRepository, never()).save(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void rejectsCompletionFromWrongApplicationStatus() {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.completeManualVerification(
                        APPLICATION_ID,
                        new CompleteUnsecuredConsumerLoanVerificationRequest("Evidence is consistent.")
                )
        );

        assertEquals("PRODUCT_VERIFICATION_COMPLETION_NOT_ALLOWED", exception.getErrorCode());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void duplicateCompletionCannotProduceAnotherDecision() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));
        when(verificationRepository.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(
                pendingVerification().completeManualReview(REVIEWER_ID, NOW.minusMinutes(1), "First decision.")
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.completeManualVerification(
                        APPLICATION_ID,
                        new CompleteUnsecuredConsumerLoanVerificationRequest("Second decision.")
                )
        );

        assertEquals("PRODUCT_VERIFICATION_NOT_PENDING", exception.getErrorCode());
        verify(verificationRepository, never()).save(any());
        verify(applicationRepository, never()).save(any());
    }

    private LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(
                APPLICATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "UCL-20260811-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                BigDecimal.valueOf(5_000_000).setScale(2),
                6,
                NOW.minusDays(1)
        );
    }

    private LoanApplication salaryAdvance(LoanApplicationStatus status) {
        LoanApplication ucl = application(status);
        return new LoanApplication(
                ucl.id(),
                ucl.customerId(),
                ucl.loanProductId(),
                "SA-20260811-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                ucl.requestedAmount(),
                1,
                ucl.submittedAt()
        );
    }

    private UnsecuredConsumerLoanVerification pendingVerification() {
        return UnsecuredConsumerLoanVerification.pendingManualReview(
                UUID.randomUUID(),
                application(LoanApplicationStatus.SUBMITTED),
                NOW.minusDays(1)
        );
    }

    private CorrectionPlanRequest uclReplacementPlan() {
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
}
