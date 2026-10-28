package com.meridian.platform.document.application.spi.loan;

import java.util.UUID;

public interface LoanDocumentWorkflowSpi {

    LoanDocumentWorkflowSnapshot lock(UUID loanApplicationId);

    LoanDocumentWorkflowSnapshot find(UUID loanApplicationId);

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
