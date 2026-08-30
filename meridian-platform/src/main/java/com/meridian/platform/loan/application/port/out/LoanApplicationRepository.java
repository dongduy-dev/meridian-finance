package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LoanApplicationRepository {
    void acquireWorkflowLock(UUID loanApplicationId);

    void acquireCustomerProductLock(UUID customerId, ProductCode productCode);

    LoanApplication save(LoanApplication loanApplication);

    Optional<LoanApplication> findById(UUID loanApplicationId);

    Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId);

    default List<LoanApplication> findByCustomerIdOrderBySubmittedAtDesc(UUID customerId) {
        return List.of();
    }

    boolean existsByCustomerIdAndProductCodeAndStatusIn(
            UUID customerId,
            ProductCode productCode,
            Set<LoanApplicationStatus> statuses
    );

    default boolean existsByCustomerIdAndProductCodeAndStatusInExcludingApplication(
            UUID customerId,
            ProductCode productCode,
            Set<LoanApplicationStatus> statuses,
            UUID excludedLoanApplicationId
    ) {
        return existsByCustomerIdAndProductCodeAndStatusIn(customerId, productCode, statuses);
    }

    long nextApplicationNumberSequence();
}
