package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;

import java.util.UUID;

public interface StartLoanApplicationReviewUseCase {

    LoanApplicationReviewDto startReview(UUID loanApplicationId);
}
