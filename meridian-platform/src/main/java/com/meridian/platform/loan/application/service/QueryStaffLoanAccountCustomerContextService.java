package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffLoanAccountCustomerContextDto;
import com.meridian.platform.loan.application.dto.StaffLoanAccountCustomerContextDto.CustomerContext;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanAccountCustomerContextUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.ServicingCustomerContextPort;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueryStaffLoanAccountCustomerContextService implements QueryStaffLoanAccountCustomerContextUseCase {
    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accounts;
    private final ServicingCustomerContextPort customers;
    private final CurrentUserProvider users;

    public QueryStaffLoanAccountCustomerContextService(LoanApplicationRepository applications,
            LoanAccountRepository accounts, ServicingCustomerContextPort customers, CurrentUserProvider users) {
        this.applications = applications;
        this.accounts = accounts;
        this.customers = customers;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffLoanAccountCustomerContextDto query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId);
        AuthenticatedUser actor = users.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:read")) {
            throw new AuthorizationException("LOAN_APPLICATION_ACCESS_DENIED", "Loan application access is denied.");
        }
        var application = applications.findById(loanApplicationId).orElseThrow(() ->
                new EntityNotFoundException("LOAN_APPLICATION_NOT_FOUND", "Loan application was not found."));
        var account = accounts.findByLoanApplicationId(loanApplicationId).orElseThrow(() ->
                new EntityNotFoundException("LOAN_ACCOUNT_NOT_FOUND", "Loan Account was not found."));
        if (!loanApplicationId.equals(application.id())
                || application.status() != LoanApplicationStatus.DISBURSED
                || application.customerId() == null || account.id() == null
                || !application.id().equals(account.loanApplicationId())
                || !application.customerId().equals(account.customerId())) throw conflict();

        CustomerContext customer = null;
        if (hasContactAuthority(actor)) {
            var contact = customers.findCurrentContactByCustomerId(application.customerId())
                    .orElseThrow(QueryStaffLoanAccountCustomerContextService::conflict);
            requireIdentity(contact.customerNumber(), contact.fullName());
            if (contact.phoneNumber() == null || contact.phoneNumber().isBlank()) throw conflict();
            customer = new CustomerContext(contact.customerNumber(), contact.fullName(), contact.phoneNumber());
        } else if (actor.roles().contains("APPROVER") && actor.hasPermission("loan:settlement:approve")) {
            var identity = customers.findBusinessIdentityByCustomerId(application.customerId())
                    .orElseThrow(QueryStaffLoanAccountCustomerContextService::conflict);
            requireIdentity(identity.customerNumber(), identity.fullName());
            customer = new CustomerContext(identity.customerNumber(), identity.fullName(), null);
        }
        return new StaffLoanAccountCustomerContextDto(application.id(), account.id(), customer);
    }

    private static boolean hasContactAuthority(AuthenticatedUser actor) {
        return actor.hasPermission("customer:read")
                || (actor.roles().contains("ACCOUNTING_OFFICER")
                && (actor.hasPermission("repayment:update") || actor.hasPermission("loan:account:close")));
    }

    private static void requireIdentity(String number, String name) {
        if (number == null || number.isBlank() || name == null || name.isBlank()) throw conflict();
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT",
                "Servicing Customer context is inconsistent.");
    }
}
