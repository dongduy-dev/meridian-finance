package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;

public interface ApplyApprovalDecisionUseCase {

    LoanApplicationReviewDto applyApprovalDecision(ApplyApprovalDecisionCommand command);
}
