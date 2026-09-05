package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationReviewDto;

import java.util.UUID;

public interface QueryStaffLoanApplicationReviewUseCase {

    StaffLoanApplicationReviewDto query(UUID loanApplicationId);
}
