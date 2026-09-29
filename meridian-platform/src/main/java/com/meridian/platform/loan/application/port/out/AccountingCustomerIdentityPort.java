package com.meridian.platform.loan.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface AccountingCustomerIdentityPort {
    Optional<AccountingCustomerIdentitySnapshot> findByCustomerId(UUID customerId);
}
