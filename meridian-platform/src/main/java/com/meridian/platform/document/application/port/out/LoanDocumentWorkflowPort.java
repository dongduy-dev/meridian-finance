package com.meridian.platform.document.application.port.out;

import java.util.UUID;

public interface LoanDocumentWorkflowPort {

    LoanDocumentWorkflowSnapshot lock(UUID loanApplicationId);

    default LoanDocumentWorkflowSnapshot find(UUID loanApplicationId) {
        return lock(loanApplicationId);
    }

    record LoanDocumentWorkflowSnapshot(
            UUID loanApplicationId,
            UUID customerId,
            String originationChannel,
            String status
    ) {
        public LoanDocumentWorkflowSnapshot(
                UUID loanApplicationId,
                UUID customerId,
                String status
        ) {
            this(loanApplicationId, customerId, "CUSTOMER_DIGITAL", status);
        }
    }
}
