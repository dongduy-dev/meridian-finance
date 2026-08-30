package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryCustomerLoanAccountIndexServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();

    @Mock LoanApplicationRepository applications;
    @Mock LoanContractRepository contracts;
    @Mock LoanAccountRepository accounts;
    @Mock RepaymentScheduleRepository schedules;
    @Mock RepaymentInstallmentProgressRepository progress;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryLoanAccountService service;

    @BeforeEach
    void setUp() {
        service = new QueryLoanAccountService(
                applications, contracts, accounts, schedules, progress, currentUserProvider
        );
    }

    @Test
    void returnsCompactOwnedAccountsWithAuthoritativeBalanceAndClassification() {
        LoanApplication application = application(CUSTOMER_ID);
        LoanAccount account = account(CUSTOMER_ID, LoanAccountStatus.ACTIVE);
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(applications.findByCustomerIdOrderBySubmittedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(application));
        when(accounts.findByCustomerIdOrderByActivatedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(account));

        var result = service.queryOwnAccounts();

        assertEquals(1, result.size());
        assertEquals("UNSECURED_CONSUMER_LOAN", result.getFirst().productCode());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getFirst().totalPaid());
        assertEquals(new BigDecimal("11800000.00"), result.getFirst().totalOutstanding());
        assertEquals(true, result.getFirst().servicingActive());
    }

    @Test
    void classifiesClosedAccountAsInactiveWithSettledBalance() {
        LoanApplication application = application(CUSTOMER_ID);
        LoanAccount account = closedAccount(CUSTOMER_ID);
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(applications.findByCustomerIdOrderBySubmittedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(application));
        when(accounts.findByCustomerIdOrderByActivatedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(account));

        var result = service.queryOwnAccounts();

        assertEquals("CLOSED", result.getFirst().status());
        assertEquals(new BigDecimal("11800000.00"), result.getFirst().totalPaid());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getFirst().totalOutstanding());
        assertEquals(false, result.getFirst().servicingActive());
    }

    @Test
    void validEmptyIndexAndCustomerOnlyAuthorization() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(applications.findByCustomerIdOrderBySubmittedAtDesc(CUSTOMER_ID)).thenReturn(List.of());
        when(accounts.findByCustomerIdOrderByActivatedAtDesc(CUSTOMER_ID)).thenReturn(List.of());
        assertEquals(List.of(), service.queryOwnAccounts());

        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:read")
        ));
        assertThrows(AuthorizationException.class, service::queryOwnAccounts);
    }

    private static LoanApplication application(UUID customerId) {
        return new LoanApplication(
                APPLICATION_ID, customerId, UUID.randomUUID(), "UCL-20260830-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.DISBURSED, new BigDecimal("10000000.00"), 3,
                LocalDateTime.of(2026, 8, 30, 8, 0)
        );
    }

    private static LoanAccount account(UUID customerId, LoanAccountStatus status) {
        UUID accountId = UUID.randomUUID();
        return new LoanAccount(
                accountId, APPLICATION_ID, UUID.randomUUID(), customerId,
                LoanAccount.accountNumberFor(accountId), status,
                new BigDecimal("10000000.00"), 3, new BigDecimal("1800000.00"),
                BigDecimal.ZERO.setScale(2), new BigDecimal("11800000.00"),
                LocalDateTime.of(2026, 8, 30, 9, 0)
        );
    }

    private static LoanAccount closedAccount(UUID customerId) {
        LoanAccount active = account(customerId, LoanAccountStatus.ACTIVE);
        LocalDateTime paidAt = active.activatedAt().plusDays(1);
        RepaymentBalance settledBalance = new RepaymentBalance(
                active.approvedPrincipal(), active.totalInterest(), active.feeAmount(),
                active.totalRepaymentAmount(), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), paidAt.toLocalDate(), paidAt,
                paidAt.toLocalDate()
        );
        return active.withServicingState(settledBalance, LoanAccountStatus.SETTLED, paidAt)
                .closeAdministratively(paidAt.plusMinutes(1));
    }

    private static AuthenticatedUser customer(UUID customerId) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("loan:read:own")
        );
    }
}
