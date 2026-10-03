package com.meridian.platform.loan.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface CustomerReadinessPort {

    Optional<CustomerReadinessSnapshot> findReadinessByCustomerId(UUID customerId);
    default Optional<CustomerReadinessSnapshot> findOriginationReadinessForUpdate(UUID customerId) {
        return findReadinessByCustomerId(customerId);
    }
}