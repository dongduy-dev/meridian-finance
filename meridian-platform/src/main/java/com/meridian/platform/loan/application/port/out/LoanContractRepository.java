package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanContract;

import java.util.Optional;
import java.util.UUID;

public interface LoanContractRepository {
    LoanContract save(LoanContract contract);
    LoanContract saveAndFlush(LoanContract contract);
    Optional<LoanContract> findCurrentByApplicationId(UUID loanApplicationId);
    Optional<LoanContract> findCurrentByApplicationIdForUpdate(UUID loanApplicationId);
    Optional<LoanContract> findByPreparationRequestId(UUID requestId);
    Optional<LoanContract> findByAcknowledgmentRequestId(UUID requestId);
    Optional<LoanContract> findByConfirmationRequestId(UUID requestId);
    int nextVersion(UUID loanApplicationId);
}
