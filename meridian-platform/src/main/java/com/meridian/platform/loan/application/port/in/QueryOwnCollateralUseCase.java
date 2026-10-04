package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CustomerCollateralDto;

import java.util.UUID;

public interface QueryOwnCollateralUseCase {

    CustomerCollateralDto query(UUID loanApplicationId);
}
