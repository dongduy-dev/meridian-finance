package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.CompleteCollateralLoanVerificationRequest;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.Collateral;
import com.meridian.platform.loan.domain.model.CollateralLoanManualVerificationOutcome;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.CollateralType;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManageCollateralLoanVerificationServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VERIFICATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID REVIEWER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 9, 0);

    @Mock private LoanApplicationRepository applications;
    @Mock private CollateralLoanVerificationRepository verifications;
    @Mock private CollateralRepository collaterals;
    @Mock private LoanDocumentChecklistPort documents;
    @Mock private CustomerCorrectionWorkflowService corrections;
    @Mock private LoanApplicationStatusTransitionRecorder transitions;
    @Mock private BusinessAuditPublisher audits;
    @Mock private CurrentUserProvider currentUser;

    private ManageCollateralLoanVerificationService service;

    @BeforeEach
    void setUp() {
        service = new ManageCollateralLoanVerificationService(
                applications,
                verifications,
                collaterals,
                documents,
                corrections,
                transitions,
                audits,
                currentUser,
                Clock.fixed(Instant.parse("2026-08-19T09:00:00Z"), ZoneOffset.UTC)
        );
        lenient().when(currentUser.currentUser()).thenReturn(new AuthenticatedUser(
                REVIEWER_ID,
                "loan-officer@meridian.test",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review")
        ));
        lenient().when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.SUBMITTED)));
        lenient().when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(verifications.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(pendingVerification()));
        lenient().when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(documents.isProcessingReady(APPLICATION_ID)).thenReturn(true);
        lenient().when(collaterals.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(List.of(collateral()));
    }

    @Test
    void startsVerificationAndReturnsRestrictedStaffAssessmentProjection() {
        CollateralLoanVerificationStartDto result = service.startManualVerification(APPLICATION_ID);

        assertEquals(VERIFICATION_ID, result.verificationId());
        assertEquals("VERIFICATION_PENDING", result.status());
        assertEquals("PENDING_MANUAL_REVIEW", result.productVerificationResult());
        assertEquals("CAR", result.collateral().collateralType());
        assertEquals("Customer vehicle", result.collateral().description());
        assertEquals(new BigDecimal("25000000"), result.collateral().estimatedValue());
        verify(applications).acquireWorkflowLock(APPLICATION_ID);
        verify(transitions).record(any(), any(), org.mockito.ArgumentMatchers.isNull());
        verify(audits).publish(any());
    }

    @Test
    void duplicateStartUsesLifecycleConflictInsteadOfReplay() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("PRODUCT_VERIFICATION_START_NOT_ALLOWED", exception.getErrorCode());
        verify(applications, never()).save(any());
        verify(audits, never()).publish(any());
    }

    @Test
    void rejectsWrongProduct() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(uclApplication(LoanApplicationStatus.SUBMITTED)));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("COLLATERAL_VERIFICATION_NOT_APPLICABLE", exception.getErrorCode());
    }

    @Test
    void rejectsMissingVerificationEvidence() {
        when(verifications.findLatestByLoanApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.empty());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("COLLATERAL_VERIFICATION_REQUIRED", exception.getErrorCode());
    }

    @Test
    void rejectsMissingOrMultipleCollateralFacts() {
        when(collaterals.findByLoanApplicationId(APPLICATION_ID)).thenReturn(List.of());
        BusinessStateConflictException missing = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        when(collaterals.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(List.of(collateral(), collateral()));
        BusinessStateConflictException multiple = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("SYSTEM_STATE_CONFLICT", missing.getErrorCode());
        assertEquals("SYSTEM_STATE_CONFLICT", multiple.getErrorCode());
    }

    @Test
    void rejectsStartWhenDocumentsAreNotProcessingReady() {
        when(documents.isProcessingReady(APPLICATION_ID)).thenReturn(false);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startManualVerification(APPLICATION_ID)
        );

        assertEquals("COLLATERAL_VERIFICATION_DOCUMENTS_NOT_READY", exception.getErrorCode());
        verify(applications, never()).save(any());
    }

    @Test
    void completesVerifiedCycleUsingExpectedIdAndNormalizedAssessment() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));

        CollateralLoanVerificationDto result = service.completeManualVerification(
                APPLICATION_ID,
                terminalRequest(CollateralLoanManualVerificationOutcome.VERIFIED)
        );

        assertEquals(VERIFICATION_ID, result.verificationId());
        assertEquals("SUBMITTED", result.status());
        assertEquals("VERIFIED", result.productVerificationResult());
        assertEquals(NOW, result.reviewedAt());
        ArgumentCaptor<CollateralLoanVerification> captor =
                ArgumentCaptor.forClass(CollateralLoanVerification.class);
        verify(verifications).save(captor.capture());
        assertEquals(REVIEWER_ID, captor.getValue().reviewedByUserId());
        assertEquals("Evidence is sufficient.", captor.getValue().assessmentNote());
    }

    @Test
    void failedCycleIsTerminalWithoutCorrection() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));

        CollateralLoanVerificationDto result = service.completeManualVerification(
                APPLICATION_ID,
                terminalRequest(CollateralLoanManualVerificationOutcome.FAILED)
        );

        assertEquals("VERIFICATION_FAILED", result.status());
        verify(corrections, never()).createFromProductVerification(any(), any(), any(), any());
    }

    @Test
    void moreInformationCreatesDocumentOnlyCorrection() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));
        CorrectionPlanRequest plan = replacementPlan();

        CollateralLoanVerificationDto result = service.completeManualVerification(
                APPLICATION_ID,
                new CompleteCollateralLoanVerificationRequest(
                        VERIFICATION_ID,
                        CollateralLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION,
                        "Ownership evidence must be replaced.",
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        plan
                )
        );

        assertEquals("RETURNED_FOR_REVISION", result.status());
        verify(corrections).createFromProductVerification(
                any(),
                org.mockito.ArgumentMatchers.eq(CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED),
                org.mockito.ArgumentMatchers.same(plan),
                any()
        );
    }

    @Test
    void rejectsStaleExpectedVerificationIdBeforeMutation() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));
        CompleteCollateralLoanVerificationRequest request = new CompleteCollateralLoanVerificationRequest(
                UUID.randomUUID(),
                CollateralLoanManualVerificationOutcome.VERIFIED,
                "Evidence is sufficient.",
                null,
                null
        );

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.completeManualVerification(APPLICATION_ID, request)
        );

        assertEquals("STALE_COLLATERAL_VERIFICATION", exception.getErrorCode());
        verify(verifications, never()).save(any());
        verify(applications, never()).save(any());
    }

    @Test
    void terminalOutcomeRejectsCorrectionFieldsBeforeLocking() {
        CompleteCollateralLoanVerificationRequest request = new CompleteCollateralLoanVerificationRequest(
                VERIFICATION_ID,
                CollateralLoanManualVerificationOutcome.VERIFIED,
                "Evidence is sufficient.",
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan()
        );

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.completeManualVerification(APPLICATION_ID, request)
        );

        assertEquals("INVALID_CORRECTION_PLAN", exception.getErrorCode());
        verify(applications, never()).acquireWorkflowLock(any());
    }

    @Test
    void moreInformationRequiresReasonAndPlan() {
        CompleteCollateralLoanVerificationRequest request = new CompleteCollateralLoanVerificationRequest(
                VERIFICATION_ID,
                CollateralLoanManualVerificationOutcome.REQUIRES_MORE_INFORMATION,
                "More evidence is required.",
                null,
                null
        );

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.completeManualVerification(APPLICATION_ID, request)
        );

        assertEquals("INVALID_CORRECTION_PLAN", exception.getErrorCode());
    }

    @Test
    void documentsBecomingUnreadyBlocksCompletion() {
        when(applications.findByIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.VERIFICATION_PENDING)));
        when(documents.isProcessingReady(APPLICATION_ID)).thenReturn(false);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.completeManualVerification(
                        APPLICATION_ID,
                        terminalRequest(CollateralLoanManualVerificationOutcome.VERIFIED)
                )
        );

        assertEquals("COLLATERAL_VERIFICATION_DOCUMENTS_NOT_READY", exception.getErrorCode());
        verify(verifications, never()).save(any());
    }

    private CompleteCollateralLoanVerificationRequest terminalRequest(
            CollateralLoanManualVerificationOutcome outcome
    ) {
        return new CompleteCollateralLoanVerificationRequest(
                VERIFICATION_ID,
                outcome,
                "  Evidence is sufficient.  ",
                null,
                null
        );
    }

    private CorrectionPlanRequest replacementPlan() {
        return new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.CUSTOMER,
                DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE,
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Replace the ownership evidence.",
                null
        )));
    }

    private CollateralLoanVerification pendingVerification() {
        return CollateralLoanVerification.pendingManualReview(
                VERIFICATION_ID,
                application(LoanApplicationStatus.SUBMITTED),
                NOW.minusDays(1)
        );
    }

    private Collateral collateral() {
        return new Collateral(
                UUID.randomUUID(),
                APPLICATION_ID,
                CollateralType.CAR,
                "Customer vehicle",
                new BigDecimal("25000000"),
                "Customer-owned",
                "Operational condition",
                NOW.minusDays(1)
        );
    }

    private LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(
                APPLICATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CL-20260819-000001",
                ProductCode.COLLATERAL_LOAN,
                ProductType.SECURED,
                status,
                new BigDecimal("15000000"),
                12,
                NOW.minusDays(1)
        );
    }

    private LoanApplication uclApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                APPLICATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "UCL-20260819-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                new BigDecimal("5000000"),
                6,
                NOW.minusDays(1)
        );
    }
}
