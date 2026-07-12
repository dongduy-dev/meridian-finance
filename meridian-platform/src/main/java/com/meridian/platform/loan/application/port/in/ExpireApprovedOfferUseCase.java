package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.ExpiryDiscoveryTrigger;

import java.util.UUID;

public interface ExpireApprovedOfferUseCase {

    void expireDueOffer(
            UUID loanApplicationId,
            BusinessOperationContext operationContext,
            ExpiryDiscoveryTrigger discoveryTrigger
    );
}
