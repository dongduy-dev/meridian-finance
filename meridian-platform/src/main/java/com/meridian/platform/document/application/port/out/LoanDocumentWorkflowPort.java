package com.meridian.platform.document.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;

import java.util.UUID;

public interface LoanDocumentWorkflowPort {

    LoanDocumentWorkflowSnapshot lock(UUID loanApplicationId);

    default LoanDocumentWorkflowSnapshot find(UUID loanApplicationId) {
        return lock(loanApplicationId);
    }

    record LoanDocumentWorkflowSnapshot(
            UUID loanApplicationId,
            UUID customerId,
            LoanApplicationStatus status
    ) {
    }
}
