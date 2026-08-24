package com.meridian.platform.loan.application.service.collateral;

import com.meridian.platform.loan.domain.model.LoanApplication;
import org.springframework.stereotype.Component;

@Component
public class CollateralLoanApprovalExecutionGuard {

    private final CollateralLoanReviewGate collateralLoanReviewGate;

    public CollateralLoanApprovalExecutionGuard(CollateralLoanReviewGate collateralLoanReviewGate) {
        this.collateralLoanReviewGate = collateralLoanReviewGate;
    }

    public void requireExecutionSupported(LoanApplication loanApplication) {
        collateralLoanReviewGate.requireProgressionAllowed(loanApplication);
    }
}
