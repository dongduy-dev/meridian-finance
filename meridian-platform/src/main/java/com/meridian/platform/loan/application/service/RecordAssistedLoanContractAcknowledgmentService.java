package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.RecordAssistedLoanContractAcknowledgmentUseCase;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.*;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecordAssistedLoanContractAcknowledgmentService
        implements RecordAssistedLoanContractAcknowledgmentUseCase {

    private final LoanApplicationRepository applications;
    private final LoanContractRepository contracts;
    private final StaffAssistedContractAcknowledgmentRepository acknowledgments;
    private final LoanAssistedActionEvidencePort evidence;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public RecordAssistedLoanContractAcknowledgmentService(
            LoanApplicationRepository applications,
            LoanContractRepository contracts,
            StaffAssistedContractAcknowledgmentRepository acknowledgments,
            LoanAssistedActionEvidencePort evidence,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.applications = applications;
        this.contracts = contracts;
        this.acknowledgments = acknowledgments;
        this.evidence = evidence;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LoanContract record(Command command) {
        requireCommand(command);
        AuthenticatedUser actor = requireActor();
        contracts.acquireAcknowledgmentRequestLock(command.acknowledgmentRequestId());
        StaffAssistedContractAcknowledgment replay = acknowledgments
                .findByAcknowledgmentRequestId(command.acknowledgmentRequestId()).orElse(null);
        if (replay != null) return replayResult(replay, command, actor.userId());
        if (contracts.findByAcknowledgmentRequestId(command.acknowledgmentRequestId()).isPresent()) {
            throw idempotencyReused();
        }

        applications.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication application = applications.findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
        requireEligible(application);
        LoanContract contract = contracts.findCurrentByApplicationIdForUpdate(application.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "CURRENT_CONTRACT_MISSING", "Current loan contract was not found."));
        if (!contract.id().equals(command.contractId())
                || contract.contractVersion() != command.expectedContractVersion()) {
            throw new BusinessStateConflictException(
                    "CONTRACT_VERSION_STALE", "Expected contract identity or version is stale.");
        }
        replay = acknowledgments.findByAcknowledgmentRequestId(command.acknowledgmentRequestId()).orElse(null);
        if (replay != null) return replayResult(replay, command, actor.userId());
        if (contracts.findByAcknowledgmentRequestId(command.acknowledgmentRequestId()).isPresent()) {
            throw idempotencyReused();
        }
        if (acknowledgments.findByLoanContractIdAndContractVersion(
                contract.id(), contract.contractVersion()).isPresent()) {
            throw new BusinessStateConflictException(
                    "CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED", "This contract version is already acknowledged.");
        }
        if (contract.status() != LoanContractStatus.PREPARED) {
            throw new BusinessStateConflictException(
                    "CONTRACT_ACKNOWLEDGMENT_NOT_ALLOWED", "Only a prepared current contract may be acknowledged.");
        }

        evidence.requireCurrentContractEvidence(application.id(), contract.id(), contract.contractVersion(),
                command.evidenceDocumentVersionId());
        LocalDateTime now = LocalDateTime.now(clock);
        LoanContract acknowledged = contracts.save(contract.acknowledge(
                command.acknowledgmentRequestId(), actor.userId(), now));
        StaffAssistedContractAcknowledgment recorded = acknowledgments.save(
                new StaffAssistedContractAcknowledgment(
                        UUID.randomUUID(), command.acknowledgmentRequestId(), application.id(),
                        application.customerId(), contract.id(), contract.contractVersion(),
                        command.evidenceDocumentVersionId(), actor.userId(), now));
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, application.id())
                .put(BusinessAuditPayloadKey.CUSTOMER_ID, application.customerId())
                .put(BusinessAuditPayloadKey.LOAN_CONTRACT_ID, acknowledged.id())
                .put(BusinessAuditPayloadKey.LOAN_CONTRACT_STATUS, acknowledged.status())
                .put(BusinessAuditPayloadKey.DOCUMENT_VERSION_ID, recorded.evidenceDocumentVersionId())
                .build();
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(command.acknowledgmentRequestId(), actor.userId(), now),
                new BusinessAuditEntry(BusinessAuditAction.LOAN_CONTRACT_ACKNOWLEDGED,
                        BusinessAuditEntityType.LOAN_CONTRACT, acknowledged.id(), payload)));
        return acknowledged;
    }

    private LoanContract replayResult(
            StaffAssistedContractAcknowledgment replay,
            Command command,
            UUID actorId
    ) {
        if (!replay.loanApplicationId().equals(command.loanApplicationId())
                || !replay.loanContractId().equals(command.contractId())
                || replay.contractVersion() != command.expectedContractVersion()
                || !replay.evidenceDocumentVersionId().equals(command.evidenceDocumentVersionId())
                || !replay.recordedByStaffUserId().equals(actorId)) {
            throw idempotencyReused();
        }
        return contracts.findById(replay.loanContractId()).orElseThrow(() -> new EntityNotFoundException(
                "CURRENT_CONTRACT_MISSING", "Acknowledged loan contract was not found."));
    }

    private AuthenticatedUser requireActor() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:contract:acknowledge:staff")) {
            throw new AuthorizationException("ASSISTED_ACTION_ACCESS_DENIED",
                    "Staff-assisted contract acknowledgment access is denied.");
        }
        if (!actor.roles().contains("ACCOUNTING_OFFICER")) {
            throw new AuthorizationException("ASSISTED_ACTION_ROLE_REQUIRED",
                    "Accounting Officer authority is required to record assisted acknowledgment.");
        }
        return actor;
    }

    private static void requireEligible(LoanApplication application) {
        if (application.originationChannel() != OriginationChannel.STAFF_ASSISTED
                || (application.productCode() != ProductCode.UNSECURED_CONSUMER_LOAN
                && application.productCode() != ProductCode.COLLATERAL_LOAN)) {
            throw new BusinessStateConflictException("ASSISTED_ACTION_NOT_ALLOWED",
                    "Only Staff-assisted UCL or Collateral Loan permits this action.");
        }
        if (application.status() != LoanApplicationStatus.CONTRACT_PENDING) {
            throw new BusinessStateConflictException("INVALID_APPLICATION_STATE",
                    "Loan application is not contract-pending.");
        }
    }

    private static void requireCommand(Command command) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(command.acknowledgmentRequestId());
        Objects.requireNonNull(command.loanApplicationId());
        Objects.requireNonNull(command.contractId());
        Objects.requireNonNull(command.evidenceDocumentVersionId());
        if (command.expectedContractVersion() <= 0) {
            throw new IllegalArgumentException("expectedContractVersion must be positive");
        }
    }

    private static BusinessStateConflictException idempotencyReused() {
        return new BusinessStateConflictException(
                "IDEMPOTENCY_KEY_REUSED", "Command request ID was already used for different content.");
    }
}
