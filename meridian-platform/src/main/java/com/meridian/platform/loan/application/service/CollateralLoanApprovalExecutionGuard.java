package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Component;

@Component
public class CollateralLoanApprovalExecutionGuard {

    public void requireExecutionSupported(LoanApplication loanApplication) {
        if (loanApplication.productCode() == ProductCode.COLLATERAL_LOAN) {
            throw new BusinessStateConflictException(
                    "PRODUCT_APPROVAL_EXECUTION_UNSUPPORTED",
                    "Collateral Loan approval execution is not supported."
            );
        }
    }
}
