package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffLoanAccountServicingProvenanceDto;
import com.meridian.platform.loan.application.dto.StaffLoanAccountServicingProvenanceDto.*;
import com.meridian.platform.loan.application.mapper.LoanRepaymentApiMapper;
import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanAccountServicingProvenanceUseCase;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QueryStaffLoanAccountServicingProvenanceService
        implements QueryStaffLoanAccountServicingProvenanceUseCase {
    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accounts;
    private final ManualDisbursementRepository disbursements;
    private final LoanAccountStatusTransitionRepository transitions;
    private final RepaymentTransactionRepository transactions;
    private final ApprovedLoanSettlementRepository settlements;
    private final LoanAccountClosureRepository closures;
    private final StaffActorDirectoryPort staffActors;
    private final QueryRepaymentsUseCase repaymentHistory;
    private final LoanRepaymentApiMapper repaymentMapper;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffLoanAccountServicingProvenanceService(
            LoanApplicationRepository applications, LoanAccountRepository accounts,
            ManualDisbursementRepository disbursements,
            LoanAccountStatusTransitionRepository transitions,
            RepaymentTransactionRepository transactions,
            ApprovedLoanSettlementRepository settlements,
            LoanAccountClosureRepository closures,
            StaffActorDirectoryPort staffActors, QueryRepaymentsUseCase repaymentHistory,
            LoanRepaymentApiMapper repaymentMapper, CurrentUserProvider currentUserProvider) {
        this.applications = applications;
        this.accounts = accounts;
        this.disbursements = disbursements;
        this.transitions = transitions;
        this.transactions = transactions;
        this.settlements = settlements;
        this.closures = closures;
        this.staffActors = staffActors;
        this.repaymentHistory = repaymentHistory;
        this.repaymentMapper = repaymentMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffLoanAccountServicingProvenanceDto query(UUID loanApplicationId, int page, int size) {
        Objects.requireNonNull(loanApplicationId);
        requireAuthority(currentUserProvider.currentUser());
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Servicing provenance page arguments are invalid.");
        }
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException("LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."));
        LoanAccount account = accounts.findByLoanApplicationId(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException("LOAN_ACCOUNT_NOT_FOUND",
                        "Loan Account was not found."));
        if (application.status() != LoanApplicationStatus.DISBURSED
                || !account.loanApplicationId().equals(application.id())
                || !account.customerId().equals(application.customerId())) throw conflict();

        ManualDisbursement origin = disbursements.findByLoanAccountId(account.id())
                .orElseThrow(QueryStaffLoanAccountServicingProvenanceService::conflict);
        if (!origin.loanApplicationId().equals(application.id())
                || !origin.loanAccountId().equals(account.id())
                || !origin.loanContractId().equals(account.loanContractId())
                || !ServicingEvidenceTimestamp.same(origin.confirmedAt(), account.activatedAt())) {
            throw conflict();
        }

        List<LoanAccountStatusTransition> history;
        try {
            history = transitions.findByLoanAccountId(account.id());
        } catch (IllegalArgumentException | NullPointerException invalidPersistedTransition) {
            throw conflict();
        }
        validateHistory(account, origin, history);
        ApprovedLoanSettlement settlement = settlements.findByLoanAccountId(account.id()).orElse(null);
        LoanAccountClosure closure = closures.findByLoanAccountId(account.id()).orElse(null);
        List<RepaymentTransaction> allTransactions;
        try {
            allTransactions = transactions.findByLoanAccountId(account.id());
        } catch (IllegalArgumentException | NullPointerException invalidPersistedTransaction) {
            throw conflict();
        }
        validateTransactions(application, account, allTransactions, history);
        validateSettlement(application, account, history, allTransactions, settlement);
        validateClosure(application, account, history, closure);

        // Both reads use this repeatable-read transaction. The response binds each financial
        // item to its transaction actor by UUID before it reaches the browser.
        var financial = repaymentMapper.toDto(repaymentHistory.query(loanApplicationId, page, size));
        RepaymentTransactionRepository.Page selected;
        try {
            selected = transactions.findPageByLoanAccountId(account.id(), page, size);
        } catch (IllegalArgumentException | NullPointerException invalidPersistedTransaction) {
            throw conflict();
        }
        if (financial.page() != selected.page() || financial.size() != selected.size()
                || financial.totalElements() != selected.totalElements()
                || financial.totalPages() != selected.totalPages()
                || financial.items().size() != selected.transactions().size()) throw conflict();

        Set<UUID> actorIds = new LinkedHashSet<>();
        actorIds.add(origin.confirmedByUserId());
        for (var event : history) if (event.actorType() == ActorType.USER) actorIds.add(event.actorUserId());
        for (var transaction : selected.transactions()) actorIds.add(transaction.recordedByUserId());
        if (settlement != null) actorIds.add(settlement.approvedByUserId());
        if (closure != null) actorIds.add(closure.closedByUserId());
        Map<UUID, StaffActorSummary> actors = staffActors.findByUserIds(actorIds);
        if (actors == null) throw conflict();
        for (UUID id : actorIds) staff(actors, id);

        List<RepaymentItem> repaymentItems = new ArrayList<>();
        for (int index = 0; index < selected.transactions().size(); index++) {
            var transaction = selected.transactions().get(index);
            var item = financial.items().get(index);
            if (!transaction.loanApplicationId().equals(application.id())
                    || !transaction.loanAccountId().equals(account.id())
                    || !item.repaymentTransactionId().equals(transaction.id())
                    || !ServicingEvidenceTimestamp.same(item.recordedAt(), transaction.recordedAt())) {
                throw conflict();
            }
            repaymentItems.add(new RepaymentItem(item, transaction.transactionType().name(),
                    user(actors, transaction.recordedByUserId())));
        }
        List<StatusEvent> statusItems = history.stream().map(event -> new StatusEvent(
                event.sequenceNumber(), event.action().name(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus().name(), event.actorType() == ActorType.SYSTEM
                        ? new ServicingActor("SYSTEM", null) : user(actors, event.actorUserId()),
                event.servicingEvaluationDate(), event.occurredAt())).toList();
        return new StaffLoanAccountServicingProvenanceDto(application.id(), account.id(),
                new ActorEvent(user(actors, origin.confirmedByUserId()), origin.confirmedAt()),
                new RepaymentPage(financial.page(), financial.size(), financial.totalElements(),
                        financial.totalPages(), repaymentItems), statusItems,
                settlement == null ? null : new ActorEvent(user(actors, settlement.approvedByUserId()),
                        settlement.approvedAt()),
                closure == null ? null : new ActorEvent(user(actors, closure.closedByUserId()),
                        closure.closedAt()));
    }

    private static void validateHistory(LoanAccount account, ManualDisbursement origin,
                                        List<LoanAccountStatusTransition> history) {
        if (history.isEmpty()) throw conflict();
        LoanAccountStatus previous = null;
        for (int index = 0; index < history.size(); index++) {
            var event = history.get(index);
            if (!account.id().equals(event.loanAccountId())
                    || event.sequenceNumber() != index + 1
                    || event.fromStatus() != previous
                    || event.actorType() == ActorType.USER && event.actorUserId() == null
                    || event.actorType() == ActorType.SYSTEM && event.actorUserId() != null) throw conflict();
            if (index == 0 && (event.action() != LoanAccountServicingAction.ACTIVATION_INITIALIZED
                    || event.actorType() != ActorType.USER
                    || !origin.confirmedByUserId().equals(event.actorUserId())
                    || !ServicingEvidenceTimestamp.same(origin.confirmedAt(), event.occurredAt())
                    || event.toStatus() != LoanAccountStatus.ACTIVE
                    || !event.servicingEvaluationDate().equals(account.activatedAt().toLocalDate()))) {
                throw conflict();
            }
            if (index > 0 && event.action() == LoanAccountServicingAction.ACTIVATION_INITIALIZED) throw conflict();
            if (event.action() == LoanAccountServicingAction.OVERDUE_EVALUATED
                    && event.actorType() != ActorType.SYSTEM) throw conflict();
            if ((event.action() == LoanAccountServicingAction.REPAYMENT_RECORDED
                    || event.action() == LoanAccountServicingAction.APPROVED_SETTLEMENT
                    || event.action() == LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE)
                    && event.actorType() != ActorType.USER) throw conflict();
            previous = event.toStatus();
        }
        if (previous != account.status()) throw conflict();
        if (account.status() == LoanAccountStatus.SETTLED
                || account.status() == LoanAccountStatus.CLOSED) {
            var settled = history.stream().filter(event -> event.toStatus()
                    == LoanAccountStatus.SETTLED).toList();
            if (settled.size() != 1 || settled.getFirst().action()
                    != LoanAccountServicingAction.REPAYMENT_RECORDED
                    && settled.getFirst().action()
                    != LoanAccountServicingAction.APPROVED_SETTLEMENT) throw conflict();
        }
    }

    private static void validateTransactions(LoanApplication application, LoanAccount account,
                                             List<RepaymentTransaction> transactions,
                                             List<LoanAccountStatusTransition> history) {
        Map<UUID, RepaymentTransaction> byId = new java.util.HashMap<>();
        for (var payment : transactions) {
            if (!payment.loanApplicationId().equals(application.id())
                    || !payment.loanAccountId().equals(account.id())
                    || byId.put(payment.id(), payment) != null) throw conflict();
        }
        for (var event : history) {
            if (event.action() == LoanAccountServicingAction.REPAYMENT_RECORDED
                    || event.action() == LoanAccountServicingAction.APPROVED_SETTLEMENT) {
                var payment = byId.get(event.operationId());
                if (payment == null
                        || payment.transactionType() != (event.action()
                        == LoanAccountServicingAction.APPROVED_SETTLEMENT
                        ? RepaymentTransactionType.APPROVED_SETTLEMENT
                        : RepaymentTransactionType.REPAYMENT)
                        || !payment.recordedByUserId().equals(event.actorUserId())
                        || !ServicingEvidenceTimestamp.same(payment.recordedAt(), event.occurredAt())) {
                    throw conflict();
                }
            }
        }
    }

    private static void validateSettlement(LoanApplication application, LoanAccount account,
                                           List<LoanAccountStatusTransition> history,
                                           List<RepaymentTransaction> transactions,
                                           ApprovedLoanSettlement settlement) {
        var events = history.stream().filter(item -> item.action()
                == LoanAccountServicingAction.APPROVED_SETTLEMENT).toList();
        var payments = transactions.stream().filter(item -> item.transactionType()
                == RepaymentTransactionType.APPROVED_SETTLEMENT).toList();
        if (settlement == null) {
            if (!events.isEmpty() || !payments.isEmpty()) throw conflict();
            return; // Contractual payoff has no ApprovedLoanSettlement.
        }
        if (events.size() != 1 || payments.size() != 1
                || account.status() != LoanAccountStatus.SETTLED && account.status() != LoanAccountStatus.CLOSED
                || !settlement.loanApplicationId().equals(application.id())
                || !settlement.loanAccountId().equals(account.id())) throw conflict();
        var payment = payments.getFirst();
        var event = events.getFirst();
        if (!settlement.repaymentTransactionId().equals(payment.id())
                || !payment.loanApplicationId().equals(application.id())
                || !payment.loanAccountId().equals(account.id())
                || !payment.requestId().equals(settlement.requestId())
                || payment.receivedAmount().compareTo(settlement.settlementAmount()) != 0
                || !payment.recordedByUserId().equals(settlement.approvedByUserId())
                || !ServicingEvidenceTimestamp.same(payment.recordedAt(), settlement.approvedAt())
                || !event.operationId().equals(payment.id())
                || event.actorType() != ActorType.USER
                || !event.actorUserId().equals(settlement.approvedByUserId())
                || !ServicingEvidenceTimestamp.same(event.occurredAt(), settlement.approvedAt())
                || event.toStatus() != LoanAccountStatus.SETTLED) throw conflict();
    }

    private static void validateClosure(LoanApplication application, LoanAccount account,
                                        List<LoanAccountStatusTransition> history,
                                        LoanAccountClosure closure) {
        var events = history.stream().filter(item -> item.action()
                == LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE).toList();
        if (closure == null) {
            if (account.status() == LoanAccountStatus.CLOSED || !events.isEmpty()) throw conflict();
            return;
        }
        if (account.status() != LoanAccountStatus.CLOSED || events.size() != 1
                || !closure.loanApplicationId().equals(application.id())
                || !closure.loanAccountId().equals(account.id())) throw conflict();
        var event = events.getFirst();
        if (event != history.getLast()
                || !event.operationId().equals(closure.id())
                || event.actorType() != ActorType.USER
                || !event.actorUserId().equals(closure.closedByUserId())
                || !ServicingEvidenceTimestamp.same(event.occurredAt(), closure.closedAt())
                || event.fromStatus() != LoanAccountStatus.SETTLED
                || event.toStatus() != LoanAccountStatus.CLOSED) throw conflict();
    }

    private static ServicingActor user(Map<UUID, StaffActorSummary> actors, UUID id) {
        return new ServicingActor("USER", staff(actors, id));
    }

    private static StaffActor staff(Map<UUID, StaffActorSummary> actors, UUID id) {
        var actor = id == null ? null : actors.get(id);
        if (actor == null || !id.equals(actor.userId()) || actor.displayName() == null
                || actor.displayName().isBlank() || actor.email() == null
                || actor.email().isBlank()) throw conflict();
        return new StaffActor(actor.userId(), actor.displayName(), actor.email());
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (actor == null || !"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent() || !actor.hasPermission("loan:read")) {
            throw new AuthorizationException("LOAN_SERVICING_PROVENANCE_ACCESS_DENIED",
                    "Staff LoanAccount servicing provenance access is denied.");
        }
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT",
                "LoanAccount servicing provenance evidence is inconsistent.");
    }
}
