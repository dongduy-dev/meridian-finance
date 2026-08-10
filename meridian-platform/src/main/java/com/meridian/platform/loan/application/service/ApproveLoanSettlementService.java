package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementRepository;
import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementSaveOutcome;
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
import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountServicingAction;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanAccountStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentServicingAction;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatusTransition;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.loan.domain.model.RepaymentTransactionType;
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

@Service
public class ApproveLoanSettlementService implements ApproveLoanSettlementUseCase {

    private final ApprovedLoanSettlementRepository settlements;
    private final RepaymentTransactionRepository transactions;
    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accounts;
    private final RepaymentScheduleRepository schedules;
    private final RepaymentInstallmentProgressRepository progressRepository;
    private final ManualDisbursementRepository disbursements;
    private final RepaymentInstallmentStatusTransitionRepository installmentHistory;
    private final LoanAccountStatusTransitionRepository accountHistory;
    private final RepaymentOperationOutcomeRepository outcomes;
    private final LoanProductRepaymentPolicyResolver repaymentPolicies;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final BusinessAuditEvidenceReader auditEvidence;
    private final Clock clock;
    private final DeterministicRepaymentAllocator allocator =
            new DeterministicRepaymentAllocator();
    private final RepaymentServicingCalculator servicing =
            new RepaymentServicingCalculator();

