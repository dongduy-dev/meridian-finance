package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;

import java.util.UUID;

public interface StartAssistedCollateralLoanUseCase {

    AssistedOriginationCaseDto submit(
            UUID assistedOriginationCaseId,
            CollateralLoanApplicationRequest request
    );
}
