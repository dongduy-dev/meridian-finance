package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;

import java.util.UUID;

public interface RespondToApprovedOfferUseCase {

    ApprovedOfferActionResult acceptOffer(UUID loanApplicationId);

    ApprovedOfferActionResult declineOffer(UUID loanApplicationId);
}
