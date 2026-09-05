package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollateralLoanVerificationRepository {

    CollateralLoanVerification save(CollateralLoanVerification verification);

    Optional<CollateralLoanVerification> findLatestByLoanApplicationId(UUID loanApplicationId);

    default List<CollateralLoanVerification> findAllByLoanApplicationIdOrderByVerificationSequenceAsc(
            UUID loanApplicationId
    ) {
        return findLatestByLoanApplicationId(loanApplicationId).stream().toList();
    }

    Optional<CollateralLoanVerification> findLatestByLoanApplicationIdForUpdate(
            UUID loanApplicationId
    );

    default Optional<CollateralLoanVerification> findByLoanApplicationId(UUID loanApplicationId) {
        return findLatestByLoanApplicationId(loanApplicationId);
    }
}
