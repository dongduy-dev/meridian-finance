package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcomeRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryRepaymentsServiceTest {

    @Mock LoanApplicationRepository applications;
    @Mock LoanAccountRepository accounts;
    @Mock RepaymentScheduleRepository schedules;
    @Mock RepaymentTransactionRepository transactions;
    @Mock RepaymentOperationOutcomeRepository outcomes;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryRepaymentsService service;
    private UUID applicationId;
    private UUID customerId;
    private LoanApplication application;
    private LoanAccount account;
    private RepaymentSchedule schedule;

    @BeforeEach
    void setUp() {
        service = new QueryRepaymentsService(applications, accounts, schedules,
                transactions, outcomes, currentUserProvider);
        applicationId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        application = mock(LoanApplication.class);
        account = mock(LoanAccount.class);
        schedule = mock(RepaymentSchedule.class);
        when(application.id()).thenReturn(applicationId);
        when(application.customerId()).thenReturn(customerId);
        when(application.status()).thenReturn(LoanApplicationStatus.DISBURSED);
        UUID accountId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        when(account.id()).thenReturn(accountId);
        when(account.loanApplicationId()).thenReturn(applicationId);
        when(account.customerId()).thenReturn(customerId);
        when(account.loanContractId()).thenReturn(contractId);
        when(schedule.id()).thenReturn(scheduleId);
        when(schedule.loanApplicationId()).thenReturn(applicationId);
        when(schedule.loanAccountId()).thenReturn(accountId);
        when(schedule.loanContractId()).thenReturn(contractId);
        when(schedule.scheduleType()).thenReturn(RepaymentScheduleType.FINAL);
        when(schedule.version()).thenReturn(RepaymentSchedule.INITIAL_FINAL_VERSION);
        when(schedule.items()).thenReturn(List.of());
    }

    @Test
    void returnsBoundedEmptyPageForValidAccount() {
        arrangeStaff();
        arrangeTuple();
        when(transactions.findPageByLoanAccountId(account.id(), 2, 100))
                .thenReturn(new RepaymentTransactionRepository.Page(2, 100, 0, 0, List.of()));

        QueryRepaymentsUseCase.PageResult result = service.query(applicationId, 2, 100);

        assertEquals(2, result.page());
        assertEquals(100, result.size());
        assertEquals(0, result.totalElements());
        assertThrows(UnsupportedOperationException.class,
                () -> result.items().add(mock(QueryRepaymentsUseCase.Item.class)));
        verify(transactions).findPageByLoanAccountId(account.id(), 2, 100);
    }

    @Test
    void customerCrossOwnershipAndUnavailableEvidenceAreConcealed() {
        when(currentUserProvider.currentUser()).thenReturn(customer(UUID.randomUUID()));
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));

        EntityNotFoundException foreign = assertThrows(EntityNotFoundException.class,
                () -> service.query(applicationId, 0, 20));
        assertEquals("LOAN_ACCOUNT_NOT_FOUND", foreign.getErrorCode());

        when(currentUserProvider.currentUser()).thenReturn(customer(customerId));
        when(accounts.findByLoanApplicationId(applicationId)).thenReturn(Optional.of(account));
        when(schedules.findByLoanAccountId(account.id())).thenReturn(Optional.empty());
        EntityNotFoundException unavailable = assertThrows(EntityNotFoundException.class,
                () -> service.query(applicationId, 0, 20));
        assertEquals("LOAN_ACCOUNT_NOT_FOUND", unavailable.getErrorCode());
    }

    @Test
    void staffReceivesConflictWhenImmutableOutcomeIsMissing() {
        arrangeStaff();
        arrangeTuple();
        RepaymentTransaction transaction = mock(RepaymentTransaction.class);
        UUID transactionId = UUID.randomUUID();
        when(transaction.id()).thenReturn(transactionId);
        when(transactions.findPageByLoanAccountId(account.id(), 0, 20))
                .thenReturn(new RepaymentTransactionRepository.Page(
                        0, 20, 1, 1, List.of(transaction)));
        when(outcomes.findByRepaymentTransactionId(transactionId)).thenReturn(Optional.empty());

        BusinessStateConflictException conflict = assertThrows(
                BusinessStateConflictException.class,
                () -> service.query(applicationId, 0, 20));

        assertEquals("SYSTEM_STATE_CONFLICT", conflict.getErrorCode());
    }

    @Test
    void invalidPaginationIsRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.query(applicationId, -1, 20));
        assertThrows(IllegalArgumentException.class,
                () -> service.query(applicationId, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.query(applicationId, 0, 101));
    }

    private void arrangeStaff() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:read")));
    }

    private void arrangeTuple() {
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));
        when(accounts.findByLoanApplicationId(applicationId)).thenReturn(Optional.of(account));
        when(schedules.findByLoanAccountId(account.id())).thenReturn(Optional.of(schedule));
    }

    private static AuthenticatedUser customer(UUID id) {
        return new AuthenticatedUser(UUID.randomUUID(), "customer@meridian.test",
                "CUSTOMER", id, Set.of("CUSTOMER"), Set.of("loan:read:own"));
    }
}
