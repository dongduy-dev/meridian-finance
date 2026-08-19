package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.CollateralLoanManualVerificationOutcome;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartLoanApplicationReviewServiceTest {

    private final UUID applicationId = UUID.randomUUID();
    private LoanApplicationRepository applicationRepository;
    private LoanDocumentChecklistPort documentChecklistPort;
    private UnsecuredConsumerLoanVerificationRepository uclVerificationRepository;
    private CollateralLoanVerificationRepository collateralVerificationRepository;
    private LoanReviewCycleRepository reviewCycleRepository;
    private LoanApplicationStatusTransitionRecorder transitionRecorder;
    private BusinessAuditPublisher auditPublisher;
    private StartLoanApplicationReviewService service;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(LoanApplicationRepository.class);
        documentChecklistPort = mock(LoanDocumentChecklistPort.class);
        uclVerificationRepository = mock(UnsecuredConsumerLoanVerificationRepository.class);
        collateralVerificationRepository = mock(CollateralLoanVerificationRepository.class);
        reviewCycleRepository = mock(LoanReviewCycleRepository.class);
        transitionRecorder = mock(LoanApplicationStatusTransitionRecorder.class);
        auditPublisher = mock(BusinessAuditPublisher.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(),
                "loan.officer@meridian.local",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review")
        ));
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application()));
        when(reviewCycleRepository.nextCycleNumber(applicationId)).thenReturn(1);
        when(reviewCycleRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new StartLoanApplicationReviewService(
                applicationRepository,
                documentChecklistPort,
                uclVerificationRepository,
                new CollateralLoanReviewGate(collateralVerificationRepository),
                reviewCycleRepository,
                transitionRecorder,
                auditPublisher,
                currentUserProvider,
                Clock.fixed(Instant.parse("2026-07-19T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsReviewStartWhenChecklistIsNotProcessingReady() {
        when(documentChecklistPort.isProcessingReady(applicationId)).thenReturn(false);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startReview(applicationId)
        );

        assertEquals("LOAN_REVIEW_DOCUMENTS_NOT_READY", exception.getErrorCode());
        verify(applicationRepository).acquireWorkflowLock(applicationId);
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startsReviewExplicitlyWhenChecklistIsProcessingReady() {
        when(documentChecklistPort.isProcessingReady(applicationId)).thenReturn(true);
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("UNDER_REVIEW", service.startReview(applicationId).status());
        verify(transitionRecorder).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void pendingUclVerificationCannotStartReview() {
        LoanApplication application = uclApplication(LoanApplicationStatus.SUBMITTED);
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(uclVerificationRepository.findLatestByLoanApplicationId(applicationId))
                .thenReturn(Optional.of(pendingUclVerification(application)));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startReview(applicationId)
        );

        assertEquals("PRODUCT_VERIFICATION_PENDING", exception.getErrorCode());
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pendingCollateralVerificationCannotStartReview() {
        LoanApplication application = collateralApplication(LoanApplicationStatus.SUBMITTED);
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(collateralVerificationRepository.findByLoanApplicationId(applicationId))
                .thenReturn(Optional.of(CollateralLoanVerification.pendingManualReview(
                        UUID.randomUUID(), application, LocalDateTime.of(2026, 7, 19, 7, 0)
                )));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startReview(applicationId)
        );

        assertEquals("PRODUCT_VERIFICATION_PENDING", exception.getErrorCode());
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(documentChecklistPort, never()).isProcessingReady(applicationId);
    }

    @Test
    void missingCollateralVerificationFailsClosedBeforeReview() {
        when(applicationRepository.findByIdForUpdate(applicationId))
                .thenReturn(Optional.of(collateralApplication(LoanApplicationStatus.SUBMITTED)));
        when(collateralVerificationRepository.findByLoanApplicationId(applicationId))
                .thenReturn(Optional.empty());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startReview(applicationId)
        );

        assertEquals("COLLATERAL_VERIFICATION_REQUIRED", exception.getErrorCode());
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingUclVerificationCannotStartReview() {
        LoanApplication application = uclApplication(LoanApplicationStatus.SUBMITTED);
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(uclVerificationRepository.findLatestByLoanApplicationId(applicationId)).thenReturn(Optional.empty());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startReview(applicationId)
        );

        assertEquals("UCL_VERIFICATION_REQUIRED", exception.getErrorCode());
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void verificationPendingUclCannotStartReview() {
        LoanApplication application = uclApplication(LoanApplicationStatus.VERIFICATION_PENDING);
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(uclVerificationRepository.findLatestByLoanApplicationId(applicationId))
                .thenReturn(Optional.of(pendingUclVerification(application)));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.startReview(applicationId)
        );

        assertEquals("PRODUCT_VERIFICATION_PENDING", exception.getErrorCode());
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void verifiedUclCanStartCommonLoanOfficerReview() {
        LoanApplication application = uclApplication(LoanApplicationStatus.SUBMITTED);
        UnsecuredConsumerLoanVerification verified = pendingUclVerification(application)
                .completeManualReview(UUID.randomUUID(), LocalDateTime.of(2026, 7, 19, 7, 30), "Verified evidence.");
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(uclVerificationRepository.findLatestByLoanApplicationId(applicationId)).thenReturn(Optional.of(verified));
        when(documentChecklistPort.isProcessingReady(applicationId)).thenReturn(true);
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("UNDER_REVIEW", service.startReview(applicationId).status());
    }

    @Test
    void verifiedCollateralCanStartCommonLoanOfficerReview() {
        LoanApplication application = collateralApplication(LoanApplicationStatus.SUBMITTED);
        CollateralLoanVerification verified = CollateralLoanVerification.pendingManualReview(
                UUID.randomUUID(), application, LocalDateTime.of(2026, 7, 19, 7, 0)
        ).completeManualReview(
                CollateralLoanManualVerificationOutcome.VERIFIED,
                UUID.randomUUID(),
                LocalDateTime.of(2026, 7, 19, 7, 30),
                "Ownership evidence is sufficient."
        );
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application));
        when(collateralVerificationRepository.findByLoanApplicationId(applicationId))
                .thenReturn(Optional.of(verified));
        when(documentChecklistPort.isProcessingReady(applicationId)).thenReturn(true);
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("UNDER_REVIEW", service.startReview(applicationId).status());
    }

    private LoanApplication application() {
        return new LoanApplication(
                applicationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SA-20260719-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.SUBMITTED,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                LocalDateTime.of(2026, 7, 19, 7, 0)
        );
    }

    private LoanApplication uclApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                applicationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "UCL-20260719-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                BigDecimal.valueOf(5_000_000).setScale(2),
                6,
                LocalDateTime.of(2026, 7, 19, 7, 0)
        );
    }

    private LoanApplication collateralApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                applicationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CL-20260719-000001",
                ProductCode.COLLATERAL_LOAN,
                ProductType.SECURED,
                status,
                BigDecimal.valueOf(25_000_000).setScale(2),
                12,
                LocalDateTime.of(2026, 7, 19, 7, 0)
        );
    }

    private UnsecuredConsumerLoanVerification pendingUclVerification(LoanApplication application) {
        return UnsecuredConsumerLoanVerification.pendingManualReview(
                UUID.randomUUID(),
                application,
                LocalDateTime.of(2026, 7, 19, 7, 0)
        );
    }
}
