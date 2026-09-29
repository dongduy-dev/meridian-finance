package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.RepaymentHistoryPageDto;
import com.meridian.platform.loan.application.mapper.LoanRepaymentApiMapper;
import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryStaffLoanAccountServicingProvenanceServiceTest {
    private static final UUID APP = UUID.randomUUID(), ACCOUNT = UUID.randomUUID(),
            CONTRACT = UUID.randomUUID(), CUSTOMER = UUID.randomUUID(), USER = UUID.randomUUID();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 20, 10, 0);
    @Mock LoanApplicationRepository applications;
    @Mock LoanAccountRepository accounts;
    @Mock ManualDisbursementRepository disbursements;
    @Mock LoanAccountStatusTransitionRepository transitions;
    @Mock RepaymentTransactionRepository transactions;
    @Mock ApprovedLoanSettlementRepository settlements;
    @Mock LoanAccountClosureRepository closures;
    @Mock StaffActorDirectoryPort staffActors;
    @Mock QueryRepaymentsUseCase repaymentHistory;
    @Mock LoanRepaymentApiMapper mapper;
    @Mock CurrentUserProvider users;
    @Mock LoanApplication application;
    @Mock LoanAccount account;
    @Mock ManualDisbursement origin;
    @Mock RepaymentTransaction payment;
    @Mock ApprovedLoanSettlement settlement;
    @Mock LoanAccountClosure closure;
    @Mock RepaymentHistoryPageDto.ItemDto financialItem;
    private QueryStaffLoanAccountServicingProvenanceService service;

    @BeforeEach void setUp() {
        service = new QueryStaffLoanAccountServicingProvenanceService(applications, accounts,
                disbursements, transitions, transactions, settlements, closures, staffActors,
                repaymentHistory, mapper, users);
        when(users.currentUser()).thenReturn(new AuthenticatedUser(USER, "user@meridian.test",
                "STAFF", null, Set.of(), Set.of("loan:read")));
        when(applications.findById(APP)).thenReturn(Optional.of(application));
        when(application.id()).thenReturn(APP);
        when(application.customerId()).thenReturn(CUSTOMER);
        when(application.status()).thenReturn(LoanApplicationStatus.DISBURSED);
        when(accounts.findByLoanApplicationId(APP)).thenReturn(Optional.of(account));
        when(account.id()).thenReturn(ACCOUNT);
        when(account.loanApplicationId()).thenReturn(APP);
        when(account.loanContractId()).thenReturn(CONTRACT);
        when(account.customerId()).thenReturn(CUSTOMER);
        when(account.activatedAt()).thenReturn(AT);
        when(account.status()).thenReturn(LoanAccountStatus.ACTIVE);
        when(disbursements.findByLoanAccountId(ACCOUNT)).thenReturn(Optional.of(origin));
        when(origin.loanApplicationId()).thenReturn(APP);
        when(origin.loanAccountId()).thenReturn(ACCOUNT);
        when(origin.loanContractId()).thenReturn(CONTRACT);
        when(origin.confirmedByUserId()).thenReturn(USER);
        when(origin.confirmedAt()).thenReturn(AT);
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT)));
        when(transactions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of());
        when(repaymentHistory.query(APP, 0, 20)).thenReturn(
                new QueryRepaymentsUseCase.PageResult(0, 20, 0, 0, List.of()));
        when(mapper.toDto(any(QueryRepaymentsUseCase.PageResult.class))).thenReturn(
                new RepaymentHistoryPageDto(0, 20, 0, 0, List.of()));
        when(transactions.findPageByLoanAccountId(ACCOUNT, 0, 20)).thenReturn(
                new RepaymentTransactionRepository.Page(0, 20, 0, 0, List.of()));
        when(staffActors.findByUserIds(any())).thenReturn(Map.of(USER,
                new StaffActorSummary(USER, "Mina Accounting", "mina@meridian.test")));
    }

    @Test void originUsesExactDisbursementAndOneBatchIdentityLookup() {
        var result = service.query(APP, 0, 20);
        assertEquals("Mina Accounting", result.originatingDisbursement().actor().staff().displayName());
        assertEquals(AT, result.originatingDisbursement().at());
        assertEquals("USER", result.statusHistory().getFirst().actor().type());
        verify(staffActors, times(1)).findByUserIds(Set.of(USER));
    }

    @Test void systemEventDoesNotCreateAStaffActor() {
        when(account.status()).thenReturn(LoanAccountStatus.OVERDUE);
        var system = new LoanAccountStatusTransition(UUID.randomUUID(), ACCOUNT, 2,
                UUID.randomUUID(), LoanAccountStatus.ACTIVE, LoanAccountStatus.OVERDUE,
                LoanAccountServicingAction.OVERDUE_EVALUATED, ActorType.SYSTEM, null,
                AT.toLocalDate(), AT.plusDays(1));
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT), system));
        var result = service.query(APP, 0, 20);
        assertEquals("SYSTEM", result.statusHistory().get(1).actor().type());
        assertNull(result.statusHistory().get(1).actor().staff());
        verify(staffActors).findByUserIds(Set.of(USER));
    }

    @Test void missingOrContradictoryOriginFailsClosed() {
        when(disbursements.findByLoanAccountId(ACCOUNT)).thenReturn(Optional.empty());
        conflict();
        when(disbursements.findByLoanAccountId(ACCOUNT)).thenReturn(Optional.of(origin));
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(UUID.randomUUID(), AT)));
        conflict();
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT.plusSeconds(1))));
        conflict();
    }

    @Test void gapsWrongAccountAndUnresolvedActorFailClosed() {
        var gap = new LoanAccountStatusTransition(UUID.randomUUID(), ACCOUNT, 3,
                UUID.randomUUID(), LoanAccountStatus.ACTIVE, LoanAccountStatus.OVERDUE,
                LoanAccountServicingAction.OVERDUE_EVALUATED, ActorType.SYSTEM, null,
                AT.toLocalDate(), AT.plusDays(1));
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT), gap));
        conflict();
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(
                new LoanAccountStatusTransition(UUID.randomUUID(), UUID.randomUUID(), 1,
                        UUID.randomUUID(), null, LoanAccountStatus.ACTIVE,
                        LoanAccountServicingAction.ACTIVATION_INITIALIZED, ActorType.USER, USER,
                        AT.toLocalDate(), AT)));
        conflict();
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT)));
        when(staffActors.findByUserIds(any())).thenReturn(Map.of());
        conflict();
    }

    @Test void brokenStatusChainFinalStatusAndInvalidPersistedActorFailClosed() {
        var broken = new LoanAccountStatusTransition(UUID.randomUUID(), ACCOUNT, 2,
                UUID.randomUUID(), LoanAccountStatus.SETTLED, LoanAccountStatus.OVERDUE,
                LoanAccountServicingAction.OVERDUE_EVALUATED, ActorType.SYSTEM, null,
                AT.toLocalDate(), AT.plusDays(1));
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT), broken));
        conflict();
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT)));
        when(account.status()).thenReturn(LoanAccountStatus.OVERDUE);
        conflict();
        when(transitions.findByLoanAccountId(ACCOUNT)).thenThrow(
                new IllegalArgumentException("invalid persisted actor"));
        conflict();
    }

    @Test void customerAndStaffWithoutLoanReadAreDeniedBeforeReading() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(USER, "customer@test", "CUSTOMER",
                CUSTOMER, Set.of(), Set.of("loan:read:own")));
        assertThrows(AuthorizationException.class, () -> service.query(APP, 0, 20));
        when(users.currentUser()).thenReturn(new AuthenticatedUser(USER, "staff@test", "STAFF",
                null, Set.of(), Set.of("repayment:update")));
        assertThrows(AuthorizationException.class, () -> service.query(APP, 0, 20));
        verify(applications, never()).findById(APP);
    }

    @Test void settlementAndClosureCorrelateExactDurablePaymentAndStatusEvents() {
        arrangeSettlement();
        var settled = service.query(APP, 0, 20);
        assertEquals("Mina Accounting", settled.settlement().actor().staff().displayName());
        assertEquals(payment.id(), settled.repaymentHistory().items().getFirst()
                .financial().repaymentTransactionId());
        assertNull(settled.closure());

        when(account.status()).thenReturn(LoanAccountStatus.CLOSED);
        when(closures.findByLoanAccountId(ACCOUNT)).thenReturn(Optional.of(closure));
        when(closure.id()).thenReturn(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"));
        when(closure.loanApplicationId()).thenReturn(APP);
        when(closure.loanAccountId()).thenReturn(ACCOUNT);
        when(closure.closedByUserId()).thenReturn(USER);
        when(closure.closedAt()).thenReturn(AT.plusDays(2));
        var closeEvent = new LoanAccountStatusTransition(UUID.randomUUID(), ACCOUNT, 3,
                closure.id(), LoanAccountStatus.SETTLED, LoanAccountStatus.CLOSED,
                LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE, ActorType.USER, USER,
                AT.toLocalDate().plusDays(2), AT.plusDays(2));
        var settlementTransition = settlementEvent();
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT),
                settlementTransition, closeEvent));
        assertEquals(AT.plusDays(2), service.query(APP, 0, 20).closure().at());

        when(closure.closedByUserId()).thenReturn(UUID.randomUUID());
        conflict();
    }

    @Test void contradictorySettlementActorOrMissingSettlementFailsClosed() {
        arrangeSettlement();
        when(settlement.approvedByUserId()).thenReturn(UUID.randomUUID());
        conflict();
        when(settlement.approvedByUserId()).thenReturn(USER);
        when(settlement.approvedAt()).thenReturn(AT.plusDays(1).plusSeconds(1));
        conflict();
        when(settlement.approvedAt()).thenReturn(AT.plusDays(1));
        when(settlement.repaymentTransactionId()).thenReturn(UUID.randomUUID());
        conflict();
        UUID exactPaymentId = payment.id();
        when(settlement.repaymentTransactionId()).thenReturn(exactPaymentId);
        when(settlements.findByLoanAccountId(ACCOUNT)).thenReturn(Optional.empty());
        conflict();
    }

    @Test void repaymentActorDoesNotRequireAStatusTransition() {
        arrangePaymentPage();
        var result = service.query(APP, 0, 20);
        assertEquals("USER", result.repaymentHistory().items().getFirst().actor().type());
        assertEquals("Mina Accounting", result.repaymentHistory().items().getFirst()
                .actor().staff().displayName());
        assertEquals(1, result.statusHistory().size());
    }

    private void arrangeSettlement() {
        arrangePaymentPage();
        when(account.status()).thenReturn(LoanAccountStatus.SETTLED);
        when(payment.transactionType()).thenReturn(RepaymentTransactionType.APPROVED_SETTLEMENT);
        var settlementTransition = settlementEvent();
        when(transitions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(activation(USER, AT),
                settlementTransition));
        when(settlements.findByLoanAccountId(ACCOUNT)).thenReturn(Optional.of(settlement));
        when(settlement.loanApplicationId()).thenReturn(APP);
        when(settlement.loanAccountId()).thenReturn(ACCOUNT);
        UUID paymentId = payment.id();
        UUID requestId = payment.requestId();
        when(settlement.repaymentTransactionId()).thenReturn(paymentId);
        when(settlement.requestId()).thenReturn(requestId);
        when(settlement.settlementAmount()).thenReturn(BigDecimal.valueOf(100));
        when(settlement.approvedByUserId()).thenReturn(USER);
        when(settlement.approvedAt()).thenReturn(AT.plusDays(1));
    }

    private LoanAccountStatusTransition settlementEvent() {
        return new LoanAccountStatusTransition(UUID.randomUUID(), ACCOUNT, 2, payment.id(),
                LoanAccountStatus.ACTIVE, LoanAccountStatus.SETTLED,
                LoanAccountServicingAction.APPROVED_SETTLEMENT, ActorType.USER, USER,
                AT.toLocalDate().plusDays(1), AT.plusDays(1));
    }

    private void arrangePaymentPage() {
        UUID id = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        UUID request = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        when(payment.id()).thenReturn(id);
        when(payment.requestId()).thenReturn(request);
        when(payment.loanApplicationId()).thenReturn(APP);
        when(payment.loanAccountId()).thenReturn(ACCOUNT);
        when(payment.transactionType()).thenReturn(RepaymentTransactionType.REPAYMENT);
        when(payment.recordedByUserId()).thenReturn(USER);
        when(payment.recordedAt()).thenReturn(AT.plusDays(1));
        when(payment.receivedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(transactions.findByLoanAccountId(ACCOUNT)).thenReturn(List.of(payment));
        when(financialItem.repaymentTransactionId()).thenReturn(id);
        when(financialItem.recordedAt()).thenReturn(AT.plusDays(1));
        when(mapper.toDto(any(QueryRepaymentsUseCase.PageResult.class))).thenReturn(
                new RepaymentHistoryPageDto(0, 20, 1, 1, List.of(financialItem)));
        when(transactions.findPageByLoanAccountId(ACCOUNT, 0, 20)).thenReturn(
                new RepaymentTransactionRepository.Page(0, 20, 1, 1, List.of(payment)));
    }

    private void conflict() {
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(BusinessStateConflictException.class,
                () -> service.query(APP, 0, 20)).getErrorCode());
    }

    private static LoanAccountStatusTransition activation(UUID actor, LocalDateTime at) {
        return new LoanAccountStatusTransition(UUID.randomUUID(), ACCOUNT, 1, UUID.randomUUID(),
                null, LoanAccountStatus.ACTIVE, LoanAccountServicingAction.ACTIVATION_INITIALIZED,
                ActorType.USER, actor, LocalDate.of(2026, 9, 20), at);
    }
}
