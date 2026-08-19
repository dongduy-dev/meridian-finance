package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CollateralLoanVerificationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanVerificationStartDto;
import com.meridian.platform.loan.application.dto.CompleteCollateralLoanVerificationRequest;

import java.util.UUID;

public interface ManageCollateralLoanVerificationUseCase {

    CollateralLoanVerificationStartDto startManualVerification(UUID loanApplicationId);

    CollateralLoanVerificationDto completeManualVerification(
            UUID loanApplicationId,
            CompleteCollateralLoanVerificationRequest request
    );
}
