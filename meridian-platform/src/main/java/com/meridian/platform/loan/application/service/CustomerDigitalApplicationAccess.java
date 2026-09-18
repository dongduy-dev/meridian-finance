package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.shared.domain.exception.AuthorizationException;

final class CustomerDigitalApplicationAccess {

    private CustomerDigitalApplicationAccess() {
    }

    static void require(LoanApplication application) {
        if (application.originationChannel() != OriginationChannel.CUSTOMER_DIGITAL) {
            throw new AuthorizationException(
                    "CUSTOMER_DIRECT_ACTION_NOT_ALLOWED",
                    "Customer-direct action is not allowed for this Loan Application."
            );
        }
    }
}
