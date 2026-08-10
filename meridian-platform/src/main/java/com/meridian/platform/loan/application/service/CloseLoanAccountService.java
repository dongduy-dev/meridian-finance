package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountClosureRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountClosureSaveOutcome;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcome;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcomeRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountClosure;
import com.meridian.platform.loan.domain.model.LoanAccountServicingAction;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanAccountStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.loan.domain.model.RepaymentTransactionType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
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
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CloseLoanAccountService implements CloseLoanAccountUseCase {

    private final LoanAccountClosureRepository closures;
    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accounts;
    private final RepaymentScheduleRepository schedules;
    private final RepaymentInstallmentProgressRepository progressRepository;
    private final LoanAccountStatusTransitionRepository accountHistory;
    private final RepaymentTransactionRepository transactions;
    private final RepaymentOperationOutcomeRepository outcomes;
    private final ApprovedLoanSettlementRepository settlements;
    private final SalaryAdvanceLimitMovementRepository movements;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final BusinessAuditEvidenceReader auditEvidence;
    private final Clock clock;

    public CloseLoanAccountService(
            LoanAccountClosureRepository closures,
            LoanApplicationRepository applications,
            LoanAccountRepository accounts,
            RepaymentScheduleRepository schedules,
            RepaymentInstallmentProgressRepository progressRepository,
            LoanAccountStatusTransitionRepository accountHistory,
            RepaymentTransactionRepository transactions,
            RepaymentOperationOutcomeRepository outcomes,
            ApprovedLoanSettlementRepository settlements,
            SalaryAdvanceLimitMovementRepository movements,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            BusinessAuditEvidenceReader auditEvidence,
            Clock clock
    ) {
        this.closures = closures;
        this.applications = applications;
        this.accounts = accounts;
        this.schedules = schedules;
        this.progressRepository = progressRepository;
        this.accountHistory = accountHistory;
        this.transactions = transactions;
        this.outcomes = outcomes;
        this.settlements = settlements;
        this.movements = movements;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.auditEvidence = auditEvidence;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result close(Command command) {
        AuthenticatedUser actor = requireAccountingOfficer(currentUsers.currentUser());
        closures.acquireClosureRequestLock(command.requestId());
        LoanAccountClosure existing = closures.findByRequestId(command.requestId())
                .orElse(null);
        if (existing != null) {
            validateIdentity(existing, command, actor);
        }

        applications.acquireWorkflowLock(command.loanApplicationId());
        existing = closures.findByRequestId(command.requestId()).orElse(null);
        if (existing != null) {
            return replay(existing, command, actor);
        }

        LoanApplication application = applications
                .findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan Application was not found."
                ));
        validateApplication(application);
        LoanAccount account = accounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(CloseLoanAccountService::stateConflict);
        validateAccountIdentity(application, account);
        if (account.status() != LoanAccountStatus.SETTLED) {
            throw closureNotAllowed();
        }
        if (closures.findByLoanAccountId(account.id()).isPresent()) {
            throw stateConflict();
        }

        RepaymentSchedule schedule = schedules.findByLoanAccountId(account.id())
                .orElseThrow(CloseLoanAccountService::stateConflict);
        List<RepaymentInstallmentProgress> progress = progressRepository
                .findByRepaymentScheduleId(schedule.id());
        validateFinancialAndServicingEvidence(
                application,
                account,
                schedule,
                progress,
                null
        );

        LocalDateTime closedAt = ServicingEvidenceTimestamp.normalizeForPersistence(
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
        LoanAccount closedAccount = account.closeAdministratively(closedAt);
        LoanAccountClosure closure = LoanAccountClosure.recorded(
                UUID.randomUUID(),
                closedAccount,
                command.requestId(),
                actor.userId(),
                closedAt
        );
        LoanAccountClosureSaveOutcome saveOutcome = closures.save(closure);
        if (!(saveOutcome instanceof LoanAccountClosureSaveOutcome.Inserted)) {
            return resolveSaveConflict(saveOutcome, command, actor);
        }

        accounts.updateServicingState(closedAccount);
        accountHistory.save(new LoanAccountStatusTransition(
                UUID.randomUUID(),
                account.id(),
                accountHistory.nextSequenceNumber(account.id()),
                closure.id(),
                LoanAccountStatus.SETTLED,
                LoanAccountStatus.CLOSED,
                LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE,
                ActorType.USER,
                actor.userId(),
                account.servicingEvaluationDate(),
                closedAt
        ));
        publishAudit(closure, actor.userId());
        return result(closure, false);
    }

    private Result replay(
            LoanAccountClosure closure,
            Command command,
            AuthenticatedUser actor
    ) {
        validateIdentity(closure, command, actor);
        LoanApplication application = applications
                .findByIdForUpdate(closure.loanApplicationId())
                .orElseThrow(CloseLoanAccountService::stateConflict);
        validateApplication(application);
        LoanAccount account = accounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(CloseLoanAccountService::stateConflict);
        validateAccountIdentity(application, account);
        if (account.status() != LoanAccountStatus.CLOSED
                || !account.updatedAt().equals(closure.closedAt())
                || !account.id().equals(closure.loanAccountId())) {
            throw stateConflict();
        }
        RepaymentSchedule schedule = schedules.findByLoanAccountId(account.id())
                .orElseThrow(CloseLoanAccountService::stateConflict);
        List<RepaymentInstallmentProgress> progress = progressRepository
                .findByRepaymentScheduleId(schedule.id());
        validateFinancialAndServicingEvidence(
                application,
                account,
                schedule,
                progress,
                closure
        );
        validateClosureEvidence(closure, account);
        return result(closure, true);
    }

    private Result resolveSaveConflict(
            LoanAccountClosureSaveOutcome outcome,
            Command command,
            AuthenticatedUser actor
    ) {
        if (outcome instanceof LoanAccountClosureSaveOutcome.ExistingRequest existing) {
            validateIdentity(existing.closure(), command, actor);
            return replay(existing.closure(), command, actor);
        }
        if (outcome instanceof LoanAccountClosureSaveOutcome.Conflict conflict
                && conflict.kind()
                == LoanAccountClosureSaveOutcome.ConflictKind.LOAN_ACCOUNT) {
            throw closureNotAllowed();
        }
        throw stateConflict();
    }

    private void validateFinancialAndServicingEvidence(
            LoanApplication application,
            LoanAccount account,
            RepaymentSchedule schedule,
            List<RepaymentInstallmentProgress> progress,
            LoanAccountClosure closure
    ) {
        validateSchedule(application, account, schedule);
        Map<UUID, RepaymentInstallmentProgress> currentProgress =
                validateProgress(account, schedule, progress);
        List<LoanAccountStatusTransition> history = accountHistory
                .findByLoanAccountId(account.id());
        LoanAccountStatusTransition settledTransition = validateHistory(
                account,
                history,
                closure
        );
        RepaymentTransaction transaction = transactions
                .findById(settledTransition.operationId())
                .orElseThrow(CloseLoanAccountService::stateConflict);
        RepaymentOperationOutcome outcome = outcomes
                .findByRepaymentTransactionId(transaction.id())
                .orElseThrow(CloseLoanAccountService::stateConflict);
        validatePayoffProvenance(
                application,
                account,
                schedule,
                currentProgress,
                settledTransition,
                transaction,
                outcome
        );
        validateSalaryAdvanceExposure(application, account);
    }

    private static void validateSchedule(
            LoanApplication application,
            LoanAccount account,
            RepaymentSchedule schedule
    ) {
        if (schedule.scheduleType() != RepaymentScheduleType.FINAL
                || schedule.version() != RepaymentSchedule.INITIAL_FINAL_VERSION
                || !schedule.loanApplicationId().equals(application.id())
                || !schedule.loanAccountId().equals(account.id())
                || !schedule.loanContractId().equals(account.loanContractId())
                || schedule.approvedPrincipal().compareTo(
                account.approvedPrincipal()) != 0
                || schedule.totalInterest().compareTo(account.totalInterest()) != 0
                || schedule.feeAmount().compareTo(account.feeAmount()) != 0
                || schedule.totalRepaymentAmount().compareTo(
                account.totalRepaymentAmount()) != 0) {
            throw stateConflict();
        }
    }

    private static Map<UUID, RepaymentInstallmentProgress> validateProgress(
            LoanAccount account,
            RepaymentSchedule schedule,
            List<RepaymentInstallmentProgress> progress
    ) {
        if (progress.size() != schedule.items().size()) {
            throw stateConflict();
        }
        Map<UUID, RepaymentInstallmentProgress> progressByItem =
                new LinkedHashMap<>();
        BigDecimal principalPaid = BigDecimal.ZERO;
        BigDecimal interestPaid = BigDecimal.ZERO;
        BigDecimal feePaid = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (RepaymentInstallmentProgress item : progress) {
            if (!item.repaymentScheduleId().equals(schedule.id())
                    || !item.loanAccountId().equals(account.id())
                    || item.status() != RepaymentInstallmentStatus.PAID
                    || item.totalOutstanding().signum() != 0
                    || item.principalOutstanding().signum() != 0
                    || item.interestOutstanding().signum() != 0
                    || item.feeOutstanding().signum() != 0
                    || !item.servicingEvaluationDate().equals(
                    account.servicingEvaluationDate())
                    || progressByItem.put(
                    item.repaymentScheduleItemId(), item) != null) {
                throw stateConflict();
            }
            principalPaid = principalPaid.add(item.principalPaid());
            interestPaid = interestPaid.add(item.interestPaid());
            feePaid = feePaid.add(item.feePaid());
            totalPaid = totalPaid.add(item.totalPaid());
        }
        schedule.items().forEach(item -> {
            RepaymentInstallmentProgress current = progressByItem.get(item.id());
            if (current == null) {
                throw stateConflict();
            }
            current.validateAgainst(item);
        });
        RepaymentBalance balance = account.repaymentBalance();
        if (balance.totalOutstanding().signum() != 0
                || principalPaid.compareTo(balance.principalPaid()) != 0
                || interestPaid.compareTo(balance.interestPaid()) != 0
                || feePaid.compareTo(balance.feePaid()) != 0
                || totalPaid.compareTo(balance.totalPaid()) != 0) {
            throw stateConflict();
        }
        return progressByItem;
    }

    private static LoanAccountStatusTransition validateHistory(
            LoanAccount account,
            List<LoanAccountStatusTransition> history,
            LoanAccountClosure closure
    ) {
        if (history.isEmpty()) {
            throw stateConflict();
        }
        LoanAccountStatus prior = null;
        for (int index = 0; index < history.size(); index++) {
            LoanAccountStatusTransition transition = history.get(index);
            if (!transition.loanAccountId().equals(account.id())
                    || transition.sequenceNumber() != index + 1
                    || transition.fromStatus() != prior) {
                throw stateConflict();
            }
            prior = transition.toStatus();
        }
        int settledIndex;
        if (closure == null) {
            if (prior != LoanAccountStatus.SETTLED) {
                throw stateConflict();
            }
            settledIndex = history.size() - 1;
        } else {
            LoanAccountStatusTransition closeTransition = history.getLast();
            if (prior != LoanAccountStatus.CLOSED
                    || history.size() < 2
                    || !closeTransition.operationId().equals(closure.id())
                    || closeTransition.fromStatus() != LoanAccountStatus.SETTLED
                    || closeTransition.toStatus() != LoanAccountStatus.CLOSED
                    || closeTransition.action()
                    != LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE
                    || closeTransition.actorType() != ActorType.USER
                    || !closeTransition.actorUserId().equals(
                    closure.closedByUserId())
                    || !closeTransition.occurredAt().equals(closure.closedAt())
                    || !closeTransition.servicingEvaluationDate().equals(
                    account.servicingEvaluationDate())) {
                throw stateConflict();
            }
            settledIndex = history.size() - 2;
        }
        LoanAccountStatusTransition settled = history.get(settledIndex);
        if (settled.toStatus() != LoanAccountStatus.SETTLED
                || (settled.action()
                != LoanAccountServicingAction.REPAYMENT_RECORDED
                && settled.action()
                != LoanAccountServicingAction.APPROVED_SETTLEMENT)) {
            throw stateConflict();
        }
        return settled;
    }

    private void validatePayoffProvenance(
            LoanApplication application,
            LoanAccount account,
            RepaymentSchedule schedule,
            Map<UUID, RepaymentInstallmentProgress> currentProgress,
            LoanAccountStatusTransition settledTransition,
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome
    ) {
        RepaymentTransactionType expectedType = settledTransition.action()
                == LoanAccountServicingAction.APPROVED_SETTLEMENT
                ? RepaymentTransactionType.APPROVED_SETTLEMENT
                : RepaymentTransactionType.REPAYMENT;
        BigDecimal principalAllocated = transaction.allocations().stream()
                .filter(item -> item.component()
                == RepaymentAllocationComponent.PRINCIPAL)
                .map(item -> item.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (transaction.transactionType() != expectedType
                || !transaction.id().equals(settledTransition.operationId())
                || !transaction.loanApplicationId().equals(application.id())
                || !transaction.loanAccountId().equals(account.id())
                || !transaction.repaymentScheduleId().equals(schedule.id())
                || !transaction.recordedAt().equals(
                settledTransition.occurredAt())
                || !transaction.recordedByUserId().equals(
                settledTransition.actorUserId())
                || !outcome.repaymentTransactionId().equals(transaction.id())
                || !outcome.loanApplicationId().equals(application.id())
                || !outcome.loanAccountId().equals(account.id())
                || !outcome.repaymentScheduleId().equals(schedule.id())
                || outcome.accountStatus() != LoanAccountStatus.SETTLED
                || !outcome.accountStatusChanged()
                || outcome.receivedAmount().compareTo(
                transaction.receivedAmount()) != 0
                || !outcome.paymentValueDate().equals(
                transaction.paymentValueDate())
                || !ServicingEvidenceTimestamp.same(
                outcome.recordedAt(), transaction.recordedAt())
                || outcome.principalReleased().compareTo(principalAllocated) != 0
                || !sameBalance(outcome.accountBalance(),
                account.repaymentBalance())
                || outcome.installments().size() != currentProgress.size()) {
            throw stateConflict();
        }
        for (var allocation : transaction.allocations()) {
            if (!currentProgress.containsKey(
                    allocation.repaymentScheduleItemId())) {
                throw stateConflict();
            }
        }
        Map<UUID, RepaymentInstallmentProgress> outcomeProgress =
                new LinkedHashMap<>();
        outcome.installments().forEach(item -> {
            RepaymentInstallmentProgress previous = outcomeProgress.put(
                    item.progress().repaymentScheduleItemId(),
                    item.progress()
            );
            if (previous != null) {
                throw stateConflict();
            }
        });
        if (!sameProgress(outcomeProgress, currentProgress)) {
            throw stateConflict();
        }
        ApprovedLoanSettlement settlement = settlements
                .findByRepaymentTransactionId(transaction.id())
                .orElse(null);
        if (expectedType == RepaymentTransactionType.APPROVED_SETTLEMENT) {
            if (settlement == null
                    || !settlement.loanApplicationId().equals(application.id())
                    || !settlement.loanAccountId().equals(account.id())
                    || !settlement.repaymentTransactionId().equals(
                    transaction.id())
                    || settlement.settlementAmount().compareTo(
                    transaction.receivedAmount()) != 0
                    || !settlement.approvedByUserId().equals(
                    transaction.recordedByUserId())
                    || !settlement.approvedAt().equals(
                    transaction.recordedAt())) {
                throw stateConflict();
            }
        } else if (settlement != null) {
            throw stateConflict();
        }
    }

    private void validateSalaryAdvanceExposure(
            LoanApplication application,
            LoanAccount account
    ) {
        List<SalaryAdvanceLimitMovement> conversions = movements
                .findByLoanApplicationIdAndMovementType(
                        application.id(),
                        SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
                );
        List<SalaryAdvanceLimitMovement> releases = movements
                .findByLoanApplicationIdAndMovementType(
                        application.id(),
                        SalaryAdvanceLimitMovementType.REPAID_RELEASED
                );
        if (conversions.size() != 1
                || !conversions.getFirst().loanAccountId().equals(account.id())
                || conversions.getFirst().amount().compareTo(
                account.approvedPrincipal()) != 0) {
            throw stateConflict();
        }
        BigDecimal released = BigDecimal.ZERO;
        for (SalaryAdvanceLimitMovement movement : releases) {
            if (!movement.loanAccountId().equals(account.id())
                    || movement.repaymentTransactionId() == null) {
                throw stateConflict();
            }
            RepaymentTransaction transaction = transactions
                    .findById(movement.repaymentTransactionId())
                    .orElseThrow(CloseLoanAccountService::stateConflict);
            RepaymentOperationOutcome outcome = outcomes
                    .findByRepaymentTransactionId(transaction.id())
                    .orElseThrow(CloseLoanAccountService::stateConflict);
            BigDecimal principal = transaction.allocations().stream()
                    .filter(item -> item.component()
                    == RepaymentAllocationComponent.PRINCIPAL)
                    .map(item -> item.amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!transaction.loanApplicationId().equals(application.id())
                    || !transaction.loanAccountId().equals(account.id())
                    || movement.amount().compareTo(principal) != 0
                    || outcome.principalReleased().compareTo(principal) != 0) {
                throw stateConflict();
            }
            released = released.add(movement.amount());
        }
        if (released.compareTo(account.approvedPrincipal()) != 0) {
            throw stateConflict();
        }
    }

    private void validateClosureEvidence(
            LoanAccountClosure closure,
            LoanAccount account
    ) {
        if (auditEvidence.countMatchingOperation(
                closure.id(),
                BusinessAuditAction.LOAN_ACCOUNT_CLOSED,
                BusinessAuditEntityType.LOAN_ACCOUNT_CLOSURE,
                closure.id()
        ) != 1 || auditEvidence.countMatchingOperation(
                closure.id(),
                BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED,
                BusinessAuditEntityType.LOAN_ACCOUNT,
                account.id()
        ) != 1) {
            throw stateConflict();
        }
    }

    private void publishAudit(LoanAccountClosure closure, UUID actorId) {
        ArrayList<BusinessAuditEntry> entries = new ArrayList<>();
        entries.add(BusinessAuditEntry.of(
                BusinessAuditAction.LOAN_ACCOUNT_CLOSED,
                BusinessAuditEntityType.LOAN_ACCOUNT_CLOSURE,
                closure.id()
        ));
        entries.add(BusinessAuditEntry.of(
                BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED,
                BusinessAuditEntityType.LOAN_ACCOUNT,
                closure.loanAccountId()
        ));
        auditPublisher.publish(new BusinessAuditEvent(
                BusinessOperationContext.user(
                        closure.id(),
                        actorId,
                        closure.closedAt()
                ),
                entries
        ));
    }

    private static void validateIdentity(
            LoanAccountClosure closure,
            Command command,
            AuthenticatedUser actor
    ) {
        if (!closure.requestId().equals(command.requestId())
                || !closure.loanApplicationId().equals(
                command.loanApplicationId())
                || !closure.closedByUserId().equals(actor.userId())) {
            throw new BusinessStateConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Closure request identifier was reused for a different operation."
            );
        }
    }

    private static void validateApplication(LoanApplication application) {
        if (application.status() != LoanApplicationStatus.DISBURSED
                || application.productCode() != ProductCode.SALARY_ADVANCE) {
            throw closureNotAllowed();
        }
    }

    private static void validateAccountIdentity(
            LoanApplication application,
            LoanAccount account
    ) {
        if (!application.id().equals(account.loanApplicationId())
                || !application.customerId().equals(account.customerId())) {
            throw stateConflict();
        }
    }

    private static boolean sameBalance(
            RepaymentBalance left,
            RepaymentBalance right
    ) {
        return left.principalPaid().compareTo(right.principalPaid()) == 0
                && left.interestPaid().compareTo(right.interestPaid()) == 0
                && left.feePaid().compareTo(right.feePaid()) == 0
                && left.totalPaid().compareTo(right.totalPaid()) == 0
                && left.principalOutstanding().compareTo(
                right.principalOutstanding()) == 0
                && left.interestOutstanding().compareTo(
                right.interestOutstanding()) == 0
                && left.feeOutstanding().compareTo(
                right.feeOutstanding()) == 0
                && left.totalOutstanding().compareTo(
                right.totalOutstanding()) == 0
                && java.util.Objects.equals(
                left.lastPaymentValueDate(), right.lastPaymentValueDate())
                && ServicingEvidenceTimestamp.same(
                left.lastPaymentRecordedAt(), right.lastPaymentRecordedAt())
                && left.servicingEvaluationDate().equals(
                right.servicingEvaluationDate());
    }

    private static boolean sameProgress(
            Map<UUID, RepaymentInstallmentProgress> left,
            Map<UUID, RepaymentInstallmentProgress> right
    ) {
        if (!left.keySet().equals(right.keySet())) {
            return false;
        }
        return left.entrySet().stream().allMatch(entry ->
                sameProgress(entry.getValue(), right.get(entry.getKey()))
        );
    }

    private static boolean sameProgress(
            RepaymentInstallmentProgress left,
            RepaymentInstallmentProgress right
    ) {
        return right != null
                && left.repaymentScheduleItemId().equals(
                right.repaymentScheduleItemId())
                && left.repaymentScheduleId().equals(right.repaymentScheduleId())
                && left.loanAccountId().equals(right.loanAccountId())
                && left.installmentNumber() == right.installmentNumber()
                && left.principalPaid().compareTo(right.principalPaid()) == 0
                && left.interestPaid().compareTo(right.interestPaid()) == 0
                && left.feePaid().compareTo(right.feePaid()) == 0
                && left.totalPaid().compareTo(right.totalPaid()) == 0
                && left.principalOutstanding().compareTo(
                right.principalOutstanding()) == 0
                && left.interestOutstanding().compareTo(
                right.interestOutstanding()) == 0
                && left.feeOutstanding().compareTo(
                right.feeOutstanding()) == 0
                && left.totalOutstanding().compareTo(
                right.totalOutstanding()) == 0
                && left.status() == right.status()
                && java.util.Objects.equals(
                left.lastPaymentValueDate(), right.lastPaymentValueDate())
                && ServicingEvidenceTimestamp.same(
                left.lastPaymentRecordedAt(), right.lastPaymentRecordedAt())
                && left.servicingEvaluationDate().equals(
                right.servicingEvaluationDate())
                && ServicingEvidenceTimestamp.same(
                left.updatedAt(), right.updatedAt());
    }

    private static Result result(
            LoanAccountClosure closure,
            boolean replay
    ) {
        return new Result(
                closure.loanApplicationId(),
                closure.loanAccountId(),
                LoanAccountStatus.CLOSED,
                closure.closedAt(),
                replay
        );
    }

    private static AuthenticatedUser requireAccountingOfficer(
            AuthenticatedUser actor
    ) {
        if (actor == null
                || !"STAFF".equals(actor.userType())
                || !actor.roles().contains("ACCOUNTING_OFFICER")) {
            throw new AuthorizationException(
                    "ACCOUNTING_OFFICER_ROLE_REQUIRED",
                    "Accounting Officer authority is required for administrative closure."
            );
        }
        return actor;
    }

    private static BusinessStateConflictException closureNotAllowed() {
        return new BusinessStateConflictException(
                "LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED",
                "Administrative closure is not allowed for the Loan Account."
        );
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan Account closure evidence is inconsistent."
        );
    }
}
