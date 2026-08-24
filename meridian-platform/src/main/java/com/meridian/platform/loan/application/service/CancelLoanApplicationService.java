package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.service.salaryadvance.SalaryAdvanceReservationReleaseService;

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
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.salaryadvance.ReservationReleaseTrigger;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditEvidenceReader;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class CancelLoanApplicationService implements CancelLoanApplicationUseCase {

    private final LoanApplicationCancellationRepository cancellations;
    private final LoanApplicationRepository applications;
    private final LoanCorrectionRepository corrections;
    private final SalaryAdvanceVerificationRepository verifications;
    private final SalaryAdvanceLimitRepository limits;
    private final SalaryAdvanceLimitMovementRepository movements;
    private final SalaryAdvanceReservationReleaseService reservationReleases;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final LoanApplicationStatusTransitionRepository transitionEvidence;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final BusinessAuditEvidenceReader auditEvidence;
    private final Clock clock;

    public CancelLoanApplicationService(
            LoanApplicationCancellationRepository cancellations,
            LoanApplicationRepository applications,
            LoanCorrectionRepository corrections,
            SalaryAdvanceVerificationRepository verifications,
            SalaryAdvanceLimitRepository limits,
            SalaryAdvanceLimitMovementRepository movements,
            SalaryAdvanceReservationReleaseService reservationReleases,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            LoanApplicationStatusTransitionRepository transitionEvidence,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            BusinessAuditEvidenceReader auditEvidence,
            Clock clock
    ) {
        this.cancellations = cancellations;
        this.applications = applications;
        this.corrections = corrections;
        this.verifications = verifications;
        this.limits = limits;
        this.movements = movements;
        this.reservationReleases = reservationReleases;
        this.transitionRecorder = transitionRecorder;
        this.transitionEvidence = transitionEvidence;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.auditEvidence = auditEvidence;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result cancel(Command command) {
        AuthenticatedUser actor = requireCustomer(currentUsers.currentUser());
        UUID customerId = actor.requireCustomerId();

        cancellations.acquireCancellationRequestLock(command.requestId());
        applications.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication application = applications.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(CancelLoanApplicationService::notFound);
        if (!application.customerId().equals(customerId)) {
            throw notFound();
        }

        LoanApplicationCancellation existing = cancellations
                .findByRequestId(command.requestId())
                .orElse(null);
        if (existing != null) {
            validateIdentity(existing, command, actor);
            return replay(existing, application);
        }
        if (cancellations.findByLoanApplicationId(application.id()).isPresent()) {
            throw cancellationNotAllowed();
        }
        if (application.productCode() == ProductCode.COLLATERAL_LOAN) {
            throw cancellationNotAllowed();
        }

        LoanApplicationTransitionResult transition = application.cancelReturnedForRevision();
        LoanCorrectionRequest correction = corrections
                .findActiveRequestByApplicationIdForUpdate(application.id())
                .orElseThrow(CancelLoanApplicationService::stateConflict);

        LocalDateTime cancelledAt = ServicingEvidenceTimestamp.normalizeForPersistence(
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
        UUID cancellationId = UUID.randomUUID();
        BusinessOperationContext operation = BusinessOperationContext.user(
                cancellationId,
                actor.userId(),
                cancelledAt
        );
        SalaryAdvanceLimitMovement releaseMovement = application.productCode()
                == ProductCode.SALARY_ADVANCE
                ? releaseSalaryAdvanceReservation(application, operation)
                : requireNoUclSalaryMovement(application);

        LoanApplication cancelledApplication = applications.save(transition.loanApplication());
        LoanCorrectionRequest cancelledCorrection = corrections.saveRequest(
                correction.cancel(cancelledAt)
        );
        transitionRecorder.record(operation, transition.facts(), "CUSTOMER_CANCELLATION");
        publishCancellationAudit(operation, application, cancelledCorrection);

        LoanApplicationCancellation cancellation = application.productCode()
                == ProductCode.SALARY_ADVANCE
                ? LoanApplicationCancellation.recorded(
                        cancellationId,
                        cancelledApplication,
                        cancelledCorrection,
                        releaseMovement,
                        command.requestId(),
                        actor.userId(),
                        cancelledAt
                )
                : LoanApplicationCancellation.recordedWithoutExposureEffect(
                        cancellationId,
                        cancelledApplication,
                        cancelledCorrection,
                        command.requestId(),
                        actor.userId(),
                        cancelledAt
                );
        if (!cancellations.saveIfAbsent(cancellation)) {
            throw stateConflict();
        }
        return result(cancellation, false);
    }

    private Result replay(
            LoanApplicationCancellation cancellation,
            LoanApplication application
    ) {
        if (application.status() != LoanApplicationStatus.CANCELLED) {
            throw stateConflict();
        }
        LoanCorrectionRequest correction = corrections
                .findRequestById(cancellation.correctionRequestId())
                .orElseThrow(CancelLoanApplicationService::stateConflict);
        boolean productEvidenceValid = application.productCode() == ProductCode.SALARY_ADVANCE
                ? hasValidSalaryAdvanceReplayEvidence(application, cancellation)
                : hasValidUclReplayEvidence(application, cancellation);
        if (correction.status() != LoanCorrectionRequestStatus.CANCELLED
                || !correction.loanApplicationId().equals(application.id())
                || !cancellation.cancelledAt().equals(correction.cancelledAt())
                || !productEvidenceValid
                || transitionEvidence.countMatching(
                        application.id(),
                        LoanApplicationStatus.RETURNED_FOR_REVISION,
                        LoanApplicationStatus.CANCELLED,
                        LoanApplicationTransitionAction.CANCEL_APPLICATION
                ) != 1
                || auditEvidence.countMatchingOperation(
                        cancellation.id(),
                        BusinessAuditAction.LOAN_APPLICATION_CANCELLED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        application.id()
                ) != 1
        ) {
            throw stateConflict();
        }
        return result(cancellation, true);
    }

    private SalaryAdvanceLimitMovement releaseSalaryAdvanceReservation(
            LoanApplication application,
            BusinessOperationContext operation
    ) {
        applications.acquireCustomerProductLock(application.customerId(), application.productCode());
        SalaryAdvanceVerification verification = verifications
                .findByLoanApplicationId(application.id())
                .orElseThrow(CancelLoanApplicationService::stateConflict);
        if (!verification.customerId().equals(application.customerId())) {
            throw stateConflict();
        }
        limits.acquireCustomerLinkLock(
                application.customerId(),
                verification.customerPartnerEmployeeLinkId()
        );
        validateReservationEvidence(application, verification);
        SalaryAdvanceLimitMovement releaseMovement = reservationReleases
                .releaseReservationOnce(
                        application,
                        operation,
                        ReservationReleaseTrigger.CUSTOMER_CANCELLATION
                )
                .orElseThrow(CancelLoanApplicationService::stateConflict);
        if (releaseMovement.amount().compareTo(application.requestedAmount()) != 0) {
            throw stateConflict();
        }
        return releaseMovement;
    }

    private SalaryAdvanceLimitMovement requireNoUclSalaryMovement(LoanApplication application) {
        if (!movements.findByLoanApplicationIdAndMovementType(
                application.id(),
                SalaryAdvanceLimitMovementType.RESERVED
        ).isEmpty() || !movements.findByLoanApplicationIdAndMovementType(
                application.id(),
                SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
        ).isEmpty()) {
            throw stateConflict();
        }
        return null;
    }

    private boolean hasValidSalaryAdvanceReplayEvidence(
            LoanApplication application,
            LoanApplicationCancellation cancellation
    ) {
        if (cancellation.reservationReleaseMovementId() == null) {
            return false;
        }
        List<SalaryAdvanceLimitMovement> releases = movements
                .findByLoanApplicationIdAndMovementType(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
                );
        return releases.size() == 1
                && releases.getFirst().id().equals(cancellation.reservationReleaseMovementId())
                && releases.getFirst().amount().compareTo(application.requestedAmount()) == 0
                && auditEvidence.countMatchingOperation(
                        cancellation.id(),
                        BusinessAuditAction.RESERVATION_RELEASED,
                        BusinessAuditEntityType.SALARY_ADVANCE_LIMIT_MOVEMENT,
                        cancellation.reservationReleaseMovementId()
                ) == 1;
    }

    private boolean hasValidUclReplayEvidence(
            LoanApplication application,
            LoanApplicationCancellation cancellation
    ) {
        return application.productCode() == ProductCode.UNSECURED_CONSUMER_LOAN
                && cancellation.reservationReleaseMovementId() == null
                && movements.findByLoanApplicationIdAndMovementType(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVED
                ).isEmpty()
                && movements.findByLoanApplicationIdAndMovementType(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
                ).isEmpty()
                && auditEvidence.countMatchingOperationAction(
                        cancellation.id(),
                        BusinessAuditAction.RESERVATION_RELEASED
                ) == 0;
    }

    private void validateReservationEvidence(
            LoanApplication application,
            SalaryAdvanceVerification verification
    ) {
        List<SalaryAdvanceLimitMovement> reservations = movements
                .findByLoanApplicationIdAndMovementTypeForUpdate(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVED
                );
        List<SalaryAdvanceLimitMovement> releases = movements
                .findByLoanApplicationIdAndMovementTypeForUpdate(
                        application.id(),
                        SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
                );
        if (reservations.size() != 1
                || !releases.isEmpty()
                || !reservations.getFirst().salaryAdvanceLimitId()
                .equals(verification.salaryAdvanceLimitId())
                || reservations.getFirst().amount().compareTo(application.requestedAmount()) != 0) {
            throw stateConflict();
        }
    }

    private void publishCancellationAudit(
            BusinessOperationContext operation,
            LoanApplication application,
            LoanCorrectionRequest correction
    ) {
        auditPublisher.publish(BusinessAuditEvent.single(
                operation,
                new BusinessAuditEntry(
                        BusinessAuditAction.LOAN_APPLICATION_CANCELLED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        application.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, application.id())
                                .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, correction.id())
                                .put(
                                        BusinessAuditPayloadKey.PREVIOUS_APPLICATION_STATUS,
                                        LoanApplicationStatus.RETURNED_FOR_REVISION
                                )
                                .put(
                                        BusinessAuditPayloadKey.FINAL_APPLICATION_STATUS,
                                        LoanApplicationStatus.CANCELLED
                                )
                                .build()
                )
        ));
    }

    private static void validateIdentity(
            LoanApplicationCancellation cancellation,
            Command command,
            AuthenticatedUser actor
    ) {
        if (!cancellation.requestId().equals(command.requestId())
                || !cancellation.loanApplicationId().equals(command.loanApplicationId())
                || !cancellation.cancelledByUserId().equals(actor.userId())) {
            throw new BusinessStateConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Cancellation request identifier was reused for a different operation."
            );
        }
    }

    private static AuthenticatedUser requireCustomer(AuthenticatedUser actor) {
        if (actor == null
                || !"CUSTOMER".equals(actor.userType())
                || !actor.hasPermission("loan:cancel:own")
                || actor.optionalCustomerId().isEmpty()) {
            throw new AuthorizationException(
                    "LOAN_APPLICATION_CANCELLATION_ACCESS_DENIED",
                    "Customer cancellation authority is required."
            );
        }
        return actor;
    }

    private static Result result(
            LoanApplicationCancellation cancellation,
            boolean replay
    ) {
        return new Result(
                cancellation.loanApplicationId(),
                LoanApplicationStatus.CANCELLED,
                cancellation.cancelledAt(),
                replay
        );
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND",
                "Loan Application was not found."
        );
    }

    private static BusinessStateConflictException cancellationNotAllowed() {
        return new BusinessStateConflictException(
                "LOAN_APPLICATION_CANCELLATION_NOT_ALLOWED",
                "Loan Application cancellation is not allowed in the current state."
        );
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan Application cancellation evidence is inconsistent."
        );
    }
}
