package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcome;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcomeRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryRepaymentsService implements QueryRepaymentsUseCase {

    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accounts;
    private final RepaymentScheduleRepository schedules;
    private final RepaymentTransactionRepository transactions;
    private final RepaymentOperationOutcomeRepository outcomes;
    private final CurrentUserProvider currentUserProvider;

    public QueryRepaymentsService(
            LoanApplicationRepository applications,
            LoanAccountRepository accounts,
            RepaymentScheduleRepository schedules,
            RepaymentTransactionRepository transactions,
            RepaymentOperationOutcomeRepository outcomes,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.accounts = accounts;
        this.schedules = schedules;
        this.transactions = transactions;
        this.outcomes = outcomes;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult query(UUID loanApplicationId, int page, int size) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Repayment page arguments are invalid.");
        }
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireReadAuthority(actor);
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(() -> applicationNotFound(actor));
        authorize(actor, application);

        LoanAccount account = accounts.findByLoanApplicationId(application.id())
                .orElseThrow(QueryRepaymentsService::loanAccountNotFound);
        RepaymentSchedule schedule = schedules.findByLoanAccountId(account.id())
                .orElseThrow(() -> evidenceUnavailable(actor));
        validateTuple(actor, application, account, schedule);

        RepaymentTransactionRepository.Page selected = transactions
                .findPageByLoanAccountId(account.id(), page, size);
        List<Item> items = selected.transactions().stream()
                .map(transaction -> mapItem(actor, application, account, schedule, transaction))
                .toList();
        return new PageResult(selected.page(), selected.size(), selected.totalElements(),
                selected.totalPages(), items);
    }

    private Item mapItem(
            AuthenticatedUser actor,
            LoanApplication application,
            LoanAccount account,
            RepaymentSchedule schedule,
            RepaymentTransaction transaction
    ) {
        RepaymentOperationOutcome outcome = outcomes
                .findByRepaymentTransactionId(transaction.id())
                .orElseThrow(() -> evidenceUnavailable(actor));
        try {
            validateOperation(application, account, schedule, transaction, outcome);
            Map<UUID, RepaymentScheduleItem> scheduleItems = scheduleItems(schedule);
            List<RecordRepaymentUseCase.Allocation> allocations = transaction.allocations().stream()
                    .map(item -> mapAllocation(item, scheduleItems))
                    .toList();
            List<RecordRepaymentUseCase.InstallmentProgress> installments = outcome
                    .installments().stream()
                    .map(item -> mapProgress(item, scheduleItems))
                    .toList();
            BigDecimal principalAllocated = transaction.allocations().stream()
                    .filter(item -> item.component() == RepaymentAllocationComponent.PRINCIPAL)
                    .map(RepaymentAllocation::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (principalAllocated.compareTo(outcome.principalReleased()) != 0) {
                throw stateConflict();
            }
            return new Item(transaction.id(), outcome.receivedAmount(),
                    outcome.paymentValueDate(), outcome.recordedAt(), principalAllocated,
                    outcome.principalReleased(), outcome.accountStatus(),
                    mapBalance(outcome), allocations, installments);
        } catch (BusinessStateConflictException exception) {
            throw evidenceUnavailable(actor);
        } catch (RuntimeException exception) {
            throw evidenceUnavailable(actor);
        }
    }

    private static void validateOperation(
            LoanApplication application,
            LoanAccount account,
            RepaymentSchedule schedule,
            RepaymentTransaction transaction,
            RepaymentOperationOutcome outcome
    ) {
        if (!transaction.loanApplicationId().equals(application.id())
                || !transaction.loanAccountId().equals(account.id())
                || !transaction.repaymentScheduleId().equals(schedule.id())
                || !outcome.repaymentTransactionId().equals(transaction.id())
                || !outcome.loanApplicationId().equals(application.id())
                || !outcome.loanAccountId().equals(account.id())
                || !outcome.repaymentScheduleId().equals(schedule.id())
                || outcome.receivedAmount().compareTo(transaction.receivedAmount()) != 0
                || !outcome.paymentValueDate().equals(transaction.paymentValueDate())
                || !ServicingEvidenceTimestamp.same(
                outcome.recordedAt(), transaction.recordedAt())
                || outcome.installments().size() != schedule.items().size()) {
            throw stateConflict();
        }
        outcome.accountBalance().validateAgainst(account.approvedPrincipal(),
                account.totalInterest(), account.feeAmount());
        Map<UUID, RepaymentScheduleItem> scheduleItems = scheduleItems(schedule);
        Map<UUID, RepaymentInstallmentProgress> seen = new LinkedHashMap<>();
        for (RepaymentOperationOutcome.InstallmentOutcome item : outcome.installments()) {
            RepaymentInstallmentProgress progress = item.progress();
            RepaymentScheduleItem scheduleItem = scheduleItems.get(
                    progress.repaymentScheduleItemId()
            );
            if (scheduleItem == null
                    || !progress.repaymentScheduleId().equals(schedule.id())
                    || !progress.loanAccountId().equals(account.id())
                    || seen.put(progress.repaymentScheduleItemId(), progress) != null
                    || item.statusChanged()
                    == (item.previousStatus() == progress.status())) {
                throw stateConflict();
            }
            progress.validateAgainst(scheduleItem);
        }
        if (seen.size() != scheduleItems.size()) {
            throw stateConflict();
        }
    }

    private static RecordRepaymentUseCase.Allocation mapAllocation(
            RepaymentAllocation item,
            Map<UUID, RepaymentScheduleItem> scheduleItems
    ) {
        RepaymentScheduleItem scheduleItem = scheduleItems.get(item.repaymentScheduleItemId());
        if (scheduleItem == null) {
            throw stateConflict();
        }
        return new RecordRepaymentUseCase.Allocation(item.allocationSequence(),
                scheduleItem.id(), scheduleItem.installmentNumber(), item.component(), item.amount());
    }

    private static RecordRepaymentUseCase.InstallmentProgress mapProgress(
            RepaymentOperationOutcome.InstallmentOutcome outcome,
            Map<UUID, RepaymentScheduleItem> scheduleItems
    ) {
        RepaymentInstallmentProgress item = outcome.progress();
        RepaymentScheduleItem scheduleItem = scheduleItems.get(item.repaymentScheduleItemId());
        if (scheduleItem == null) {
            throw stateConflict();
        }
        return new RecordRepaymentUseCase.InstallmentProgress(
                item.repaymentScheduleItemId(), item.installmentNumber(), scheduleItem.dueDate(),
                item.principalPaid(), item.interestPaid(), item.feePaid(), item.totalPaid(),
                item.principalOutstanding(), item.interestOutstanding(), item.feeOutstanding(),
                item.totalOutstanding(), outcome.previousStatus(), item.status(),
                item.lastPaymentValueDate(), item.lastPaymentRecordedAt(),
                item.servicingEvaluationDate(), outcome.statusChanged());
    }

    private static RecordRepaymentUseCase.AccountBalance mapBalance(
            RepaymentOperationOutcome outcome
    ) {
        RepaymentBalance balance = outcome.accountBalance();
        return new RecordRepaymentUseCase.AccountBalance(balance.principalPaid(),
                balance.interestPaid(), balance.feePaid(), balance.totalPaid(),
                balance.principalOutstanding(), balance.interestOutstanding(),
                balance.feeOutstanding(), balance.totalOutstanding(),
                balance.lastPaymentValueDate(), balance.lastPaymentRecordedAt(),
                balance.servicingEvaluationDate(), outcome.accountStatus());
    }

    private static Map<UUID, RepaymentScheduleItem> scheduleItems(RepaymentSchedule schedule) {
        Map<UUID, RepaymentScheduleItem> items = new LinkedHashMap<>();
        schedule.items().forEach(item -> items.put(item.id(), item));
        return items;
    }

    private static void validateTuple(AuthenticatedUser actor, LoanApplication application,
                                      LoanAccount account, RepaymentSchedule schedule) {
        if (application.status() != LoanApplicationStatus.DISBURSED
                || !account.loanApplicationId().equals(application.id())
                || !account.customerId().equals(application.customerId())
                || !schedule.loanApplicationId().equals(application.id())
                || !schedule.loanAccountId().equals(account.id())
                || !schedule.loanContractId().equals(account.loanContractId())
                || schedule.scheduleType() != RepaymentScheduleType.FINAL
                || schedule.version() != RepaymentSchedule.INITIAL_FINAL_VERSION) {
            throw evidenceUnavailable(actor);
        }
    }

    private static void authorize(AuthenticatedUser actor, LoanApplication application) {
        if (actor.optionalCustomerId().isPresent()
                && !application.customerId().equals(actor.requireCustomerId())) {
            throw loanAccountNotFound();
        }
    }

    private static void requireReadAuthority(AuthenticatedUser actor) {
        if (actor.optionalCustomerId().isPresent()) {
            if (!actor.hasPermission("loan:read:own")) {
                throw accessDenied();
            }
        } else if (!actor.hasPermission("loan:read")) {
            throw accessDenied();
        }
    }

    private static RuntimeException applicationNotFound(AuthenticatedUser actor) {
        return actor.optionalCustomerId().isPresent() ? loanAccountNotFound()
                : new EntityNotFoundException("LOAN_APPLICATION_NOT_FOUND",
                "Loan application was not found.");
    }

    private static RuntimeException evidenceUnavailable(AuthenticatedUser actor) {
        return actor.optionalCustomerId().isPresent() ? loanAccountNotFound() : stateConflict();
    }

    private static EntityNotFoundException loanAccountNotFound() {
        return new EntityNotFoundException("LOAN_ACCOUNT_NOT_FOUND", "Loan Account was not found.");
    }

    private static AuthorizationException accessDenied() {
        return new AuthorizationException("LOAN_APPLICATION_ACCESS_DENIED",
                "Loan application access is denied.");
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT",
                "Repayment servicing evidence is inconsistent.");
    }
}
