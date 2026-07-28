package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementSaveOutcome;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.service.FinalRepaymentScheduleGenerator;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ConfirmManualDisbursementService implements ConfirmManualDisbursementUseCase {

    private final LoanApplicationRepository applications;
    private final LoanContractRepository contracts;
    private final LoanAccountRepository loanAccounts;
    private final ManualDisbursementRepository manualDisbursements;
    private final RepaymentScheduleRepository repaymentSchedules;
    private final LoanProductActivationPolicyResolver activationPolicies;
    private final LoanApplicationStatusTransitionRecorder transitionRecorder;
    private final BusinessAuditPublisher auditPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final FinalRepaymentScheduleGenerator scheduleGenerator =
            new FinalRepaymentScheduleGenerator();

    public ConfirmManualDisbursementService(
            LoanApplicationRepository applications,
            LoanContractRepository contracts,
            LoanAccountRepository loanAccounts,
            ManualDisbursementRepository manualDisbursements,
            RepaymentScheduleRepository repaymentSchedules,
            LoanProductActivationPolicyResolver activationPolicies,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher auditPublisher,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.applications = applications;
        this.contracts = contracts;
        this.loanAccounts = loanAccounts;
        this.manualDisbursements = manualDisbursements;
        this.repaymentSchedules = repaymentSchedules;
        this.activationPolicies = activationPolicies;
        this.transitionRecorder = transitionRecorder;
        this.auditPublisher = auditPublisher;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result confirm(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        String canonicalReference = ManualDisbursement.canonicalReference(
                command.externalTransferReference()
        );
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireAccounting(actor);

        manualDisbursements.acquireConfirmationRequestLock(command.requestId());
        ManualDisbursement replay = manualDisbursements
                .findByRequestId(command.requestId())
                .orElse(null);
        if (replay != null) {
            return validateAndLoadReplay(replay, command, canonicalReference, actor.userId());
        }

        applications.acquireWorkflowLock(command.loanApplicationId());
        replay = manualDisbursements.findByRequestId(command.requestId()).orElse(null);
        if (replay != null) {
            return validateAndLoadReplay(replay, command, canonicalReference, actor.userId());
        }

        LoanApplication application = applications
                .findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        LoanContract contract = contracts
                .findCurrentByApplicationIdForUpdate(application.id())
                .orElseThrow(() -> conflict(
                        "CURRENT_CONTRACT_MISSING",
                        "Current Loan contract is missing."
                ));
        LoanAccount existingAccount = loanAccounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElse(null);
        ManualDisbursement existingDisbursement = manualDisbursements
                .findByLoanApplicationIdForUpdate(application.id())
                .orElse(null);
        RepaymentSchedule existingSchedule = repaymentSchedules
                .findByLoanApplicationId(application.id())
                .orElse(null);

        rejectExistingActivation(
                application,
                existingAccount,
                existingDisbursement,
                existingSchedule
        );
        validateLifecycle(application, contract, command.expectedContractVersion());
        validateDates(command, contract);

        LocalDateTime operationTime = LocalDateTime.now(clock);
        UUID loanAccountId = UUID.randomUUID();
        UUID disbursementId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        List<UUID> scheduleItemIds = contract.repaymentItems().stream()
                .map(ignored -> UUID.randomUUID())
                .toList();

        LoanAccount account = loanAccounts.save(LoanAccount.activate(
                loanAccountId,
                contract,
                operationTime
        ));
        ManualDisbursement attemptedDisbursement = ManualDisbursement.confirmed(
                disbursementId,
                contract,
                account,
                command.requestId(),
                command.expectedContractVersion(),
                canonicalReference,
                command.disbursementValueDate(),
                command.firstRepaymentDate(),
                actor.userId(),
                operationTime
        );
        ManualDisbursement disbursement = resolveSaveOutcome(
                manualDisbursements.save(attemptedDisbursement),
                attemptedDisbursement,
                command,
                canonicalReference,
                actor.userId()
        );
        if (!disbursement.id().equals(attemptedDisbursement.id())) {
            Result result = loadCompletedResult(disbursement, true);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return result;
        }

        RepaymentSchedule schedule = repaymentSchedules.save(scheduleGenerator.generate(
                scheduleId,
                scheduleItemIds,
                contract,
                account,
                command.disbursementValueDate(),
                command.firstRepaymentDate(),
                operationTime
        ));

        LoanProductActivationPolicy.ProductActivationResult activationResult =
                activationPolicies.resolve(application.productCode()).activate(
                        new LoanProductActivationPolicy.ProductActivationCommand(
                                application,
                                contract,
                                account,
                                movementId,
                                operationTime
                        )
                );
        validateActivationResult(application, contract, movementId, activationResult);

        LoanApplicationTransitionResult transition = application.confirmManualDisbursement();
        LoanApplication disbursedApplication = applications.save(transition.loanApplication());
        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(),
                actor.userId(),
                operationTime
        );
        transitionRecorder.record(operation, transition.facts(), null);
        publishAudit(operation, application, disbursedApplication, contract,
                account, disbursement, schedule);

        return toResult(disbursedApplication, account, disbursement, schedule, false);
    }

    private ManualDisbursement resolveSaveOutcome(
            ManualDisbursementSaveOutcome outcome,
            ManualDisbursement attempted,
            Command command,
            String canonicalReference,
            UUID actorUserId
    ) {
        if (outcome instanceof ManualDisbursementSaveOutcome.Inserted inserted) {
            if (!attempted.equals(inserted.manualDisbursement())) {
                throw systemStateConflict();
            }
            return inserted.manualDisbursement();
        }
        if (outcome instanceof ManualDisbursementSaveOutcome.ExistingRequest existing) {
            requireSameLogicalRequest(
                    existing.manualDisbursement(),
                    command,
                    canonicalReference,
                    actorUserId
            );
            return existing.manualDisbursement();
        }
        if (outcome instanceof ManualDisbursementSaveOutcome.Conflict conflict) {
            throw switch (conflict.kind()) {
                case EXTERNAL_TRANSFER_REFERENCE -> conflict(
                        "DUPLICATE_TRANSFER_REFERENCE",
                        "External transfer evidence is already recorded."
                );
                case LOAN_APPLICATION, LOAN_CONTRACT, LOAN_ACCOUNT -> conflict(
                        "DISBURSEMENT_ALREADY_COMPLETED",
                        "Manual disbursement was already completed."
                );
                case DISBURSEMENT_ID -> systemStateConflict();
            };
        }
        if (outcome instanceof ManualDisbursementSaveOutcome.UnresolvedConflict) {
            throw systemStateConflict();
        }
        throw systemStateConflict();
    }

    private Result validateAndLoadReplay(
            ManualDisbursement existing,
            Command command,
            String canonicalReference,
            UUID actorUserId
    ) {
        requireSameLogicalRequest(existing, command, canonicalReference, actorUserId);
        return loadCompletedResult(existing, true);
    }

    private void requireSameLogicalRequest(
            ManualDisbursement existing,
            Command command,
            String canonicalReference,
            UUID actorUserId
    ) {
        if (!existing.loanApplicationId().equals(command.loanApplicationId())
                || existing.expectedContractVersion() != command.expectedContractVersion()
                || !existing.externalTransferReference().equals(canonicalReference)
                || !existing.valueDate().equals(command.disbursementValueDate())
                || !existing.firstRepaymentDate().equals(command.firstRepaymentDate())
                || !existing.confirmedByUserId().equals(actorUserId)) {
            throw conflict(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Command request ID was already used for a different logical command."
            );
        }
    }

    private Result loadCompletedResult(
            ManualDisbursement disbursement,
            boolean replay
    ) {
        LoanApplication application = applications.findById(disbursement.loanApplicationId())
                .orElseThrow(ConfirmManualDisbursementService::systemStateConflict);
        LoanContract contract = contracts
                .findCurrentByApplicationId(disbursement.loanApplicationId())
                .orElseThrow(ConfirmManualDisbursementService::systemStateConflict);
        LoanAccount account = loanAccounts.findById(disbursement.loanAccountId())
                .orElseThrow(ConfirmManualDisbursementService::systemStateConflict);
        RepaymentSchedule schedule = repaymentSchedules
                .findByLoanAccountId(account.id())
                .orElseThrow(ConfirmManualDisbursementService::systemStateConflict);
        validateCompletedEvidence(application, contract, account, disbursement, schedule);
        return toResult(application, account, disbursement, schedule, replay);
    }

    private static void validateCompletedEvidence(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account,
            ManualDisbursement disbursement,
            RepaymentSchedule schedule
    ) {
        if (application.status() != LoanApplicationStatus.DISBURSED
                || contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT
                || contract.supersededAt() != null
                || !application.id().equals(contract.loanApplicationId())
                || !application.customerId().equals(
                        contract.disbursementBankAccount().customerId()
                )
                || !account.loanApplicationId().equals(application.id())
                || !account.loanContractId().equals(contract.id())
                || !account.customerId().equals(application.customerId())
                || !disbursement.loanApplicationId().equals(application.id())
                || !disbursement.loanContractId().equals(contract.id())
                || !disbursement.loanAccountId().equals(account.id())
                || disbursement.expectedContractVersion() != contract.contractVersion()
                || disbursement.disbursedAmount().compareTo(account.approvedPrincipal()) != 0
                || !schedule.loanApplicationId().equals(application.id())
                || !schedule.loanContractId().equals(contract.id())
                || !schedule.loanAccountId().equals(account.id())
                || schedule.approvedPrincipal().compareTo(account.approvedPrincipal()) != 0
                || schedule.totalInterest().compareTo(account.totalInterest()) != 0
                || schedule.feeAmount().compareTo(account.feeAmount()) != 0
                || schedule.totalRepaymentAmount().compareTo(
                        account.totalRepaymentAmount()) != 0
                || !schedule.firstDueDate().equals(disbursement.firstRepaymentDate())) {
            throw systemStateConflict();
        }
    }

    private static void rejectExistingActivation(
            LoanApplication application,
            LoanAccount account,
            ManualDisbursement disbursement,
            RepaymentSchedule schedule
    ) {
        if (application.status() == LoanApplicationStatus.DISBURSED
                || account != null && disbursement != null && schedule != null) {
            throw conflict(
                    "DISBURSEMENT_ALREADY_COMPLETED",
                    "Manual disbursement was already completed."
            );
        }
        if (account != null || disbursement != null || schedule != null) {
            throw systemStateConflict();
        }
    }

    private static void validateLifecycle(
            LoanApplication application,
            LoanContract contract,
            int expectedContractVersion
    ) {
        if (application.status() != LoanApplicationStatus.DISBURSEMENT_PENDING) {
            throw conflict(
                    "MANUAL_DISBURSEMENT_CONFIRMATION_NOT_ALLOWED",
                    "Loan application is not pending disbursement."
            );
        }
        if (!contract.loanApplicationId().equals(application.id())
                || !contract.disbursementBankAccount().customerId().equals(
                        application.customerId())) {
            throw systemStateConflict();
        }
        if (contract.contractVersion() != expectedContractVersion) {
            throw conflict(
                    "CONTRACT_VERSION_STALE",
                    "Expected contract version is stale."
            );
        }
        if (contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT
                || contract.supersededAt() != null
                || contract.confirmedAt() == null) {
            throw conflict(
                    "CONTRACT_NOT_READY_FOR_DISBURSEMENT",
                    "Current Loan contract is not ready for disbursement."
            );
        }
    }

    private void validateDates(Command command, LoanContract contract) {
        LocalDate valueDate = command.disbursementValueDate();
        LocalDate readinessDate = contract.confirmedAt().toLocalDate();
        if (valueDate.isAfter(LocalDate.now(clock)) || valueDate.isBefore(readinessDate)) {
            throw new BusinessRuleViolationException(
                    "DISBURSEMENT_VALUE_DATE_INVALID",
                    "Disbursement value date is not valid."
            );
        }
        LocalDate firstRepaymentDate = command.firstRepaymentDate();
        if (!firstRepaymentDate.isAfter(valueDate)
                || firstRepaymentDate.isAfter(valueDate.plusMonths(1))) {
            throw new BusinessRuleViolationException(
                    "FIRST_REPAYMENT_DATE_INVALID",
                    "First repayment date is not valid."
            );
        }
    }

    private static void validateActivationResult(
            LoanApplication application,
            LoanContract contract,
            UUID movementId,
            LoanProductActivationPolicy.ProductActivationResult result
    ) {
        BigDecimal principal = contract.financialTerms().approvedPrincipal();
        if (result.productCode() != application.productCode()
                || !result.movementId().equals(movementId)
                || result.convertedAmount().compareTo(principal) != 0) {
            throw systemStateConflict();
        }
    }

    private void publishAudit(
            BusinessOperationContext operation,
            LoanApplication previousApplication,
            LoanApplication finalApplication,
            LoanContract contract,
            LoanAccount account,
            ManualDisbursement disbursement,
            RepaymentSchedule schedule
    ) {
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, finalApplication.id())
                .put(BusinessAuditPayloadKey.LOAN_CONTRACT_ID, contract.id())
                .put(BusinessAuditPayloadKey.LOAN_ACCOUNT_ID, account.id())
                .put(BusinessAuditPayloadKey.MANUAL_DISBURSEMENT_ID, disbursement.id())
                .put(BusinessAuditPayloadKey.REPAYMENT_SCHEDULE_ID, schedule.id())
                .put(BusinessAuditPayloadKey.PRODUCT_CODE, finalApplication.productCode())
                .put(BusinessAuditPayloadKey.PREVIOUS_APPLICATION_STATUS,
                        previousApplication.status())
                .put(BusinessAuditPayloadKey.FINAL_APPLICATION_STATUS,
                        finalApplication.status())
                .put(BusinessAuditPayloadKey.LOAN_ACCOUNT_STATUS, account.status())
                .build();
        auditPublisher.publish(BusinessAuditEvent.single(
                operation,
                new BusinessAuditEntry(
                        BusinessAuditAction.MANUAL_DISBURSEMENT_CONFIRMED,
                        BusinessAuditEntityType.LOAN_APPLICATION,
                        finalApplication.id(),
                        payload
                )
        ));
    }

    private static Result toResult(
            LoanApplication application,
            LoanAccount account,
            ManualDisbursement disbursement,
            RepaymentSchedule schedule,
            boolean replay
    ) {
        return new Result(
                application.id(),
                application.status(),
                account.id(),
                account.accountNumber(),
                account.status(),
                account.activatedAt(),
                disbursement.id(),
                disbursement.disbursedAmount(),
                disbursement.valueDate(),
                disbursement.firstRepaymentDate(),
                schedule.id(),
                schedule.scheduleType(),
                schedule.version(),
                schedule.items().stream()
                        .map(ConfirmManualDisbursementService::toScheduleItem)
                        .toList(),
                replay
        );
    }

    private static ScheduleItem toScheduleItem(RepaymentScheduleItem item) {
        return new ScheduleItem(
                item.id(),
                item.sourceLoanContractRepaymentItemId(),
                item.installmentNumber(),
                item.dueDate(),
                item.principalDue(),
                item.interestDue(),
                item.feeDue(),
                item.totalDue()
        );
    }

    private static void requireAccounting(AuthenticatedUser actor) {
        if (!actor.roles().contains("ACCOUNTING_OFFICER")) {
            throw new AuthorizationException(
                    "ACCOUNTING_ROLE_REQUIRED",
                    "Accounting authority is required to confirm manual disbursement."
            );
        }
    }

    private static BusinessStateConflictException conflict(
            String code,
            String message
    ) {
        return new BusinessStateConflictException(code, message);
    }

    private static BusinessStateConflictException systemStateConflict() {
        return conflict(
                "SYSTEM_STATE_CONFLICT",
                "Loan activation evidence conflicts with existing state."
        );
    }
}
