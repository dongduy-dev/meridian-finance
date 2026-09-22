package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.service.salaryadvance.SalaryAdvanceReservationReleaseService;

import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.in.RecordAssistedUclCancellationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationCancellationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort;
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
import com.meridian.platform.loan.domain.model.OriginationChannel;
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
public class CancelLoanApplicationService
        implements CancelLoanApplicationUseCase, RecordAssistedUclCancellationUseCase {

    private final LoanApplicationCancellationRepository cancellations;
    private final LoanApplicationRepository applications;
    private final LoanCorrectionRepository corrections;
    private final LoanAssistedActionEvidencePort assistedEvidence;
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
            LoanAssistedActionEvidencePort assistedEvidence,
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
        this.assistedEvidence = assistedEvidence;
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
    public CancelLoanApplicationUseCase.Result cancel(CancelLoanApplicationUseCase.Command command) {
        AuthenticatedUser actor = requireCustomer(currentUsers.currentUser());
        CancellationOutcome outcome = execute(
                command.requestId(), command.loanApplicationId(), actor,
                actor.requireCustomerId(), null, null, false);
        return new CancelLoanApplicationUseCase.Result(
                outcome.loanApplicationId(), outcome.resultingStatus(),
                outcome.cancelledAt(), outcome.idempotentReplay());
    }

    @Override
    @Transactional
    public RecordAssistedUclCancellationUseCase.Result record(
            RecordAssistedUclCancellationUseCase.Command command
    ) {
        AuthenticatedUser actor = requireAssistedCancellationStaff(currentUsers.currentUser());
        CancellationOutcome outcome = execute(
                command.requestId(), command.loanApplicationId(), actor, null,
                command.expectedCorrectionRequestId(), command.evidenceDocumentVersionId(), true);
        return new RecordAssistedUclCancellationUseCase.Result(
                outcome.loanApplicationId(), outcome.resultingStatus(),
                outcome.cancelledAt(), outcome.idempotentReplay());
    }

    private CancellationOutcome execute(
            UUID requestId,
            UUID loanApplicationId,
            AuthenticatedUser actor,
            UUID customerOwnerId,
            UUID expectedCorrectionRequestId,
            UUID evidenceDocumentVersionId,
            boolean assisted
    ) {

        cancellations.acquireCancellationRequestLock(requestId);
        applications.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = applications.findByIdForUpdate(loanApplicationId)
                .orElseThrow(CancelLoanApplicationService::notFound);
        if (assisted) {
            requireAssistedUclApplication(application);
        } else {
            if (!application.customerId().equals(customerOwnerId)) {
                throw notFound();
            }
            CustomerDigitalApplicationAccess.require(application);
        }

        LoanApplicationCancellation existing = cancellations
                .findByRequestId(requestId)
                .orElse(null);
        if (existing != null) {
            validateIdentity(
                    existing, requestId, loanApplicationId, expectedCorrectionRequestId,
                    evidenceDocumentVersionId, actor, assisted);
            return replay(existing, application, assisted);
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
        if (assisted && !correction.id().equals(expectedCorrectionRequestId)) {
            throw new BusinessStateConflictException(
                    "CORRECTION_REQUEST_CONFLICT",
                    "The cancellation command does not target the active correction request."
            );
        }
        if (assisted) {
            LoanAssistedActionEvidencePort.EvidenceSnapshot evidence = assistedEvidence
                    .requireCurrentCancellationEvidence(
                            application.id(), correction.id(), evidenceDocumentVersionId);
            if (!"CUSTOMER_CANCELLATION_REQUEST".equals(evidence.evidenceType())
                    || !correction.id().equals(evidence.correctionRequestId())) {
                throw stateConflict();
            }
        }

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
        publishCancellationAudit(
                operation, application, cancelledCorrection,
                assisted ? evidenceDocumentVersionId : null);

        LoanApplicationCancellation cancellation = application.productCode()
                == ProductCode.SALARY_ADVANCE
                ? LoanApplicationCancellation.recorded(
                        cancellationId,
                        cancelledApplication,
                        cancelledCorrection,
                        releaseMovement,
                        requestId,
                        actor.userId(),
                        cancelledAt
                )
                : assisted
                ? LoanApplicationCancellation.recordedAssistedWithoutExposureEffect(
                        cancellationId,
                        cancelledApplication,
                        cancelledCorrection,
                        requestId,
                        actor.userId(),
                        evidenceDocumentVersionId,
                        cancelledAt
                )
                : LoanApplicationCancellation.recordedWithoutExposureEffect(
                        cancellationId,
                        cancelledApplication,
                        cancelledCorrection,
                        requestId,
                        actor.userId(),
                        cancelledAt
                );
        if (!cancellations.saveIfAbsent(cancellation)) {
            throw stateConflict();
        }
        return outcome(cancellation, false);
    }

    private CancellationOutcome replay(
            LoanApplicationCancellation cancellation,
            LoanApplication application,
            boolean assisted
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
        if (assisted) {
            LoanAssistedActionEvidencePort.EvidenceSnapshot evidence = assistedEvidence
                    .requireCurrentCancellationEvidence(
                            application.id(), cancellation.correctionRequestId(),
                            cancellation.assistedEvidenceDocumentVersionId());
            productEvidenceValid = productEvidenceValid
                    && "CUSTOMER_CANCELLATION_REQUEST".equals(evidence.evidenceType())
                    && cancellation.correctionRequestId().equals(evidence.correctionRequestId());
        }
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
        return outcome(cancellation, true);
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
            LoanCorrectionRequest correction,
            UUID assistedEvidenceDocumentVersionId
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.CUSTOMER_ID, application.customerId())
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, application.id())
                .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, correction.id())
                .put(
                        BusinessAuditPayloadKey.PREVIOUS_APPLICATION_STATUS,
                        LoanApplicationStatus.RETURNED_FOR_REVISION
                )
                .put(
                        BusinessAuditPayloadKey.FINAL_APPLICATION_STATUS,
                        LoanApplicationStatus.CANCELLED
                );
        if (assistedEvidenceDocumentVersionId != null) {
            payload.put(
                    BusinessAuditPayloadKey.DOCUMENT_VERSION_ID,
                    assistedEvidenceDocumentVersionId
            );
        }
        auditPublisher.publish(BusinessAuditEvent.single(
                operation,
                new BusinessAuditEntry(
                        BusinessAuditAction.LOAN_APPLICATION_CANCELLED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        application.id(),
                        payload.build()
                )
        ));
    }

    private static void validateIdentity(
            LoanApplicationCancellation cancellation,
            UUID requestId,
            UUID loanApplicationId,
            UUID expectedCorrectionRequestId,
            UUID evidenceDocumentVersionId,
            AuthenticatedUser actor,
            boolean assisted
    ) {
        boolean matchingAssistedIdentity = assisted
                ? cancellation.correctionRequestId().equals(expectedCorrectionRequestId)
                && cancellation.assistedEvidenceDocumentVersionId() != null
                && cancellation.assistedEvidenceDocumentVersionId().equals(evidenceDocumentVersionId)
                : cancellation.assistedEvidenceDocumentVersionId() == null;
        if (!cancellation.requestId().equals(requestId)
                || !cancellation.loanApplicationId().equals(loanApplicationId)
                || !cancellation.cancelledByUserId().equals(actor.userId())
                || !matchingAssistedIdentity) {
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

    private static AuthenticatedUser requireAssistedCancellationStaff(AuthenticatedUser actor) {
        if (actor == null
                || !"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:cancel:staff")) {
            throw new AuthorizationException(
                    "ASSISTED_UCL_CANCELLATION_ACCESS_DENIED",
                    "Staff-assisted UCL cancellation authority is required."
            );
        }
        if (!actor.roles().contains("LOAN_OFFICER")) {
            throw new AuthorizationException(
                    "ASSISTED_ACTION_ROLE_REQUIRED",
                    "Loan Officer authority is required for assisted UCL cancellation."
            );
        }
        return actor;
    }

    private static void requireAssistedUclApplication(LoanApplication application) {
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN) {
            throw cancellationNotAllowed();
        }
    }

    private static CancellationOutcome outcome(
            LoanApplicationCancellation cancellation,
            boolean replay
    ) {
        return new CancellationOutcome(
                cancellation.loanApplicationId(),
                LoanApplicationStatus.CANCELLED,
                cancellation.cancelledAt(),
                replay
        );
    }

    private record CancellationOutcome(
            UUID loanApplicationId,
            LoanApplicationStatus resultingStatus,
            LocalDateTime cancelledAt,
            boolean idempotentReplay
    ) {
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
