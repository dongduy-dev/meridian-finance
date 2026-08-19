package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.CollateralLoanVerification;

import java.util.Optional;
import java.util.UUID;

public interface CollateralLoanVerificationRepository {

    CollateralLoanVerification save(CollateralLoanVerification verification);

    Optional<CollateralLoanVerification> findLatestByLoanApplicationId(UUID loanApplicationId);

    Optional<CollateralLoanVerification> findLatestByLoanApplicationIdForUpdate(
            UUID loanApplicationId
    );

    default Optional<CollateralLoanVerification> findByLoanApplicationId(UUID loanApplicationId) {
        return findLatestByLoanApplicationId(loanApplicationId);
    }
}
