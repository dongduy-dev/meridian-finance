package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.ServicingCustomerContextPort;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryStaffLoanAccountCustomerContextServiceTest {
    private static final UUID APP = UUID.randomUUID(), ACCOUNT = UUID.randomUUID(), CUSTOMER = UUID.randomUUID();
    private final LoanApplicationRepository applications = mock(LoanApplicationRepository.class);
    private final LoanAccountRepository accounts = mock(LoanAccountRepository.class);
    private final ServicingCustomerContextPort customers = mock(ServicingCustomerContextPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final LoanApplication application = mock(LoanApplication.class);
    private final LoanAccount account = mock(LoanAccount.class);
    private final QueryStaffLoanAccountCustomerContextService service =
            new QueryStaffLoanAccountCustomerContextService(applications, accounts, customers, users);

    @BeforeEach void setUp() {
        when(users.currentUser()).thenReturn(actor(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", "repayment:update")));
        when(applications.findById(APP)).thenReturn(Optional.of(application));
        when(application.id()).thenReturn(APP);
        when(application.customerId()).thenReturn(CUSTOMER);
        when(application.status()).thenReturn(LoanApplicationStatus.DISBURSED);
        when(accounts.findByLoanApplicationId(APP)).thenReturn(Optional.of(account));
        when(account.id()).thenReturn(ACCOUNT);
        when(account.loanApplicationId()).thenReturn(APP);
        when(account.customerId()).thenReturn(CUSTOMER);
        when(customers.findBusinessIdentityByCustomerId(CUSTOMER)).thenReturn(Optional.of(
                new ServicingCustomerContextPort.BusinessIdentity("CUS-001", "Ari Customer")));
        when(customers.findCurrentContactByCustomerId(CUSTOMER)).thenReturn(Optional.of(
                new ServicingCustomerContextPort.CurrentContact("CUS-001", "Ari Customer", "0901234567")));
    }

    static Stream<Arguments> visibility() {
        return Stream.of(
                Arguments.of(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", "repayment:update"), "contact"),
                Arguments.of(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", "loan:account:close"), "contact"),
                Arguments.of(Set.of("LOAN_OFFICER"), Set.of("loan:read", "customer:read"), "contact"),
                Arguments.of(Set.of(), Set.of("loan:read", "customer:read"), "contact"),
                Arguments.of(Set.of("APPROVER"), Set.of("loan:read", "loan:settlement:approve"), "identity"),
                Arguments.of(Set.of(), Set.of("loan:read"), "none"),
                Arguments.of(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read"), "none"),
                Arguments.of(Set.of("APPROVER"), Set.of("loan:read"), "none"),
                Arguments.of(Set.of(), Set.of("loan:read", "repayment:update", "loan:account:close", "loan:settlement:approve"), "none"),
                Arguments.of(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", "loan:disburse"), "none"));
    }

    @ParameterizedTest @MethodSource("visibility")
    void enforcesExactOperationalPurpose(Set<String> roles, Set<String> permissions, String scope) {
        when(users.currentUser()).thenReturn(actor(roles, permissions));
        var result = service.query(APP);
        assertEquals(APP, result.loanApplicationId());
        assertEquals(ACCOUNT, result.loanAccountId());
        if (scope.equals("none")) {
            assertNull(result.customer());
            verifyNoInteractions(customers);
        } else {
            assertEquals("CUS-001", result.customer().customerNumber());
            assertEquals("Ari Customer", result.customer().fullName());
            if (scope.equals("contact")) {
                assertEquals("0901234567", result.customer().phoneNumber());
                verify(customers).findCurrentContactByCustomerId(CUSTOMER);
                verify(customers, never()).findBusinessIdentityByCustomerId(any());
            } else {
                assertNull(result.customer().phoneNumber());
                verify(customers).findBusinessIdentityByCustomerId(CUSTOMER);
                verify(customers, never()).findCurrentContactByCustomerId(any());
            }
            assertFalse(result.toString().contains("Ari Customer"));
        }
    }

    @Test void customerSessionsAndMissingExactReadPermissionAreDeniedBeforeLookup() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "customer@test", "CUSTOMER",
                CUSTOMER, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read", "repayment:update")));
        assertThrows(AuthorizationException.class, () -> service.query(APP));
        when(users.currentUser()).thenReturn(actor(Set.of("ACCOUNTING_OFFICER"), Set.of("repayment:update")));
        assertThrows(AuthorizationException.class, () -> service.query(APP));
        verifyNoInteractions(applications, accounts, customers);
    }

    @Test void missingApplicationOrAccountKeepsEstablishedNotFoundErrors() {
        when(applications.findById(APP)).thenReturn(Optional.empty());
        assertEquals("LOAN_APPLICATION_NOT_FOUND", assertThrows(EntityNotFoundException.class, () -> service.query(APP)).getErrorCode());
        when(applications.findById(APP)).thenReturn(Optional.of(application));
        when(accounts.findByLoanApplicationId(APP)).thenReturn(Optional.empty());
        assertEquals("LOAN_ACCOUNT_NOT_FOUND", assertThrows(EntityNotFoundException.class, () -> service.query(APP)).getErrorCode());
        verifyNoInteractions(customers);
    }

    @Test void contradictoryApplicationAccountCustomerLinksFailBeforeCustomerLookup() {
        when(application.id()).thenReturn(UUID.randomUUID());
        conflict();
        when(application.id()).thenReturn(APP);
        when(application.status()).thenReturn(LoanApplicationStatus.DISBURSEMENT_PENDING);
        conflict();
        when(application.status()).thenReturn(LoanApplicationStatus.DISBURSED);
        when(account.loanApplicationId()).thenReturn(UUID.randomUUID());
        conflict();
        when(account.loanApplicationId()).thenReturn(APP);
        when(account.customerId()).thenReturn(UUID.randomUUID());
        conflict();
        when(account.customerId()).thenReturn(CUSTOMER);
        when(application.customerId()).thenReturn(null);
        conflict();
        verifyNoInteractions(customers);
    }

    @Test void missingOrIncompleteAuthorizedContactAndIdentityFailClosed() {
        when(customers.findCurrentContactByCustomerId(CUSTOMER)).thenReturn(Optional.empty());
        conflict();
        when(customers.findCurrentContactByCustomerId(CUSTOMER)).thenReturn(Optional.of(
                new ServicingCustomerContextPort.CurrentContact("CUS-001", "Ari Customer", null)));
        conflict();
        when(users.currentUser()).thenReturn(actor(Set.of("APPROVER"), Set.of("loan:read", "loan:settlement:approve")));
        when(customers.findBusinessIdentityByCustomerId(CUSTOMER)).thenReturn(Optional.empty());
        conflict();
        when(customers.findBusinessIdentityByCustomerId(CUSTOMER)).thenReturn(Optional.of(
                new ServicingCustomerContextPort.BusinessIdentity("CUS-001", " ")));
        conflict();
    }

    private void conflict() {
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(BusinessStateConflictException.class, () -> service.query(APP)).getErrorCode());
    }
    private static AuthenticatedUser actor(Set<String> roles, Set<String> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), "staff@test", "STAFF", null, roles, permissions);
    }
}
