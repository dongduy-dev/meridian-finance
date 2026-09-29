package com.meridian.platform.customer.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface QueryAccountingCustomerIdentityUseCase {
    Optional<AccountingCustomerIdentity> findByCustomerId(UUID customerId);
}
