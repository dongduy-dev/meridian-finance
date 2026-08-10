package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanAccountClosure;

import java.util.Optional;
import java.util.UUID;

public interface LoanAccountClosureRepository {

    void acquireClosureRequestLock(UUID requestId);

    LoanAccountClosureSaveOutcome save(LoanAccountClosure closure);

    Optional<LoanAccountClosure> findByRequestId(UUID requestId);

    Optional<LoanAccountClosure> findByLoanAccountId(UUID loanAccountId);
}
