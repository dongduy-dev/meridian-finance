package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.*;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.*;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LoanContractReadinessService implements PrepareLoanContractUseCase,
        AcknowledgeLoanContractUseCase, QueryCurrentLoanContractUseCase,
        QueryContractReadinessUseCase, ConfirmContractReadinessUseCase {

    private final LoanApplicationRepository applications;
    private final ApprovedOfferRepository offers;
    private final LoanContractRepository contracts;
    private final LoanCorrectionRepository corrections;
    private final LoanDocumentChecklistPort documents;
    private final ContractBankAccountPort bankAccounts;
    private final DisbursementBankAccountProtector accountProtector;
    private final SalaryAdvanceVerificationRepository verifications;
    private final UnsecuredConsumerLoanVerificationRepository uclVerifications;
    private final SalaryAdvanceLimitRepository limits;
    private final SalaryAdvanceLimitMovementRepository movements;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public LoanContractReadinessService(
            LoanApplicationRepository applications, ApprovedOfferRepository offers,
            LoanContractRepository contracts, LoanCorrectionRepository corrections,
            LoanDocumentChecklistPort documents, ContractBankAccountPort bankAccounts,
            DisbursementBankAccountProtector accountProtector,
            SalaryAdvanceVerificationRepository verifications,
            UnsecuredConsumerLoanVerificationRepository uclVerifications,
            SalaryAdvanceLimitRepository limits,
            SalaryAdvanceLimitMovementRepository movements,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher, CurrentUserProvider currentUserProvider, Clock clock
    ) {
        this.applications = applications; this.offers = offers; this.contracts = contracts;
        this.corrections = corrections; this.documents = documents; this.bankAccounts = bankAccounts;
        this.accountProtector = accountProtector; this.verifications = verifications; this.limits = limits;
        this.uclVerifications = uclVerifications;
        this.movements = movements; this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher; this.currentUserProvider = currentUserProvider; this.clock = clock;
    }

    @Override
    @Transactional
    public LoanContract prepare(PrepareLoanContractUseCase.Command command) {
        requirePrepareCommand(command);
        AuthenticatedUser actor = currentUserProvider.currentUser();
        contracts.acquirePreparationRequestLock(command.requestId());
        LoanContract replay = contracts.findByPreparationRequestId(command.requestId()).orElse(null);
        if (replay != null) return validatePreparationReplay(replay, command, actor.userId());

        applications.acquireWorkflowLock(command.loanApplicationId());
        replay = contracts.findByPreparationRequestId(command.requestId()).orElse(null);
        if (replay != null) return validatePreparationReplay(replay, command, actor.userId());

        LoanApplication application = lockApplication(command.loanApplicationId());
        requireAccounting(actor);
        requireContractPending(application);
        requireExecutableContractPreparation(application);
        ApprovedOffer offer = lockAcceptedOffer(application.id());
        LoanContract current = contracts.findCurrentByApplicationIdForUpdate(application.id()).orElse(null);
        validatePreparationVersion(command, current);
        if (corrections.findActiveRequestByApplicationIdForUpdate(application.id()).isPresent()) {
            throw conflict("ACTIVE_CORRECTION_REQUEST", "An active correction request blocks contract preparation.");
        }
        if (!documents.isProcessingReady(application.id())) {
            throw conflict("DOCUMENTS_NOT_PROCESSING_READY", "Documents are not processing-ready.");
        }
        requirePreCaptureProductEvidence(application);

        LocalDateTime now = LocalDateTime.now(clock);
        UUID contractId = UUID.randomUUID();
        ProtectedDisbursementBankAccount protectedAccount;
        try (SensitiveDisbursementBankAccountDetails account = bankAccounts.capturePrimaryActive(application.customerId())) {
            byte[] plaintext = account.copyAccountNumber();
            try {
                DisbursementBankAccountProtectionContext protectionContext = new DisbursementBankAccountProtectionContext(
                        contractId, application.id(), application.customerId(), account.bankAccountId());
                ProtectedBankAccountEnvelope envelope = accountProtector.protect(plaintext, protectionContext);
                protectedAccount = new ProtectedDisbursementBankAccount(
                        application.customerId(), account.bankAccountId(), account.bankCode(), account.bankNameSnapshot(),
                        account.accountHolderName(), account.lastFour(), true, true, now, envelope.protectionScheme(),
                        envelope.keyId(), envelope.nonce(), envelope.ciphertext(), envelope.aadVersion());
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }

        int version = current == null ? 1 : current.contractVersion() + 1;
        List<LoanContractRepaymentItem> repaymentItems = offer.repaymentItems().stream()
                .map(item -> new LoanContractRepaymentItem(UUID.randomUUID(), item.id(), item.installmentNumber(),
                        item.principalDue(), item.interestDue(), item.feeDue(), item.totalDue()))
                .toList();
        LoanContract prepared = LoanContract.prepared(contractId, application.id(), offer.id(),
                "MCT-" + contractId.toString().toUpperCase(Locale.ROOT), version, offer.financialTerms(),
                repaymentItems, protectedAccount, command.requestId(), current == null ? null : current.contractVersion(),
                command.supersessionReason(), actor.userId(), now, current == null ? null : current.id());
        requireValidPreparationProductEvidence(
                application,
                prepared.financialTerms().approvedPrincipal()
        );

        BusinessOperationContext operation = BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now);
        if (current != null) {
            LoanContract superseded = contracts.saveAndFlush(current.supersede(actor.userId(), now));
            publishContractAudit(operation, BusinessAuditAction.LOAN_CONTRACT_SUPERSEDED, superseded);
        }
        LoanContract saved = contracts.save(prepared);
        publishContractAudit(operation, BusinessAuditAction.LOAN_CONTRACT_PREPARED, saved);
        return saved;
    }

    @Override
    @Transactional
    public LoanContract acknowledge(AcknowledgeLoanContractUseCase.Command command) {
        requireAcknowledgmentCommand(command);
        AuthenticatedUser actor = currentUserProvider.currentUser();
        UUID customerId = actor.requireCustomerId();
        contracts.acquireAcknowledgmentRequestLock(command.requestId());
        LoanContract replay = contracts.findByAcknowledgmentRequestId(command.requestId()).orElse(null);
        if (replay != null) return validateAcknowledgmentReplay(replay, command, actor.userId());

        applications.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication application = lockApplication(command.loanApplicationId());
        if (!application.customerId().equals(customerId)) {
            throw new com.meridian.platform.shared.domain.exception.AuthorizationException(
                    "LOAN_APPLICATION_ACCESS_DENIED", "Loan application does not belong to the authenticated customer.");
        }
        LoanContract current = requireCurrentContractForUpdate(application.id());
        requireContractVersion(current, command.expectedContractVersion());
        replay = contracts.findByAcknowledgmentRequestId(command.requestId()).orElse(null);
        if (replay != null) return validateAcknowledgmentReplay(replay, command, actor.userId());
        LocalDateTime now = LocalDateTime.now(clock);
        LoanContract acknowledged = contracts.save(current.acknowledge(command.requestId(), actor.userId(), now));
        publishContractAudit(BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                BusinessAuditAction.LOAN_CONTRACT_ACKNOWLEDGED, acknowledged);
        return acknowledged;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoanContract> findCurrent(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId);
        AuthenticatedUser actor = currentUserProvider.currentUser();
        LoanApplication application = applications.findById(loanApplicationId).orElseThrow(
                () -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                )
        );
        if (actor.optionalCustomerId().isPresent()) {
            if (!application.customerId().equals(actor.requireCustomerId())) {
                throw new com.meridian.platform.shared.domain.exception.AuthorizationException(
                        "LOAN_APPLICATION_ACCESS_DENIED",
                        "Loan application does not belong to the authenticated customer."
                );
            }
        } else {
            requireAccounting(actor);
        }
        if (application.status() != LoanApplicationStatus.CONTRACT_PENDING
                && application.status() != LoanApplicationStatus.DISBURSEMENT_PENDING) {
            throw conflict("INVALID_APPLICATION_STATE", "Loan application is not in a contract-readable state.");
        }
        return contracts.findCurrentByApplicationId(loanApplicationId);
    }

    @Override
    @Transactional(readOnly = true)
    public QueryContractReadinessUseCase.Snapshot query(UUID loanApplicationId, Integer expectedContractVersion) {
        Objects.requireNonNull(loanApplicationId);
        if (expectedContractVersion != null && expectedContractVersion <= 0) {
            throw new IllegalArgumentException("expectedContractVersion must be positive.");
        }
        requireAccounting(currentUserProvider.currentUser());
        LoanApplication application = applications.findById(loanApplicationId).orElse(null);
        ApprovedOffer offer = offers.findByLoanApplicationId(loanApplicationId).orElse(null);
        LoanContract contract = contracts.findCurrentByApplicationId(loanApplicationId).orElse(null);
        List<ContractReadinessBlockerCode> blockers = readinessBlockers(
                application, offer, contract, expectedContractVersion, false);
        return new QueryContractReadinessUseCase.Snapshot(loanApplicationId,
                contract == null ? null : contract.id(), contract == null ? null : contract.contractVersion(),
                blockers.isEmpty(), blockers);
    }

    @Override
    @Transactional
    public LoanContract confirm(ConfirmContractReadinessUseCase.Command command) {
        requireConfirmationCommand(command);
        AuthenticatedUser actor = currentUserProvider.currentUser();
        contracts.acquireConfirmationRequestLock(command.requestId());
        LoanContract replay = contracts.findByConfirmationRequestId(command.requestId()).orElse(null);
        if (replay != null) return validateConfirmationReplay(replay, command, actor.userId());

        applications.acquireWorkflowLock(command.loanApplicationId());
        LoanApplication application = lockApplication(command.loanApplicationId());
        ApprovedOffer offer = offers.findByLoanApplicationIdForUpdate(application.id()).orElse(null);
        LoanContract contract = contracts.findCurrentByApplicationIdForUpdate(application.id()).orElse(null);
        requireAccounting(actor);
        replay = contracts.findByConfirmationRequestId(command.requestId()).orElse(null);
        if (replay != null) return validateConfirmationReplay(replay, command, actor.userId());
        if (contract != null) requireContractIdentity(contract, command.contractId(), command.expectedContractVersion());
        List<ContractReadinessBlockerCode> blockers = readinessBlockers(
                application, offer, contract, command.expectedContractVersion(), true);
        if (!blockers.isEmpty()) {
            ContractReadinessBlockerCode blocker = blockers.getFirst();
            throw conflict(blocker.name(), "Contract readiness confirmation is blocked.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LoanContract ready = contracts.save(contract.confirmReady(command.requestId(), actor.userId(), now));
        LoanApplicationTransitionResult transition = application.confirmDisbursementReadiness();
        applications.save(transition.loanApplication());
        BusinessOperationContext operation = BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now);
        transitionRecorder.record(operation, transition.facts(), null);
        publishContractAudit(operation, BusinessAuditAction.LOAN_CONTRACT_READINESS_CONFIRMED, ready);
        return ready;
    }

    private List<ContractReadinessBlockerCode> readinessBlockers(
            LoanApplication application, ApprovedOffer offer, LoanContract contract,
            Integer expectedVersion, boolean lockReservation
    ) {
        LinkedHashSet<ContractReadinessBlockerCode> blockers = new LinkedHashSet<>();
        if (application == null || application.status() != LoanApplicationStatus.CONTRACT_PENDING) {
            if (application != null && application.status() == LoanApplicationStatus.DISBURSEMENT_PENDING) {
                blockers.add(contract != null && contract.status() == LoanContractStatus.READY_FOR_DISBURSEMENT
                        ? ContractReadinessBlockerCode.READINESS_ALREADY_CONFIRMED
                        : ContractReadinessBlockerCode.CONFLICTING_COMPLETED_TRANSITION);
            } else {
                blockers.add(ContractReadinessBlockerCode.INVALID_APPLICATION_STATE);
            }
        }
        if (offer == null) blockers.add(ContractReadinessBlockerCode.ACCEPTED_OFFER_MISSING);
        else if (offer.status() != ApprovedOfferStatus.ACCEPTED) blockers.add(ContractReadinessBlockerCode.ACCEPTED_OFFER_NOT_ACCEPTED);
        if (contract == null) blockers.add(ContractReadinessBlockerCode.CURRENT_CONTRACT_MISSING);
        else {
            if (expectedVersion != null && contract.contractVersion() != expectedVersion)
                blockers.add(ContractReadinessBlockerCode.CONTRACT_VERSION_STALE);
            if (contract.status() != LoanContractStatus.ACKNOWLEDGED) {
                if (contract.status() == LoanContractStatus.READY_FOR_DISBURSEMENT) {
                    blockers.add(application != null && application.status() == LoanApplicationStatus.DISBURSEMENT_PENDING
                            ? ContractReadinessBlockerCode.READINESS_ALREADY_CONFIRMED
                            : ContractReadinessBlockerCode.CONFLICTING_COMPLETED_TRANSITION);
                } else blockers.add(ContractReadinessBlockerCode.ACKNOWLEDGMENT_MISSING);
            }
        }
        if (application != null && contract != null) {
            if (!documents.isProcessingReady(application.id()))
                blockers.add(ContractReadinessBlockerCode.DOCUMENTS_NOT_PROCESSING_READY);
            boolean activeCorrection = lockReservation
                    ? corrections.findActiveRequestByApplicationIdForUpdate(application.id()).isPresent()
                    : corrections.existsActiveRequestByApplicationId(application.id());
            if (activeCorrection)
                blockers.add(ContractReadinessBlockerCode.ACTIVE_CORRECTION_REQUEST);
            ContractBankAccountPort.ContractBankAccountState account = lockReservation
                    ? bankAccounts.inspectCapturedForUpdate(application.customerId(),
                            contract.disbursementBankAccount().sourceBankAccountId())
                    : bankAccounts.inspectCaptured(application.customerId(),
                            contract.disbursementBankAccount().sourceBankAccountId());
            if (!account.customerActive()) blockers.add(ContractReadinessBlockerCode.CUSTOMER_INACTIVE);
            if (!account.accountExists()) blockers.add(ContractReadinessBlockerCode.CAPTURED_ACCOUNT_MISSING);
            else if (!account.accountActive()) blockers.add(ContractReadinessBlockerCode.CAPTURED_ACCOUNT_INACTIVE);
            ContractReadinessBlockerCode productEvidence = productReadinessBlocker(
                    application,
                    contract.financialTerms().approvedPrincipal(),
                    lockReservation
            );
            if (productEvidence != null) blockers.add(productEvidence);
        }
        return List.copyOf(blockers);
    }

    private void requireValidPreparationProductEvidence(
            LoanApplication application,
            BigDecimal approvedPrincipal
    ) {
        if (application.productCode() != ProductCode.SALARY_ADVANCE) {
            return;
        }
        ContractReadinessBlockerCode blocker = productReadinessBlocker(
                application,
                approvedPrincipal,
                true
        );
        if (blocker != null) {
            throw conflict(blocker.name(), "Product evidence is not valid for contract preparation.");
        }
    }

    private void requirePreCaptureProductEvidence(LoanApplication application) {
        if (application.productCode() == ProductCode.UNSECURED_CONSUMER_LOAN
                && uclVerificationBlocker(application) != null) {
            throw conflict(
                    ContractReadinessBlockerCode.UCL_VERIFICATION_INVALID.name(),
                    "Unsecured Consumer Loan verification is not valid for contract preparation."
            );
        }
    }

    private ContractReadinessBlockerCode productReadinessBlocker(
            LoanApplication application,
            BigDecimal approvedPrincipal,
            boolean lock
    ) {
        return switch (application.productCode()) {
            case SALARY_ADVANCE -> reservationBlocker(application, approvedPrincipal, lock);
            case UNSECURED_CONSUMER_LOAN -> uclVerificationBlocker(application);
            case COLLATERAL_LOAN ->
                    ContractReadinessBlockerCode.PRODUCT_CONTRACT_EXECUTION_UNSUPPORTED;
        };
    }

    private ContractReadinessBlockerCode uclVerificationBlocker(LoanApplication application) {
        UnsecuredConsumerLoanVerification verification = uclVerifications
                .findLatestByLoanApplicationId(application.id())
                .orElse(null);
        if (verification == null
                || !verification.loanApplicationId().equals(application.id())
                || verification.productVerificationResult() != ProductVerificationResult.VERIFIED) {
            return ContractReadinessBlockerCode.UCL_VERIFICATION_INVALID;
        }
        return null;
    }

    private ContractReadinessBlockerCode reservationBlocker(
            LoanApplication application, BigDecimal approvedPrincipal, boolean lock
    ) {
        SalaryAdvanceVerification verification = verifications.findByLoanApplicationId(application.id()).orElse(null);
        if (verification == null || !verification.customerId().equals(application.customerId()))
            return ContractReadinessBlockerCode.SALARY_ADVANCE_RESERVATION_INVALID;
        if (lock) limits.acquireCustomerLinkLock(verification.customerId(), verification.customerPartnerEmployeeLinkId());
        SalaryAdvanceLimit limit = (lock ? limits.findByIdForUpdate(verification.salaryAdvanceLimitId())
                : limits.findById(verification.salaryAdvanceLimitId())).orElse(null);
        if (movements.existsByLoanApplicationIdAndMovementType(application.id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED))
            return ContractReadinessBlockerCode.SALARY_ADVANCE_RESERVATION_RELEASED;
        if (limit == null || limit.status() != SalaryAdvanceLimitStatus.ACTIVE
                || !limit.customerId().equals(application.customerId())
                || !limit.customerPartnerEmployeeLinkId().equals(verification.customerPartnerEmployeeLinkId()))
            return ContractReadinessBlockerCode.SALARY_ADVANCE_RESERVATION_INVALID;

        List<SalaryAdvanceLimitMovement> reservations = lock
                ? movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                        application.id(), SalaryAdvanceLimitMovementType.RESERVED)
                : movements.findByLoanApplicationIdAndMovementType(
                        application.id(), SalaryAdvanceLimitMovementType.RESERVED);
        BigDecimal outstandingReservedAmount = movements.calculateOutstandingReservedAmount(limit.id());
        if (reservations.size() != 1
                || !application.id().equals(reservations.getFirst().loanApplicationId())
                || !limit.id().equals(reservations.getFirst().salaryAdvanceLimitId())
                || reservations.getFirst().amount().compareTo(approvedPrincipal) != 0
                || outstandingReservedAmount == null
                || limit.reservedAmount().compareTo(outstandingReservedAmount) != 0)
            return ContractReadinessBlockerCode.SALARY_ADVANCE_RESERVATION_INVALID;
        return null;
    }

    private LoanApplication lockApplication(UUID applicationId) {
        return applications.findByIdForUpdate(applicationId).orElseThrow(() -> new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
    }
    private ApprovedOffer lockAcceptedOffer(UUID applicationId) {
        ApprovedOffer offer = offers.findByLoanApplicationIdForUpdate(applicationId).orElseThrow(() -> new EntityNotFoundException(
                "APPROVED_OFFER_NOT_FOUND", "Approved offer was not found."));
        if (offer.status() != ApprovedOfferStatus.ACCEPTED) throw new BusinessRuleViolationException(
                "OFFER_NOT_ACCEPTED", "Approved offer has not been accepted.");
        return offer;
    }
    private LoanContract requireCurrentContractForUpdate(UUID applicationId) {
        return contracts.findCurrentByApplicationIdForUpdate(applicationId).orElseThrow(() -> new EntityNotFoundException(
                "CURRENT_CONTRACT_MISSING", "Current loan contract was not found."));
    }
    private static void requireContractPending(LoanApplication application) {
        if (application.status() != LoanApplicationStatus.CONTRACT_PENDING)
            throw conflict("INVALID_APPLICATION_STATE", "Loan application is not contract-pending.");
    }
    private void requireExecutableContractPreparation(LoanApplication application) {
        if (application.productCode() == ProductCode.COLLATERAL_LOAN) {
            throw conflict(
                    ContractReadinessBlockerCode.PRODUCT_CONTRACT_EXECUTION_UNSUPPORTED.name(),
                    "Loan product contract execution is not supported."
            );
        }
    }
    private static void validatePreparationVersion(PrepareLoanContractUseCase.Command command, LoanContract current) {
        int actual = current == null ? 0 : current.contractVersion();
        if (command.expectedCurrentVersion() != actual) throw stale();
        if (current == null && command.supersessionReason() != null)
            throw conflict("CONTRACT_SUPERSESSION_REASON_NOT_ALLOWED", "First preparation cannot have a supersession reason.");
        if (current != null && command.supersessionReason() != ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH)
            throw conflict("CONTRACT_SUPERSESSION_REASON_REQUIRED", "Regeneration requires the controlled account-refresh reason.");
        if (current != null && current.status() == LoanContractStatus.READY_FOR_DISBURSEMENT)
            throw conflict("CONTRACT_REGENERATION_NOT_ALLOWED", "A ready contract cannot be regenerated.");
    }
    private static void requireContractIdentity(LoanContract contract, UUID contractId, int version) {
        if (!contract.id().equals(contractId) || contract.contractVersion() != version) throw stale();
    }

    private static LoanContract validatePreparationReplay(LoanContract replay, PrepareLoanContractUseCase.Command command, UUID actor) {
        if (replay.loanApplicationId().equals(command.loanApplicationId())
                && Objects.equals(replay.expectedPreviousVersion(), command.expectedCurrentVersion() == 0 ? null : command.expectedCurrentVersion())
                && replay.supersessionReason() == command.supersessionReason()
                && replay.preparedByUserId().equals(actor)) return replay;
        throw idempotencyReused();
    }
    private static void requireContractVersion(LoanContract contract, int version) {
        if (contract.contractVersion() != version) throw stale();
    }
    private static LoanContract validateAcknowledgmentReplay(LoanContract replay, AcknowledgeLoanContractUseCase.Command command, UUID actor) {
        if (replay.loanApplicationId().equals(command.loanApplicationId())
                && replay.contractVersion() == command.expectedContractVersion()
                && actor.equals(replay.acknowledgedByUserId())) return replay;
        throw idempotencyReused();
    }
    private static LoanContract validateConfirmationReplay(LoanContract replay, ConfirmContractReadinessUseCase.Command command, UUID actor) {
        if (replay.loanApplicationId().equals(command.loanApplicationId())
                && replay.id().equals(command.contractId())
                && replay.contractVersion() == command.expectedContractVersion()
                && actor.equals(replay.confirmedByUserId())) return replay;
        throw idempotencyReused();
    }

    private void publishContractAudit(BusinessOperationContext operation, BusinessAuditAction action, LoanContract contract) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, contract.loanApplicationId())
                .put(BusinessAuditPayloadKey.LOAN_CONTRACT_ID, contract.id())
                .put(BusinessAuditPayloadKey.LOAN_CONTRACT_STATUS, contract.status());
        if (contract.supersessionReason() != null)
            payload.put(BusinessAuditPayloadKey.CONTRACT_SUPERSESSION_REASON, contract.supersessionReason());
        auditPublisher.publish(BusinessAuditEvent.single(operation, new BusinessAuditEntry(
                action, BusinessAuditEntityType.LOAN_CONTRACT, contract.id(), payload.build())));
    }

    private static void requirePrepareCommand(PrepareLoanContractUseCase.Command command) {
        Objects.requireNonNull(command); Objects.requireNonNull(command.requestId()); Objects.requireNonNull(command.loanApplicationId());
        if (command.expectedCurrentVersion() < 0) throw new IllegalArgumentException("expectedCurrentVersion must not be negative.");
    }
    private static void requireAcknowledgmentCommand(AcknowledgeLoanContractUseCase.Command command) {
        Objects.requireNonNull(command); Objects.requireNonNull(command.requestId()); Objects.requireNonNull(command.loanApplicationId());
        if (command.expectedContractVersion() <= 0) throw new IllegalArgumentException("expectedContractVersion must be positive.");
    }
    private static void requireConfirmationCommand(ConfirmContractReadinessUseCase.Command command) {
        Objects.requireNonNull(command); Objects.requireNonNull(command.requestId()); Objects.requireNonNull(command.loanApplicationId());
        Objects.requireNonNull(command.contractId()); if (command.expectedContractVersion() <= 0) throw new IllegalArgumentException("expectedContractVersion must be positive.");
    }
    private static BusinessStateConflictException stale() { return conflict("CONTRACT_VERSION_STALE", "Expected contract version is stale."); }
    private static BusinessStateConflictException idempotencyReused() { return conflict("IDEMPOTENCY_KEY_REUSED", "Command request ID was already used for a different logical command."); }
    private static BusinessStateConflictException conflict(String code, String message) { return new BusinessStateConflictException(code, message); }
    private static void requireAccounting(AuthenticatedUser user) {
        if (!user.roles().contains("ACCOUNTING_OFFICER")) {
            throw new com.meridian.platform.shared.domain.exception.AuthorizationException(
                    "ACCOUNTING_ROLE_REQUIRED", "Accounting authority is required for contract preparation and readiness confirmation."
            );
        }
    }
}
