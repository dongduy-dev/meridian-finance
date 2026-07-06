package com.meridian.platform.loan.application.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ExpireApprovedOfferUseCase {

    void expireDueOffer(UUID loanApplicationId, LocalDateTime now);
}
