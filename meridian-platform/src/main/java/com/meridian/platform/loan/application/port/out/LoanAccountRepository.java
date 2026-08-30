package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanAccountRepository {

    LoanAccount save(LoanAccount loanAccount);

    LoanAccount updateServicingState(LoanAccount loanAccount);

    Optional<LoanAccount> findById(UUID loanAccountId);

    Optional<LoanAccount> findByLoanApplicationId(UUID loanApplicationId);

    Optional<LoanAccount> findByLoanContractId(UUID loanContractId);

    Optional<LoanAccount> findByLoanApplicationIdForUpdate(UUID loanApplicationId);

    default List<LoanAccount> findByCustomerIdOrderByActivatedAtDesc(UUID customerId) {
        return List.of();
    }
}
