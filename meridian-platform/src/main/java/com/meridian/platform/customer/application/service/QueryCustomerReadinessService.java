package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.CustomerReadinessSnapshot;
import com.meridian.platform.customer.application.port.in.QueryCustomerReadinessUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryCustomerReadinessService implements QueryCustomerReadinessUseCase {

    private final CustomerRepository customerRepository;

    public QueryCustomerReadinessService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerReadinessSnapshot> findReadinessByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        return customerRepository.findById(customerId)
                .map(this::toReadinessSnapshot);
    }

    private CustomerReadinessSnapshot toReadinessSnapshot(Customer customer) {
        return new CustomerReadinessSnapshot(
                customer.id(),
                customer.isActive(),
                customer.profileCompletionStatus() == ProfileCompletionStatus.COMPLETE,
                customer.bankAccounts().stream().anyMatch(CustomerBankAccount::isPrimaryActive),
                customer.verificationStatus().name()
        );
    }
}