package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import java.util.UUID;

public interface LoanDocumentWorkflowUseCase {
    LoanDocumentWorkflowSnapshot find(UUID loanApplicationId);
    LoanDocumentWorkflowSnapshot lock(UUID loanApplicationId);

    record LoanDocumentWorkflowSnapshot(UUID loanApplicationId, UUID customerId,
            OriginationChannel originationChannel, LoanApplicationStatus status) { }
}
