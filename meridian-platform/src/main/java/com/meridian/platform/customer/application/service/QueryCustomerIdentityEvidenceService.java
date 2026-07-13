package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.customer.application.port.in.QueryCustomerIdentityEvidenceUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryCustomerIdentityEvidenceService implements QueryCustomerIdentityEvidenceUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerSensitiveValueProtector sensitiveValueProtector;

    public QueryCustomerIdentityEvidenceService(
            CustomerRepository customerRepository,
            CustomerSensitiveValueProtector sensitiveValueProtector
    ) {
        this.customerRepository = customerRepository;
        this.sensitiveValueProtector = sensitiveValueProtector;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerIdentityEvidenceSnapshot> findIdentityEvidenceByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        return customerRepository.findById(customerId)
                .map(this::toIdentityEvidenceSnapshot);
    }

    private CustomerIdentityEvidenceSnapshot toIdentityEvidenceSnapshot(Customer customer) {
        boolean profileComplete = customer.hasCompleteProfile();
        return new CustomerIdentityEvidenceSnapshot(
                customer.id(),
                customer.isActive(),
                profileComplete,
                profileComplete ? sensitiveValueProtector.reveal(customer.profile().identityReference()) : null
        );
    }
}