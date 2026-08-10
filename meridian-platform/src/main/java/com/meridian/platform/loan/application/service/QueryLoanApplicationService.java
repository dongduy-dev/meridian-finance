package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueryLoanApplicationService implements QueryLoanApplicationUseCase {

    private final LoanApplicationRepository applications;
    private final CurrentUserProvider currentUserProvider;

    public QueryLoanApplicationService(
            LoanApplicationRepository applications,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public LoanApplicationStatusDto query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireReadAuthority(actor);
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryLoanApplicationService::notFound);
        if (actor.optionalCustomerId().isPresent()
                && !application.customerId().equals(actor.requireCustomerId())) {
            throw notFound();
        }
        return new LoanApplicationStatusDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                application.status().name(),
                application.submittedAt()
        );
    }

    private static void requireReadAuthority(AuthenticatedUser actor) {
        boolean allowed = actor.optionalCustomerId().isPresent()
                ? actor.hasPermission("loan:read:own")
                : actor.hasPermission("loan:read");
        if (!allowed) {
            throw new AuthorizationException(
                    "LOAN_APPLICATION_ACCESS_DENIED",
                    "Loan Application access is denied."
            );
        }
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND",
                "Loan Application was not found."
        );
    }
}
