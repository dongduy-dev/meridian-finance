package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.document.application.spi.loan.LoanDocumentWorkflowSpi;
import com.meridian.platform.loan.application.port.in.LoanDocumentWorkflowUseCase;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class LoanDocumentWorkflowAdapter implements LoanDocumentWorkflowSpi {
    private final LoanDocumentWorkflowUseCase workflows;

    public LoanDocumentWorkflowAdapter(LoanDocumentWorkflowUseCase workflows) {
        this.workflows = workflows;
    }

    @Override
    public LoanDocumentWorkflowSnapshot find(UUID loanApplicationId) {
        return snapshot(workflows.find(loanApplicationId));
    }

    @Override
    public LoanDocumentWorkflowSnapshot lock(UUID loanApplicationId) {
        return snapshot(workflows.lock(loanApplicationId));
    }

    private static LoanDocumentWorkflowSnapshot snapshot(LoanDocumentWorkflowUseCase.LoanDocumentWorkflowSnapshot result) {
        return new LoanDocumentWorkflowSnapshot(result.loanApplicationId(), result.customerId(),
                result.originationChannel().name(), result.status().name());
    }
}
