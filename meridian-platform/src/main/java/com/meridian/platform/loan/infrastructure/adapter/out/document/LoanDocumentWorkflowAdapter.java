package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoanDocumentWorkflowAdapter implements LoanDocumentWorkflowPort {

    private final LoanApplicationRepository loanApplicationRepository;

    public LoanDocumentWorkflowAdapter(LoanApplicationRepository loanApplicationRepository) {
        this.loanApplicationRepository = loanApplicationRepository;
    }

    @Override
    public LoanDocumentWorkflowSnapshot lock(UUID loanApplicationId) {
        loanApplicationRepository.acquireWorkflowLock(loanApplicationId);
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        return new LoanDocumentWorkflowSnapshot(
                loanApplication.id(),
                loanApplication.customerId(),
                loanApplication.status()
        );
    }

    @Override
    public LoanDocumentWorkflowSnapshot find(UUID loanApplicationId) {
        LoanApplication loanApplication = loanApplicationRepository.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        return new LoanDocumentWorkflowSnapshot(
                loanApplication.id(),
                loanApplication.customerId(),
                loanApplication.status()
        );
    }
}
