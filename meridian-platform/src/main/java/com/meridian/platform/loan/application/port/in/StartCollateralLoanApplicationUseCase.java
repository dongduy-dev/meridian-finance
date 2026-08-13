package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;

public interface StartCollateralLoanApplicationUseCase {

    CollateralLoanApplicationDto startCollateralLoanApplication(CollateralLoanApplicationRequest request);
}
