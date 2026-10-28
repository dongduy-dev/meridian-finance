package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.LoanDocumentWorkflowUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LoanDocumentWorkflowService implements LoanDocumentWorkflowUseCase {

    private final LoanApplicationRepository loanApplicationRepository;

    public LoanDocumentWorkflowService(LoanApplicationRepository loanApplicationRepository) {
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
                loanApplication.originationChannel(),
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
                loanApplication.originationChannel(),
                loanApplication.status()
        );
    }
}
