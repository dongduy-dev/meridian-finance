package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcome;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcomeRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionSaveOutcome;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountServicingAction;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanAccountStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentServicingAction;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatusTransition;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.loan.domain.service.DeterministicRepaymentAllocator;
import com.meridian.platform.loan.domain.service.RepaymentServicingCalculator;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditEvidenceReader;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecordRepaymentService implements RecordRepaymentUseCase {
    private final RepaymentTransactionRepository transactions;
    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accounts;
    private final RepaymentScheduleRepository schedules;
    private final RepaymentInstallmentProgressRepository progressRepository;
    private final ManualDisbursementRepository disbursements;
    private final RepaymentInstallmentStatusTransitionRepository installmentHistory;
    private final LoanAccountStatusTransitionRepository accountHistory;
    private final RepaymentOperationOutcomeRepository outcomes;
    private final LoanProductRepaymentPolicyResolver policies;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final BusinessAuditEvidenceReader auditEvidence;
    private final Clock clock;
    private final DeterministicRepaymentAllocator allocator =
            new DeterministicRepaymentAllocator();
    private final RepaymentServicingCalculator servicing =
            new RepaymentServicingCalculator();

    public RecordRepaymentService(
            RepaymentTransactionRepository transactions,
            LoanApplicationRepository applications,
            LoanAccountRepository accounts,
            RepaymentScheduleRepository schedules,
            RepaymentInstallmentProgressRepository progressRepository,
            ManualDisbursementRepository disbursements,
            RepaymentInstallmentStatusTransitionRepository installmentHistory,
            LoanAccountStatusTransitionRepository accountHistory,
            RepaymentOperationOutcomeRepository outcomes,
            LoanProductRepaymentPolicyResolver policies,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            BusinessAuditEvidenceReader auditEvidence,
            Clock clock
    ) {
        this.transactions = transactions;
        this.applications = applications;
        this.accounts = accounts;
        this.schedules = schedules;
        this.progressRepository = progressRepository;
        this.disbursements = disbursements;
        this.installmentHistory = installmentHistory;
        this.accountHistory = accountHistory;
        this.outcomes = outcomes;
        this.policies = policies;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.auditEvidence = auditEvidence;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result record(Command command) {
        AuthenticatedUser actor = requireStaff(currentUsers.currentUser());
        Instant recordedInstant = clock.instant();
        LocalDateTime recordedAt = LocalDateTime.ofInstant(recordedInstant, ZoneOffset.UTC);
        LocalDate evaluationDate = LocalDate.ofInstant(recordedInstant, ZoneOffset.UTC);

        transactions.acquireRecordingRequestLock(command.requestId());
        transactions.findByRequestId(command.requestId())
                .ifPresent(existing -> validateIdentity(existing, command, actor));
        applications.acquireWorkflowLock(command.loanApplicationId());
        RepaymentTransaction existing = transactions.findByRequestId(command.requestId())
                .orElse(null);
        if (existing != null) {
            return replay(existing, command, actor);
        }

        LoanApplication application = applications
                .findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."
                ));
        if (application.status() != LoanApplicationStatus.DISBURSED) {
            throw repaymentNotAllowed();
        }
        LoanAccount account = accounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(RecordRepaymentService::stateConflict);
        validateOpenAccount(application, account);
        RepaymentSchedule schedule = schedules.findByLoanAccountIdForUpdate(account.id())
                .orElseThrow(RecordRepaymentService::stateConflict);
        validateSchedule(application, account, schedule);
        List<RepaymentInstallmentProgress> current =
                progressRepository.findByLoanAccountIdForUpdate(account.id());
        if (current.size() != schedule.items().size()) {
            throw stateConflict();
        }
        ManualDisbursement disbursement = disbursements.findByLoanAccountId(account.id())
                .orElseThrow(RecordRepaymentService::stateConflict);
        try {
            RepaymentTransaction.validateValueDate(
                    command.paymentValueDate(), disbursement.valueDate(), evaluationDate
            );
        } catch (BusinessRuleViolationException exception) {
            throw new BusinessRuleViolationException(
                    "REPAYMENT_VALUE_DATE_INVALID",
                    "Payment value date is outside the permitted UTC date range."
            );
        }
        if (command.amount().compareTo(account.repaymentBalance().totalOutstanding()) > 0) {
            throw new BusinessRuleViolationException(
                    "REPAYMENT_EXCEEDS_OUTSTANDING",
                    "Repayment amount exceeds the contractual outstanding amount."
            );
        }

        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = allocator.allocate(
                transactionId, command.amount(), schedule, current
        );
        RepaymentServicingCalculator.Result calculated = servicing.apply(
                schedule, current, allocations, command.paymentValueDate(),
                recordedAt, evaluationDate
        );
        RepaymentTransaction transaction = RepaymentTransaction.recorded(
                transactionId, application.id(), account.id(), schedule.id(),
                command.requestId(), command.externalPaymentReference(), command.amount(),
                command.paymentValueDate(), disbursement.valueDate(), evaluationDate,
                actor.userId(), recordedAt, allocations
        );
        RepaymentTransactionSaveOutcome saved = transactions.save(transaction);
        if (!(saved instanceof RepaymentTransactionSaveOutcome.Inserted)) {
            return resolveSaveConflict(saved, command, actor);
        }

        progressRepository.saveAll(calculated.progress());
        appendInstallmentHistory(
                current, calculated, transactionId, actor.userId(), recordedAt, evaluationDate
        );
        LoanAccount updatedAccount = account.withServicingState(
                calculated.balance(), calculated.accountStatus(), recordedAt
        );
        accounts.updateServicingState(updatedAccount);
        boolean accountStatusChanged = account.status() != updatedAccount.status();
        if (accountStatusChanged) {
            accountHistory.save(new LoanAccountStatusTransition(
                    UUID.randomUUID(), account.id(),
                    accountHistory.nextSequenceNumber(account.id()), transactionId,
                    account.status(), updatedAccount.status(),
                    LoanAccountServicingAction.REPAYMENT_RECORDED,
                    ActorType.USER, actor.userId(), evaluationDate, recordedAt
            ));
        }

        LoanProductRepaymentPolicy policy = policies.resolve(application.productCode());
        BigDecimal principalReleased = policy.releasePrincipal(
                new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                        application, updatedAccount, transactionId, allocations, recordedAt
                )
        );
        RepaymentOperationOutcome outcome = RepaymentOperationOutcome.captured(
                transactionId, application.id(), account.id(), schedule.id(),
                command.amount(), command.paymentValueDate(), recordedAt,
                calculated.balance(), calculated.accountStatus(), accountStatusChanged,
                principalReleased, current, calculated.progress(),
                calculated.installmentStatusChanges()
        );
        outcomes.save(outcome);
        publishAudit(transactionId, account.id(), accountStatusChanged, actor.userId(), recordedAt);
        return toResult(transaction, outcome, schedule, false);
    }

    private Result replay(
            RepaymentTransaction transaction,
            Command command,
            AuthenticatedUser actor
    ) {
        validateIdentity(transaction, command, actor);
        LoanApplication application = applications.findByIdForUpdate(transaction.loanApplicationId())
                .orElseThrow(RecordRepaymentService::stateConflict);
        LoanAccount account = accounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(RecordRepaymentService::stateConflict);
        RepaymentSchedule schedule = schedules.findByLoanAccountIdForUpdate(account.id())
                .orElseThrow(RecordRepaymentService::stateConflict);
        validateSchedule(application, account, schedule);
        RepaymentOperationOutcome outcome = outcomes.findByRepaymentTransactionId(transaction.id())
                .orElseThrow(RecordRepaymentService::stateConflict);
        validateOutcome(transaction, outcome);
        policies.resolve(application.productCode()).validateCompletedRelease(
                new LoanProductRepaymentPolicy.CompletedReleaseCommand(
                        application, account, transaction.id(), outcome.principalReleased()
                )
        );
        validateReplayEvidence(transaction, outcome);
        return toResult(transaction, outcome, schedule, true);
    }

    private Result resolveSaveConflict(
            RepaymentTransactionSaveOutcome outcome,
            Command command,
            AuthenticatedUser actor
    ) {
        if (outcome instanceof RepaymentTransactionSaveOutcome.ExistingRequest existing) {
            validateIdentity(existing.transaction(), command, actor);
            return replay(existing.transaction(), command, actor);
        }
        if (outcome instanceof RepaymentTransactionSaveOutcome.Conflict conflict
                && conflict.kind() == RepaymentTransactionSaveOutcome.ConflictKind
                .EXTERNAL_PAYMENT_REFERENCE) {
            throw new BusinessStateConflictException(
                    "DUPLICATE_PAYMENT_REFERENCE",
                    "External payment evidence was already recorded."
            );
        }
        throw stateConflict();
    }

    private void appendInstallmentHistory(
            List<RepaymentInstallmentProgress> current,
            RepaymentServicingCalculator.Result calculated,
            UUID transactionId,
            UUID actorId,
            LocalDateTime recordedAt,
            LocalDate evaluationDate
    ) {
        Map<UUID, RepaymentInstallmentProgress> before = new LinkedHashMap<>();
        current.forEach(item -> before.put(item.repaymentScheduleItemId(), item));
        for (RepaymentInstallmentProgress after : calculated.progress()) {
            RepaymentInstallmentProgress prior = before.get(after.repaymentScheduleItemId());
            if (prior.status() != after.status()) {
                installmentHistory.save(new RepaymentInstallmentStatusTransition(
                        UUID.randomUUID(), after.repaymentScheduleItemId(),
                        installmentHistory.nextSequenceNumber(after.repaymentScheduleItemId()),
                        transactionId, prior.status(), after.status(),
                        RepaymentInstallmentServicingAction.REPAYMENT_RECORDED,
                        ActorType.USER, actorId, evaluationDate, recordedAt
                ));
            }
        }
    }

    private void publishAudit(
            UUID transactionId,
            UUID accountId,
            boolean accountStatusChanged,
            UUID actorId,
            LocalDateTime recordedAt
    ) {
        ArrayList<BusinessAuditEntry> entries = new ArrayList<>();
        entries.add(BusinessAuditEntry.of(
                BusinessAuditAction.REPAYMENT_RECORDED,
                BusinessAuditEntityType.REPAYMENT_TRANSACTION,
                transactionId
        ));
        if (accountStatusChanged) {
            entries.add(BusinessAuditEntry.of(
                    BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED,
                    BusinessAuditEntityType.LOAN_ACCOUNT,
                    accountId
            ));
        }
        auditPublisher.publish(new BusinessAuditEvent(
                BusinessOperationContext.user(transactionId, actorId, recordedAt), entries
        ));
    }

    private void validateReplayEvidence(
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome
    ) {
        long repaymentAudit = auditEvidence.countMatching(
                BusinessAuditAction.REPAYMENT_RECORDED,
                BusinessAuditEntityType.REPAYMENT_TRANSACTION,
                transaction.id()
        );
        long accountAudit = auditEvidence.countMatchingOperation(
                transaction.id(),
                BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED,
                BusinessAuditEntityType.LOAN_ACCOUNT,
                transaction.loanAccountId()
        );
        List<RepaymentInstallmentStatusTransition> installmentTransitions =
                installmentHistory.findByOperationId(transaction.id());
        Map<UUID, RepaymentInstallmentStatusTransition> transitionsByItem =
                new LinkedHashMap<>();
        for (RepaymentInstallmentStatusTransition transition : installmentTransitions) {
            if (transitionsByItem.put(
                    transition.repaymentScheduleItemId(),
                    transition
            ) != null) {
                throw stateConflict();
            }
        }
        for (RepaymentOperationOutcome.InstallmentOutcome item : outcome.installments()) {
            RepaymentInstallmentStatusTransition transition = transitionsByItem.remove(
                    item.progress().repaymentScheduleItemId()
            );
            if (item.statusChanged()) {
                if (transition == null
                        || transition.fromStatus() != item.previousStatus()
                        || transition.toStatus() != item.progress().status()) {
                    throw stateConflict();
                }
            } else if (transition != null
                    || item.previousStatus() != item.progress().status()) {
                throw stateConflict();
            }
        }
        long actualAccountTransitions = accountHistory
                .findByLoanAccountId(transaction.loanAccountId()).stream()
                .filter(item -> transaction.id().equals(item.operationId()))
                .count();
        if (repaymentAudit != 1
                || accountAudit != (outcome.accountStatusChanged() ? 1 : 0)
                || !transitionsByItem.isEmpty()
                || actualAccountTransitions != (outcome.accountStatusChanged() ? 1 : 0)) {
            throw stateConflict();
        }
    }

    private static void validateIdentity(
            RepaymentTransaction transaction,
            Command command,
            AuthenticatedUser actor
    ) {
        if (!transaction.requestId().equals(command.requestId())
                || !transaction.loanApplicationId().equals(command.loanApplicationId())
                || !transaction.externalPaymentReference()
                .equals(command.externalPaymentReference())
                || transaction.receivedAmount().compareTo(command.amount()) != 0
                || !transaction.paymentValueDate().equals(command.paymentValueDate())
                || !transaction.recordedByUserId().equals(actor.userId())) {
            throw new BusinessStateConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Repayment request identifier was reused for a different operation."
            );
        }
    }

    private static void validateOutcome(
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome
    ) {
        BigDecimal principal = transaction.allocations().stream()
                .filter(item -> item.component().name().equals("PRINCIPAL"))
                .map(RepaymentAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!transaction.id().equals(outcome.repaymentTransactionId())
                || !transaction.loanApplicationId().equals(outcome.loanApplicationId())
                || !transaction.loanAccountId().equals(outcome.loanAccountId())
                || !transaction.repaymentScheduleId().equals(outcome.repaymentScheduleId())
                || transaction.receivedAmount().compareTo(outcome.receivedAmount()) != 0
                || !transaction.paymentValueDate().equals(outcome.paymentValueDate())
                || !transaction.recordedAt().equals(outcome.recordedAt())
                || principal.compareTo(outcome.principalReleased()) != 0) {
            throw stateConflict();
        }
    }

    private static void validateOpenAccount(LoanApplication application, LoanAccount account) {
        if (!application.id().equals(account.loanApplicationId())
                || !application.customerId().equals(account.customerId())) {
            throw stateConflict();
        }
        if (account.status() == LoanAccountStatus.SETTLED
                || account.status() == LoanAccountStatus.CLOSED) {
            throw repaymentNotAllowed();
        }
    }

    private static void validateSchedule(
            LoanApplication application,
            LoanAccount account,
            RepaymentSchedule schedule
    ) {
        if (schedule.scheduleType() != RepaymentScheduleType.FINAL
                || schedule.version() != RepaymentSchedule.INITIAL_FINAL_VERSION
                || !application.id().equals(schedule.loanApplicationId())
                || !account.id().equals(schedule.loanAccountId())
                || !account.loanContractId().equals(schedule.loanContractId())
                || account.repaymentBalance().totalOutstanding().compareTo(
                schedule.totalRepaymentAmount().subtract(
                        account.repaymentBalance().totalPaid())) != 0) {
            throw stateConflict();
        }
    }

    private static AuthenticatedUser requireStaff(AuthenticatedUser actor) {
        if (actor == null || !"STAFF".equals(actor.userType())) {
            throw new AuthorizationException(
                    "STAFF_CONTEXT_REQUIRED",
                    "Repayment recording requires an authenticated staff actor."
            );
        }
        return actor;
    }

    private static Result toResult(
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome,
            RepaymentSchedule schedule,
            boolean replay
    ) {
        Map<UUID, RepaymentScheduleItem> scheduleItems = schedule.items().stream()
                .collect(Collectors.toMap(RepaymentScheduleItem::id, item -> item));
        List<Allocation> allocations = transaction.allocations().stream()
                .map(item -> {
                    RepaymentScheduleItem scheduleItem = scheduleItems.get(
                            item.repaymentScheduleItemId()
                    );
                    if (scheduleItem == null) {
                        throw stateConflict();
                    }
                    return new Allocation(item.allocationSequence(), scheduleItem.id(),
                            scheduleItem.installmentNumber(), item.component(), item.amount());
                }).toList();
        List<InstallmentProgress> progress = outcome.installments().stream()
                .map(item -> toProgress(item, scheduleItems)).toList();
        RepaymentBalance balance = outcome.accountBalance();
        AccountBalance accountBalance = new AccountBalance(
                balance.principalPaid(), balance.interestPaid(), balance.feePaid(),
                balance.totalPaid(), balance.principalOutstanding(),
                balance.interestOutstanding(), balance.feeOutstanding(),
                balance.totalOutstanding(), balance.lastPaymentValueDate(),
                balance.lastPaymentRecordedAt(), balance.servicingEvaluationDate(),
                outcome.accountStatus()
        );
        return new Result(
                outcome.loanApplicationId(), outcome.loanAccountId(),
                transaction.id(), outcome.repaymentScheduleId(),
                outcome.receivedAmount(), outcome.paymentValueDate(), outcome.recordedAt(),
                allocations, progress, accountBalance, outcome.principalReleased(), replay
        );
    }

    private static InstallmentProgress toProgress(
            RepaymentOperationOutcome.InstallmentOutcome outcome,
            Map<UUID, RepaymentScheduleItem> scheduleItems
    ) {
        RepaymentInstallmentProgress item = outcome.progress();
        RepaymentScheduleItem scheduleItem = scheduleItems.get(
                item.repaymentScheduleItemId()
        );
        if (scheduleItem == null) {
            throw stateConflict();
        }
        return new InstallmentProgress(
                item.repaymentScheduleItemId(), item.installmentNumber(),
                scheduleItem.dueDate(),
                item.principalPaid(), item.interestPaid(), item.feePaid(), item.totalPaid(),
                item.principalOutstanding(), item.interestOutstanding(),
                item.feeOutstanding(), item.totalOutstanding(), outcome.previousStatus(),
                item.status(),
                item.lastPaymentValueDate(), item.lastPaymentRecordedAt(),
                item.servicingEvaluationDate(), outcome.statusChanged()
        );
    }

    private static BusinessStateConflictException repaymentNotAllowed() {
        return new BusinessStateConflictException(
                "REPAYMENT_NOT_ALLOWED", "Repayment is not allowed for the Loan Account."
        );
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Repayment servicing evidence is inconsistent."
        );
    }
}
