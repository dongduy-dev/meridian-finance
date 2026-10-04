package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CustomerCollateralDto;
import com.meridian.platform.loan.application.port.in.QueryOwnCollateralUseCase;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryOwnCollateralService implements QueryOwnCollateralUseCase {

    private final LoanApplicationRepository applications;
    private final CollateralRepository collaterals;
    private final CurrentUserProvider currentUserProvider;

    public QueryOwnCollateralService(
            LoanApplicationRepository applications,
            CollateralRepository collaterals,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.collaterals = collaterals;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerCollateralDto query(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        if (!"CUSTOMER".equals(actor.userType())
                || actor.optionalCustomerId().isEmpty()
                || !actor.hasPermission("loan:read:own")) {
            throw new AuthorizationException(
                    "LOAN_APPLICATION_ACCESS_DENIED", "Customer Loan Application access is denied."
            );
        }
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(QueryOwnCollateralService::notFound);
        if (!application.customerId().equals(actor.requireCustomerId())
                || application.productCode() != ProductCode.COLLATERAL_LOAN) {
            throw notFound();
        }
        List<Collateral> facts = collaterals.findByLoanApplicationId(loanApplicationId);
        if (facts.size() != 1 || !facts.getFirst().loanApplicationId().equals(loanApplicationId)) {
            throw new BusinessStateConflictException(
                    "SYSTEM_STATE_CONFLICT", "Authoritative collateral facts are inconsistent."
            );
        }
        Collateral collateral = facts.getFirst();
        return new CustomerCollateralDto(
                collateral.collateralType().name(), collateral.description(), collateral.estimatedValue(),
                collateral.ownershipStatus(), collateral.conditionNote()
        );
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."
        );
    }
}
