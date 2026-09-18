package com.meridian.platform.document.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;

import java.util.UUID;

public interface LoanDocumentWorkflowPort {

    LoanDocumentWorkflowSnapshot lock(UUID loanApplicationId);

    default LoanDocumentWorkflowSnapshot find(UUID loanApplicationId) {
        return lock(loanApplicationId);
    }

    record LoanDocumentWorkflowSnapshot(
            UUID loanApplicationId,
            UUID customerId,
            OriginationChannel originationChannel,
            LoanApplicationStatus status
    ) {
        public LoanDocumentWorkflowSnapshot(
                UUID loanApplicationId,
                UUID customerId,
                LoanApplicationStatus status
        ) {
            this(loanApplicationId, customerId, OriginationChannel.CUSTOMER_DIGITAL, status);
        }
    }
}
