package com.meridian.platform.loan.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface CustomerLoanCaseContactPort {
    Optional<CustomerLoanCaseContactSnapshot> findByCustomerId(UUID customerId);
}
