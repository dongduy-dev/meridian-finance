package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanContract;
import java.util.Optional;
import java.util.UUID;

public interface QueryCurrentLoanContractUseCase {
    Optional<LoanContract> findCurrent(UUID loanApplicationId);
}
