package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.ApprovedOfferDto;

import java.util.UUID;

public interface QueryApprovedOfferUseCase {

    ApprovedOfferDto getApprovedOffer(UUID loanApplicationId);
}
