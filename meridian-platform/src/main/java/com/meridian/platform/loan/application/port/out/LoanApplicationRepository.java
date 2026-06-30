package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LoanApplicationRepository {

    LoanApplication save(LoanApplication loanApplication);

    Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId);

    boolean existsByCustomerIdAndProductCodeAndStatusIn(
            UUID customerId,
            ProductCode productCode,
            Set<LoanApplicationStatus> statuses
    );

    long nextApplicationNumberSequence();
}
