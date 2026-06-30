package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.StartLoanApplicationReviewUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/review")
public class LoanApplicationReviewController {

    private final StartLoanApplicationReviewUseCase startLoanApplicationReviewUseCase;

    public LoanApplicationReviewController(StartLoanApplicationReviewUseCase startLoanApplicationReviewUseCase) {
        this.startLoanApplicationReviewUseCase = startLoanApplicationReviewUseCase;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('loan:review')")
    public LoanApplicationReviewDto startReview(@PathVariable UUID loanApplicationId) {
        return startLoanApplicationReviewUseCase.startReview(loanApplicationId);
    }
}
