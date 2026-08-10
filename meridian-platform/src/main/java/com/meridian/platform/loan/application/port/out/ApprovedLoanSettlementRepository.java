package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;

import java.util.Optional;
import java.util.UUID;

public interface ApprovedLoanSettlementRepository {

    void acquireApprovalRequestLock(UUID requestId);

    ApprovedLoanSettlementSaveOutcome save(ApprovedLoanSettlement settlement);

    Optional<ApprovedLoanSettlement> findByRequestId(UUID requestId);

    Optional<ApprovedLoanSettlement> findByLoanAccountId(UUID loanAccountId);

    Optional<ApprovedLoanSettlement> findByRepaymentTransactionId(
            UUID repaymentTransactionId
    );
}
