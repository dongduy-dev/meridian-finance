package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplicationCancellation;

import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationCancellationRepository {

    void acquireCancellationRequestLock(UUID requestId);

    boolean saveIfAbsent(LoanApplicationCancellation cancellation);

    Optional<LoanApplicationCancellation> findByRequestId(UUID requestId);

    Optional<LoanApplicationCancellation> findByLoanApplicationId(UUID loanApplicationId);
}
