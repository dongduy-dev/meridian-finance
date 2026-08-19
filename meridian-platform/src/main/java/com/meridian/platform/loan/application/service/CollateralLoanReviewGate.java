package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Component;

@Component
public class CollateralLoanReviewGate {

    private final CollateralLoanVerificationRepository verificationRepository;

    public CollateralLoanReviewGate(CollateralLoanVerificationRepository verificationRepository) {
        this.verificationRepository = verificationRepository;
    }

    public void requireProgressionAllowed(LoanApplication loanApplication) {
        if (loanApplication.productCode() != ProductCode.COLLATERAL_LOAN) {
            return;
        }

        CollateralLoanVerification verification = verificationRepository
                .findByLoanApplicationId(loanApplication.id())
                .orElseThrow(() -> new BusinessStateConflictException(
                        "COLLATERAL_VERIFICATION_REQUIRED",
                        "Collateral Loan verification evidence is required before review progression."
                ));

        switch (verification.productVerificationResult()) {
            case VERIFIED -> {
            }
            case PENDING_MANUAL_REVIEW -> throw new BusinessRuleViolationException(
                    "PRODUCT_VERIFICATION_PENDING",
                    "Collateral Loan verification must complete before review progression."
            );
            case FAILED -> throw new BusinessRuleViolationException(
                    "PRODUCT_VERIFICATION_FAILED",
                    "Collateral Loan verification failed."
            );
            case REQUIRES_MORE_INFORMATION -> throw new BusinessRuleViolationException(
                    "PRODUCT_VERIFICATION_REQUIRES_MORE_INFORMATION",
                    "Collateral Loan verification requires more information."
            );
        }
    }
}
