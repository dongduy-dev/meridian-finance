package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class StartLoanApplicationReviewService implements StartLoanApplicationReviewUseCase {

    private final LoanApplicationRepository loanApplicationRepository;

    public StartLoanApplicationReviewService(LoanApplicationRepository loanApplicationRepository) {
        this.loanApplicationRepository = loanApplicationRepository;
    }

    @Override
    @Transactional
    public LoanApplicationReviewDto startReview(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        LoanApplication underReview = loanApplication.startReview();
        LoanApplication savedApplication = loanApplicationRepository.save(underReview);

        return new LoanApplicationReviewDto(
                savedApplication.id(),
                savedApplication.status().name()
        );
    }
}
