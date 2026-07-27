package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.ManualDisbursement;

import java.util.Optional;
import java.util.UUID;

public interface ManualDisbursementRepository {

    ManualDisbursementSaveOutcome save(ManualDisbursement manualDisbursement);

    Optional<ManualDisbursement> findByRequestId(UUID requestId);

    Optional<ManualDisbursement> findByLoanApplicationId(UUID loanApplicationId);

    Optional<ManualDisbursement> findByLoanContractId(UUID loanContractId);

    Optional<ManualDisbursement> findByLoanAccountId(UUID loanAccountId);

    Optional<ManualDisbursement> findByExternalTransferReference(String externalTransferReference);

    Optional<ManualDisbursement> findByLoanApplicationIdForUpdate(UUID loanApplicationId);
}
