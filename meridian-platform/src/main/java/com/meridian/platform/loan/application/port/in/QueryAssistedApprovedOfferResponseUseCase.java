package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.AssistedOfferResponseCaseDto;

import java.util.UUID;

public interface QueryAssistedApprovedOfferResponseUseCase {

    AssistedOfferResponseCaseDto query(UUID loanApplicationId);
}
