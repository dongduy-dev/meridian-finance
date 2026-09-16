package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;
import com.meridian.platform.loan.application.dto.ChangeLoanProductActivationRequest;
import com.meridian.platform.loan.application.dto.UpdateLoanProductLimitsRequest;

public interface ManageLoanProductUseCase {
    AdminLoanProductDto updateLimits(String productCode, UpdateLoanProductLimitsRequest request);
    AdminLoanProductDto changeActivation(String productCode, ChangeLoanProductActivationRequest request);
}