    public ApproveLoanSettlementService(
            ApprovedLoanSettlementRepository settlements,
            RepaymentTransactionRepository transactions,
            LoanApplicationRepository applications,
            LoanAccountRepository accounts,
            RepaymentScheduleRepository schedules,
            RepaymentInstallmentProgressRepository progressRepository,
            ManualDisbursementRepository disbursements,
            RepaymentInstallmentStatusTransitionRepository installmentHistory,
            LoanAccountStatusTransitionRepository accountHistory,
            RepaymentOperationOutcomeRepository outcomes,
            LoanProductRepaymentPolicyResolver repaymentPolicies,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            BusinessAuditEvidenceReader auditEvidence,
            Clock clock
    ) {
        this.settlements = settlements;
        this.transactions = transactions;
        this.applications = applications;
        this.accounts = accounts;
        this.schedules = schedules;
        this.progressRepository = progressRepository;
        this.disbursements = disbursements;
        this.installmentHistory = installmentHistory;
        this.accountHistory = accountHistory;
        this.outcomes = outcomes;
        this.repaymentPolicies = repaymentPolicies;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.auditEvidence = auditEvidence;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result approve(Command command) {
        AuthenticatedUser actor = requireApprover(currentUsers.currentUser());
        Instant approvedInstant = clock.instant();
        LocalDateTime approvedAt = ServicingEvidenceTimestamp.normalizeForPersistence(
                LocalDateTime.ofInstant(approvedInstant, ZoneOffset.UTC)
        );
        LocalDate evaluationDate = LocalDate.ofInstant(
                approvedInstant,
                ZoneOffset.UTC
        );

        settlements.acquireApprovalRequestLock(command.requestId());
        ApprovedLoanSettlement existing = settlements
                .findByRequestId(command.requestId())
                .orElse(null);
        if (existing != null) {
            validateIdentity(existing, transaction(existing), command, actor);
        } else {
            rejectConflictingTransactionRequest(command.requestId());
        }

        applications.acquireWorkflowLock(command.loanApplicationId());
        existing = settlements.findByRequestId(command.requestId()).orElse(null);
        if (existing != null) {
            return replay(existing, command, actor);
        }
        rejectConflictingTransactionRequest(command.requestId());

        LoanApplication application = applications
                .findByIdForUpdate(command.loanApplicationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan Application was not found."
                ));
        validateApplication(application);
        LoanAccount account = accounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
        validateOpenAccount(application, account);
        RepaymentSchedule schedule = schedules
                .findByLoanAccountIdForUpdate(account.id())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
        validateSchedule(application, account, schedule);
        List<RepaymentInstallmentProgress> current =
                progressRepository.findByLoanAccountIdForUpdate(account.id());
        if (current.size() != schedule.items().size()) {
            throw stateConflict();
        }
        ManualDisbursement disbursement = disbursements
                .findByLoanAccountId(account.id())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
        validateValueDate(
                command.paymentValueDate(),
                disbursement.valueDate(),
                evaluationDate
        );
        if (command.expectedSettlementAmount().compareTo(
                account.repaymentBalance().totalOutstanding()) != 0) {
            throw new BusinessRuleViolationException(
                    "SETTLEMENT_AMOUNT_INVALID",
                    "Expected settlement amount must equal the current contractual outstanding."
            );
        }

        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = allocator.allocate(
                transactionId,
                command.expectedSettlementAmount(),
                schedule,
                current
        );
        RepaymentServicingCalculator.Result calculated = servicing.apply(
                schedule,
                current,
                allocations,
                command.paymentValueDate(),
                approvedAt,
                evaluationDate
        );
        validateCalculatedSettlement(calculated, schedule);
        RepaymentTransaction transaction = RepaymentTransaction.approvedSettlement(
                transactionId,
                application.id(),
                account.id(),
                schedule.id(),
                command.requestId(),
                command.externalPaymentReference(),
                command.expectedSettlementAmount(),
                command.paymentValueDate(),
                disbursement.valueDate(),
                evaluationDate,
                actor.userId(),
                approvedAt,
                allocations
        );
        RepaymentTransactionSaveOutcome transactionSave = transactions.save(transaction);
        if (!(transactionSave instanceof RepaymentTransactionSaveOutcome.Inserted)) {
            return resolveTransactionConflict(transactionSave, command, actor);
        }

        progressRepository.saveAll(calculated.progress());
        appendInstallmentHistory(
                current,
                calculated,
                transactionId,
                actor.userId(),
                approvedAt,
                evaluationDate
        );
        LoanAccount settledAccount = account.withServicingState(
                calculated.balance(),
                LoanAccountStatus.SETTLED,
                approvedAt
        );
        accounts.updateServicingState(settledAccount);
        accountHistory.save(new LoanAccountStatusTransition(
                UUID.randomUUID(),
                account.id(),
                accountHistory.nextSequenceNumber(account.id()),
                transactionId,
                account.status(),
                LoanAccountStatus.SETTLED,
                LoanAccountServicingAction.APPROVED_SETTLEMENT,
                ActorType.USER,
                actor.userId(),
                evaluationDate,
                approvedAt
        ));

        LoanProductRepaymentPolicy policy = repaymentPolicies.resolve(
                application.productCode()
        );
        BigDecimal principalReleased = policy.releasePrincipal(
                new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                        application,
                        settledAccount,
                        transactionId,
                        allocations,
                        approvedAt
                )
        );
        RepaymentOperationOutcome outcome = RepaymentOperationOutcome.captured(
                transactionId,
                application.id(),
                account.id(),
                schedule.id(),
                command.expectedSettlementAmount(),
                command.paymentValueDate(),
                approvedAt,
                calculated.balance(),
                LoanAccountStatus.SETTLED,
                true,
                principalReleased,
                current,
                calculated.progress(),
                calculated.installmentStatusChanges()
        );
        outcomes.save(outcome);
        ApprovedLoanSettlement settlement = ApprovedLoanSettlement.from(
                UUID.randomUUID(),
                transaction
        );
        ApprovedLoanSettlementSaveOutcome settlementSave = settlements.save(settlement);
        if (!(settlementSave instanceof ApprovedLoanSettlementSaveOutcome.Inserted)) {
            throw stateConflict();
        }
        publishAudit(settlement, transactionId, account.id(), actor.userId(), approvedAt);
        return toResult(transaction, outcome, false);
    }

    private Result replay(
            ApprovedLoanSettlement settlement,
            Command command,
            AuthenticatedUser actor
    ) {
        RepaymentTransaction transaction = transaction(settlement);
        validateIdentity(settlement, transaction, command, actor);
        LoanApplication application = applications
                .findByIdForUpdate(transaction.loanApplicationId())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
        validateApplication(application);
        LoanAccount account = accounts
                .findByLoanApplicationIdForUpdate(application.id())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
        validateAccountIdentity(application, account);
        if (account.status() != LoanAccountStatus.SETTLED
                && account.status() != LoanAccountStatus.CLOSED) {
            throw stateConflict();
        }
        RepaymentSchedule schedule = schedules
                .findByLoanAccountIdForUpdate(account.id())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
        validateSchedule(application, account, schedule);
        RepaymentOperationOutcome outcome = outcomes
                .findByRepaymentTransactionId(transaction.id())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
        validateOutcome(settlement, transaction, outcome);
        repaymentPolicies.resolve(application.productCode()).validateCompletedRelease(
                new LoanProductRepaymentPolicy.CompletedReleaseCommand(
                        application,
                        account,
                        transaction.id(),
                        outcome.principalReleased()
                )
        );
        validateReplayEvidence(settlement, transaction, outcome);
        return toResult(transaction, outcome, true);
    }

    private Result resolveTransactionConflict(
            RepaymentTransactionSaveOutcome saveOutcome,
            Command command,
            AuthenticatedUser actor
    ) {
        if (saveOutcome instanceof RepaymentTransactionSaveOutcome.ExistingRequest existing) {
            ApprovedLoanSettlement settlement = settlements
                    .findByRequestId(command.requestId())
                    .orElse(null);
            if (settlement == null) {
                throw idempotencyReused();
            }
            validateIdentity(settlement, existing.transaction(), command, actor);
            return replay(settlement, command, actor);
        }
        if (saveOutcome instanceof RepaymentTransactionSaveOutcome.Conflict conflict
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
            LocalDateTime approvedAt,
            LocalDate evaluationDate
    ) {
        Map<UUID, RepaymentInstallmentProgress> before = new LinkedHashMap<>();
        current.forEach(item -> before.put(item.repaymentScheduleItemId(), item));
        for (RepaymentInstallmentProgress after : calculated.progress()) {
            RepaymentInstallmentProgress prior = before.get(
                    after.repaymentScheduleItemId()
            );
            if (prior == null) {
                throw stateConflict();
            }
            if (prior.status() != after.status()) {
                installmentHistory.save(new RepaymentInstallmentStatusTransition(
                        UUID.randomUUID(),
                        after.repaymentScheduleItemId(),
                        installmentHistory.nextSequenceNumber(
                                after.repaymentScheduleItemId()
                        ),
                        transactionId,
                        prior.status(),
                        after.status(),
                        RepaymentInstallmentServicingAction.APPROVED_SETTLEMENT,
                        ActorType.USER,
                        actorId,
                        evaluationDate,
                        approvedAt
                ));
            }
        }
    }

    private void validateReplayEvidence(
            ApprovedLoanSettlement settlement,
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome
    ) {
        long settlementAudit = auditEvidence.countMatchingOperation(
                transaction.id(),
                BusinessAuditAction.LOAN_SETTLEMENT_APPROVED,
                BusinessAuditEntityType.LOAN_SETTLEMENT,
                settlement.id()
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
            if (transition.action()
                    != RepaymentInstallmentServicingAction.APPROVED_SETTLEMENT
                    || transition.actorType() != ActorType.USER
                    || !transaction.recordedByUserId().equals(transition.actorUserId())
                    || !transaction.recordedAt().equals(transition.occurredAt())
                    || transitionsByItem.put(
                    transition.repaymentScheduleItemId(), transition) != null) {
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
        List<LoanAccountStatusTransition> accountTransitions = accountHistory
                .findByLoanAccountId(transaction.loanAccountId()).stream()
                .filter(item -> transaction.id().equals(item.operationId()))
                .toList();
        if (settlementAudit != 1
                || accountAudit != 1
                || !transitionsByItem.isEmpty()
                || accountTransitions.size() != 1) {
            throw stateConflict();
        }
        LoanAccountStatusTransition accountTransition = accountTransitions.getFirst();
        if ((accountTransition.fromStatus() != LoanAccountStatus.ACTIVE
                && accountTransition.fromStatus() != LoanAccountStatus.OVERDUE)
                || accountTransition.toStatus() != LoanAccountStatus.SETTLED
                || accountTransition.action()
                != LoanAccountServicingAction.APPROVED_SETTLEMENT
                || accountTransition.actorType() != ActorType.USER
                || !transaction.recordedByUserId().equals(
                accountTransition.actorUserId())
                || !transaction.recordedAt().equals(accountTransition.occurredAt())) {
            throw stateConflict();
        }
    }

    private void publishAudit(
            ApprovedLoanSettlement settlement,
            UUID transactionId,
            UUID accountId,
            UUID actorId,
            LocalDateTime approvedAt
    ) {
        ArrayList<BusinessAuditEntry> entries = new ArrayList<>();
        entries.add(BusinessAuditEntry.of(
                BusinessAuditAction.LOAN_SETTLEMENT_APPROVED,
                BusinessAuditEntityType.LOAN_SETTLEMENT,
                settlement.id()
        ));
        entries.add(BusinessAuditEntry.of(
                BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED,
                BusinessAuditEntityType.LOAN_ACCOUNT,
                accountId
        ));
        auditPublisher.publish(new BusinessAuditEvent(
                BusinessOperationContext.user(transactionId, actorId, approvedAt),
                entries
        ));
    }

    private RepaymentTransaction transaction(ApprovedLoanSettlement settlement) {
        return transactions.findById(settlement.repaymentTransactionId())
                .orElseThrow(ApproveLoanSettlementService::stateConflict);
    }

    private void rejectConflictingTransactionRequest(UUID requestId) {
        if (transactions.findByRequestId(requestId).isPresent()) {
            throw idempotencyReused();
        }
    }

    private static void validateIdentity(
            ApprovedLoanSettlement settlement,
            RepaymentTransaction transaction,
            Command command,
            AuthenticatedUser actor
    ) {
        if (!settlement.requestId().equals(command.requestId())
                || !settlement.loanApplicationId().equals(command.loanApplicationId())
                || settlement.settlementAmount().compareTo(
                command.expectedSettlementAmount()) != 0
                || !settlement.approvedByUserId().equals(actor.userId())
                || !settlement.repaymentTransactionId().equals(transaction.id())
                || transaction.transactionType()
                != RepaymentTransactionType.APPROVED_SETTLEMENT
                || !transaction.requestId().equals(command.requestId())
                || !transaction.loanApplicationId().equals(command.loanApplicationId())
                || !transaction.loanAccountId().equals(settlement.loanAccountId())
                || transaction.receivedAmount().compareTo(
                command.expectedSettlementAmount()) != 0
                || !transaction.paymentValueDate().equals(command.paymentValueDate())
                || !transaction.externalPaymentReference().equals(
                command.externalPaymentReference())
                || !transaction.recordedByUserId().equals(actor.userId())
                || !transaction.recordedAt().equals(settlement.approvedAt())) {
            throw idempotencyReused();
        }
    }

    private static void validateOutcome(
            ApprovedLoanSettlement settlement,
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome
    ) {
        BigDecimal principal = transaction.allocations().stream()
                .filter(item -> item.component()
                        == RepaymentAllocationComponent.PRINCIPAL)
                .map(RepaymentAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!transaction.id().equals(outcome.repaymentTransactionId())
                || !transaction.loanApplicationId().equals(outcome.loanApplicationId())
                || !transaction.loanAccountId().equals(outcome.loanAccountId())
                || !transaction.repaymentScheduleId().equals(
                outcome.repaymentScheduleId())
                || transaction.receivedAmount().compareTo(outcome.receivedAmount()) != 0
                || !transaction.paymentValueDate().equals(outcome.paymentValueDate())
                || !ServicingEvidenceTimestamp.same(
                transaction.recordedAt(), outcome.recordedAt())
                || settlement.settlementAmount().compareTo(outcome.receivedAmount()) != 0
                || principal.compareTo(outcome.principalReleased()) != 0
                || outcome.accountStatus() != LoanAccountStatus.SETTLED
                || !outcome.accountStatusChanged()
                || outcome.accountBalance().totalOutstanding().signum() != 0) {
            throw stateConflict();
        }
    }

    private static void validateApplication(LoanApplication application) {
        if (application.status() != LoanApplicationStatus.DISBURSED
                || application.productCode() != ProductCode.SALARY_ADVANCE) {
            throw settlementNotAllowed();
        }
    }

    private static void validateOpenAccount(
            LoanApplication application,
            LoanAccount account
    ) {
        validateAccountIdentity(application, account);
        if ((account.status() != LoanAccountStatus.ACTIVE
                && account.status() != LoanAccountStatus.OVERDUE)
                || account.repaymentBalance().totalOutstanding().signum() <= 0) {
            throw settlementNotAllowed();
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

    private static void validateCalculatedSettlement(
            RepaymentServicingCalculator.Result calculated,
            RepaymentSchedule schedule
    ) {
        boolean incomplete = calculated.progress().size() != schedule.items().size()
                || calculated.progress().stream().anyMatch(item ->
                item.status() != RepaymentInstallmentStatus.PAID
                        || item.totalOutstanding().signum() != 0);
        if (calculated.accountStatus() != LoanAccountStatus.SETTLED
                || calculated.balance().totalOutstanding().signum() != 0
                || incomplete) {
            throw stateConflict();
        }
    }

    private static void validateValueDate(
            LocalDate paymentValueDate,
            LocalDate disbursementValueDate,
            LocalDate evaluationDate
    ) {
        try {
            RepaymentTransaction.validateValueDate(
                    paymentValueDate,
                    disbursementValueDate,
                    evaluationDate
            );
        } catch (BusinessRuleViolationException exception) {
            throw new BusinessRuleViolationException(
                    "SETTLEMENT_VALUE_DATE_INVALID",
                    "Settlement payment value date is outside the permitted UTC date range."
            );
        }
    }

    private static AuthenticatedUser requireApprover(AuthenticatedUser actor) {
        if (actor == null
                || !"STAFF".equals(actor.userType())
                || !actor.roles().contains("APPROVER")) {
            throw new AuthorizationException(
                    "APPROVER_ROLE_REQUIRED",
                    "Approver authority is required for Administrative Full-Balance Settlement."
            );
        }
        return actor;
    }

    private static Result toResult(
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome,
            boolean replay
    ) {
        RepaymentBalance balance = outcome.accountBalance();
        return new Result(
                outcome.loanApplicationId(),
                outcome.loanAccountId(),
                transaction.id(),
                outcome.repaymentScheduleId(),
                outcome.receivedAmount(),
                outcome.paymentValueDate(),
                outcome.recordedAt(),
                outcome.principalReleased(),
                new AccountBalance(
                        balance.principalPaid(),
                        balance.interestPaid(),
                        balance.feePaid(),
                        balance.totalPaid(),
                        balance.principalOutstanding(),
                        balance.interestOutstanding(),
                        balance.feeOutstanding(),
                        balance.totalOutstanding(),
                        balance.lastPaymentValueDate(),
                        balance.lastPaymentRecordedAt(),
                        balance.servicingEvaluationDate(),
                        outcome.accountStatus()
                ),
                replay
        );
    }

    private static BusinessStateConflictException settlementNotAllowed() {
        return new BusinessStateConflictException(
                "SETTLEMENT_NOT_ALLOWED",
                "Administrative settlement is not allowed for the Loan Account."
        );
    }

    private static BusinessStateConflictException idempotencyReused() {
        return new BusinessStateConflictException(
                "IDEMPOTENCY_KEY_REUSED",
                "Settlement request identifier was reused for a different operation."
        );
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan settlement servicing evidence is inconsistent."
        );
    }
}
