package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.RevealStaffCustomerIdentityReferenceUseCase;
import com.meridian.platform.loan.application.port.out.CustomerIdentityReferenceRevealPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

@Service
public class RevealStaffCustomerIdentityReferenceService implements RevealStaffCustomerIdentityReferenceUseCase {
    private final LoanApplicationRepository applications;
    private final CustomerIdentityReferenceRevealPort customerReveal;
    private final CurrentUserProvider currentUser;

    public RevealStaffCustomerIdentityReferenceService(LoanApplicationRepository applications,
            CustomerIdentityReferenceRevealPort customerReveal, CurrentUserProvider currentUser) {
        this.applications = applications;
        this.customerReveal = customerReveal;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public Result reveal(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        var actor = currentUser.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.roles().contains("LOAN_OFFICER") || !actor.hasPermission("loan:read")
                || !actor.hasPermission("customer:read") || !actor.hasPermission("customer:identity:reveal")) {
            throw new AuthorizationException("CUSTOMER_IDENTITY_REFERENCE_ACCESS_DENIED",
                    "Customer Identity Reference access is denied.");
        }
        var application = applications.findById(loanApplicationId).orElseThrow(() ->
                new EntityNotFoundException("LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));
        var result = customerReveal.reveal(application.customerId(), application.id());
        return new Result(application.id(), result.identityReference());
    }
}
