package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;

public interface ApplyReviewRecommendationUseCase {

    LoanApplicationReviewDto applyReviewRecommendation(ApplyReviewRecommendationCommand command);
}
