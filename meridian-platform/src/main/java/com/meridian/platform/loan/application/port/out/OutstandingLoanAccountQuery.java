package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.ProductCode;

import java.util.UUID;

public interface OutstandingLoanAccountQuery {

    GuardResult inspect(UUID customerId, ProductCode productCode);

    enum GuardResult {
        CLEAR,
        OUTSTANDING_EXISTS,
        INCONSISTENT
    }
}
