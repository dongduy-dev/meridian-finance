package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.service.salaryadvance.SalaryAdvanceReservationReleaseService;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationCancellationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationCancellation;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.shared.application.audit.BusinessAuditEvidenceReader;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelLoanApplicationServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID CUSTOMER_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );
    private static final UUID USER_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
    );
    private static final UUID CORRECTION_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001"
    );
    private static final UUID LIMIT_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
    );
    private static final UUID LINK_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
    );
    private static final UUID RESERVATION_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001"
    );
    private static final UUID RELEASE_ID = UUID.fromString(
            "80000000-0000-0000-0000-000000000001"
    );
    private static final BigDecimal REQUESTED_AMOUNT = new BigDecimal("3000000.00");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 9, 0);

    @Mock LoanApplicationCancellationRepository cancellations;
    @Mock LoanApplicationRepository applications;
    @Mock LoanCorrectionRepository corrections;
    @Mock SalaryAdvanceVerificationRepository verifications;
    @Mock SalaryAdvanceLimitRepository limits;
    @Mock SalaryAdvanceLimitMovementRepository movements;
    @Mock SalaryAdvanceReservationReleaseService reservationReleases;
    @Mock LoanApplicationStatusTransitionRecorder transitionRecorder;
    @Mock LoanApplicationStatusTransitionRepository transitionEvidence;
    @Mock CurrentUserProvider currentUsers;
    @Mock BusinessAuditPublisher auditPublisher;
    @Mock BusinessAuditEvidenceReader auditEvidence;

    private CancelLoanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CancelLoanApplicationService(
                cancellations,
                applications,
                corrections,
                verifications,
                limits,
                movements,
                reservationReleases,
                transitionRecorder,
                transitionEvidence,
                currentUsers,
                auditPublisher,
                auditEvidence,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
        when(currentUsers.currentUser()).thenReturn(customerActor());
    }

    @Test
    void ownCancellationUsesAuthoritativeReservationAndPersistsTerminalEvidence() {
        UUID requestId = UUID.randomUUID();
        LoanApplication application = application(
                CUSTOMER_ID,
                LoanApplicationStatus.RETURNED_FOR_REVISION
        );
        LoanCorrectionRequest correction = correction(LoanCorrectionRequestStatus.OPEN, null);
        SalaryAdvanceLimitMovement reservation = reservation();
        SalaryAdvanceLimitMovement release = release();
        stubNewCancellation(application, correction, reservation, release);

        CancelLoanApplicationUseCase.Result result = service.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, APPLICATION_ID)
        );

        assertEquals(LoanApplicationStatus.CANCELLED, result.resultingStatus());
        assertEquals(NOW, result.cancelledAt());
        assertFalse(result.idempotentReplay());
        verify(reservationReleases).releaseReservationOnce(
                eq(application),
                any(),
                eq(com.meridian.platform.loan.domain.model.salaryadvance.ReservationReleaseTrigger.CUSTOMER_CANCELLATION)
        );
        verify(transitionRecorder).record(any(), any(), eq("CUSTOMER_CANCELLATION"));
        verify(auditPublisher).publish(any());

        ArgumentCaptor<LoanApplicationCancellation> evidence =
                ArgumentCaptor.forClass(LoanApplicationCancellation.class);
        verify(cancellations).saveIfAbsent(evidence.capture());
        assertEquals(requestId, evidence.getValue().requestId());
        assertEquals(RELEASE_ID, evidence.getValue().reservationReleaseMovementId());
        assertEquals(CORRECTION_ID, evidence.getValue().correctionRequestId());
        assertEquals(REQUESTED_AMOUNT, release.amount());

        ArgumentCaptor<LoanCorrectionRequest> terminalCorrection =
                ArgumentCaptor.forClass(LoanCorrectionRequest.class);
        verify(corrections).saveRequest(terminalCorrection.capture());
        assertEquals(LoanCorrectionRequestStatus.CANCELLED,
                terminalCorrection.getValue().status());
        assertFalse(terminalCorrection.getValue().isActive());
    }

    @Test
    void foreignApplicationIsConcealedBeforeFinancialWork() {
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(
                application(UUID.randomUUID(), LoanApplicationStatus.RETURNED_FOR_REVISION)
        ));

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> service.cancel(new CancelLoanApplicationUseCase.Command(
                        UUID.randomUUID(), APPLICATION_ID
                ))
        );

        assertEquals("LOAN_APPLICATION_NOT_FOUND", exception.getErrorCode());
        verify(reservationReleases, never()).releaseReservationOnce(any(), any(), any());
    }

    @Test
    void nonReturnedApplicationFailsWithoutReleasingReservation() {
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(
                application(CUSTOMER_ID, LoanApplicationStatus.SUBMITTED)
        ));
        when(cancellations.findByRequestId(any())).thenReturn(Optional.empty());
        when(cancellations.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.cancel(new CancelLoanApplicationUseCase.Command(
                        UUID.randomUUID(), APPLICATION_ID
                ))
        );

        assertEquals("LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED", exception.getErrorCode());
        verify(reservationReleases, never()).releaseReservationOnce(any(), any(), any());
    }

    @Test
    void staffCannotUseCustomerCancellationEvenWithPermission() {
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(),
                "staff@meridian.test",
                "STAFF",
                null,
                Set.of("APPROVER"),
                Set.of("loan:cancel:own", "loan:read", "approval:decide")
        ));

        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> service.cancel(new CancelLoanApplicationUseCase.Command(
                        UUID.randomUUID(), APPLICATION_ID
                ))
        );

        assertEquals("LOAN_APPLICATION_CANCELLATION_ACCESS_DENIED", exception.getErrorCode());
        verify(applications, never()).acquireWorkflowLock(any());
    }

    @Test
    void sameRequestReplayReturnsDurableResultWithoutNewEffects() {
        UUID requestId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();
        LoanApplication application = application(CUSTOMER_ID, LoanApplicationStatus.CANCELLED);
        LoanApplicationCancellation cancellation = new LoanApplicationCancellation(
                cancellationId,
                APPLICATION_ID,
                CORRECTION_ID,
                RELEASE_ID,
                requestId,
                USER_ID,
                NOW
        );
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(cancellations.findByRequestId(requestId)).thenReturn(Optional.of(cancellation));
        when(corrections.findRequestById(CORRECTION_ID)).thenReturn(Optional.of(
                correction(LoanCorrectionRequestStatus.CANCELLED, NOW)
        ));
        when(movements.findByLoanApplicationIdAndMovementType(
                APPLICATION_ID, SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
        )).thenReturn(List.of(release()));
        when(transitionEvidence.countMatching(
                APPLICATION_ID,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationStatus.CANCELLED,
                LoanApplicationTransitionAction.CANCEL_APPLICATION
        )).thenReturn(1L);
        when(auditEvidence.countMatchingOperation(
                cancellationId,
                BusinessAuditAction.LOAN_APPLICATION_CANCELLED,
                BusinessAuditEntityType.LOAN_APPLICATION,
                APPLICATION_ID
        )).thenReturn(1L);
        when(auditEvidence.countMatchingOperation(
                cancellationId,
                BusinessAuditAction.RESERVATION_RELEASED,
                BusinessAuditEntityType.SALARY_ADVANCE_LIMIT_MOVEMENT,
                RELEASE_ID
        )).thenReturn(1L);

        CancelLoanApplicationUseCase.Result result = service.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, APPLICATION_ID)
        );

        assertEquals(true, result.idempotentReplay());
        assertEquals(NOW, result.cancelledAt());
        verify(reservationReleases, never()).releaseReservationOnce(any(), any(), any());
        verify(cancellations, never()).saveIfAbsent(any());
        verify(transitionRecorder, never()).record(any(), any(), any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void uclCancellationPersistsNoSalaryReleaseEvidenceOrEffect() {
        UUID requestId = UUID.randomUUID();
        LoanApplication application = uclApplication(
                CUSTOMER_ID,
                LoanApplicationStatus.RETURNED_FOR_REVISION
        );
        LoanCorrectionRequest correction = uclCorrection(LoanCorrectionRequestStatus.OPEN, null);
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(cancellations.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(cancellations.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(correction));
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(corrections.saveRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cancellations.saveIfAbsent(any())).thenReturn(true);

        CancelLoanApplicationUseCase.Result result = service.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, APPLICATION_ID)
        );

        assertEquals(LoanApplicationStatus.CANCELLED, result.resultingStatus());
        ArgumentCaptor<LoanApplicationCancellation> evidence =
                ArgumentCaptor.forClass(LoanApplicationCancellation.class);
        verify(cancellations).saveIfAbsent(evidence.capture());
        assertEquals(null, evidence.getValue().reservationReleaseMovementId());
        verify(reservationReleases, never()).releaseReservationOnce(any(), any(), any());
        verify(verifications, never()).findByLoanApplicationId(any());
        verify(limits, never()).acquireCustomerLinkLock(any(), any());
    }

    @Test
    void exactUclCancellationReplayRequiresZeroSalaryEvidence() {
        UUID requestId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();
        LoanApplication application = uclApplication(CUSTOMER_ID, LoanApplicationStatus.CANCELLED);
        LoanApplicationCancellation cancellation = new LoanApplicationCancellation(
                cancellationId,
                APPLICATION_ID,
                CORRECTION_ID,
                null,
                requestId,
                USER_ID,
                NOW
        );
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(cancellations.findByRequestId(requestId)).thenReturn(Optional.of(cancellation));
        when(corrections.findRequestById(CORRECTION_ID)).thenReturn(Optional.of(
                uclCorrection(LoanCorrectionRequestStatus.CANCELLED, NOW)
        ));
        when(transitionEvidence.countMatching(
                APPLICATION_ID,
                LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationStatus.CANCELLED,
                LoanApplicationTransitionAction.CANCEL_APPLICATION
        )).thenReturn(1L);
        when(auditEvidence.countMatchingOperation(
                cancellationId,
                BusinessAuditAction.LOAN_APPLICATION_CANCELLED,
                BusinessAuditEntityType.LOAN_APPLICATION,
                APPLICATION_ID
        )).thenReturn(1L);

        CancelLoanApplicationUseCase.Result result = service.cancel(
                new CancelLoanApplicationUseCase.Command(requestId, APPLICATION_ID)
        );

        assertEquals(true, result.idempotentReplay());
        verify(auditEvidence).countMatchingOperationAction(
                cancellationId,
                BusinessAuditAction.RESERVATION_RELEASED
        );
        verify(reservationReleases, never()).releaseReservationOnce(any(), any(), any());
    }

    private void stubNewCancellation(
            LoanApplication application,
            LoanCorrectionRequest correction,
            SalaryAdvanceLimitMovement reservation,
            SalaryAdvanceLimitMovement release
    ) {
        when(applications.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(cancellations.findByRequestId(any())).thenReturn(Optional.empty());
        when(cancellations.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
        when(corrections.findActiveRequestByApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.of(correction));
        when(verifications.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(verification()));
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                APPLICATION_ID, SalaryAdvanceLimitMovementType.RESERVED
        )).thenReturn(List.of(reservation));
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                APPLICATION_ID, SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
        )).thenReturn(List.of());
        when(reservationReleases.releaseReservationOnce(any(), any(), any()))
                .thenReturn(Optional.of(release));
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(corrections.saveRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cancellations.saveIfAbsent(any())).thenReturn(true);
    }

    private static AuthenticatedUser customerActor() {
        return new AuthenticatedUser(
                USER_ID,
                "customer@meridian.test",
                "CUSTOMER",
                CUSTOMER_ID,
                Set.of("CUSTOMER"),
                Set.of("loan:cancel:own")
        );
    }

    private static LoanApplication application(
            UUID customerId,
            LoanApplicationStatus status
    ) {
        return new LoanApplication(
                APPLICATION_ID,
                customerId,
                UUID.randomUUID(),
                "SA-20260810-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                REQUESTED_AMOUNT,
                1,
                NOW.minusDays(2)
        );
    }

    private static LoanApplication uclApplication(
            UUID customerId,
            LoanApplicationStatus status
    ) {
        return new LoanApplication(
                APPLICATION_ID,
                customerId,
                UUID.randomUUID(),
                "UCL-20260810-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                new BigDecimal("5000000.00"),
                6,
                NOW.minusDays(2)
        );
    }

    private static LoanCorrectionRequest correction(
            LoanCorrectionRequestStatus status,
            LocalDateTime cancelledAt
    ) {
        return new LoanCorrectionRequest(
                CORRECTION_ID,
                APPLICATION_ID,
                UUID.randomUUID(),
                "RETURN_TO_CUSTOMER_REVISION",
                CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED,
                UUID.randomUUID(),
                status,
                null,
                NOW.minusDays(1),
                null,
                null,
                cancelledAt
        );
    }

    private static LoanCorrectionRequest uclCorrection(
            LoanCorrectionRequestStatus status,
            LocalDateTime cancelledAt
    ) {
        return new LoanCorrectionRequest(
                CORRECTION_ID,
                APPLICATION_ID,
                null,
                "COMPLETE_PRODUCT_VERIFICATION",
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                UUID.randomUUID(),
                status,
                null,
                NOW.minusDays(1),
                null,
                null,
                cancelledAt
        );
    }

    private static SalaryAdvanceVerification verification() {
        return new SalaryAdvanceVerification(
                UUID.randomUUID(),
                APPLICATION_ID,
                CUSTOMER_ID,
                LINK_ID,
                LIMIT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                ProductVerificationResult.VERIFIED,
                new BigDecimal("10000000.00"),
                BigDecimal.ZERO.setScale(2),
                REQUESTED_AMOUNT,
                new BigDecimal("7000000.00"),
                NOW.minusDays(2)
        );
    }

    private static SalaryAdvanceLimitMovement reservation() {
        return SalaryAdvanceLimitMovement.reserved(
                RESERVATION_ID,
                LIMIT_ID,
                APPLICATION_ID,
                REQUESTED_AMOUNT,
                NOW.minusDays(2)
        );
    }

    private static SalaryAdvanceLimitMovement release() {
        return SalaryAdvanceLimitMovement.reservationReleased(
                RELEASE_ID,
                LIMIT_ID,
                APPLICATION_ID,
                REQUESTED_AMOUNT,
                NOW
        );
    }
}
