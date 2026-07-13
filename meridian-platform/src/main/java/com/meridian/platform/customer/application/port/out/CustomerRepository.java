package com.meridian.platform.customer.application.port.out;

import com.meridian.platform.customer.domain.model.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID customerId);

    Optional<Customer> findByIdForUpdate(UUID customerId);

    boolean existsByIdentityReferenceFingerprintAndCustomerIdNot(String fingerprint, UUID customerId);
}