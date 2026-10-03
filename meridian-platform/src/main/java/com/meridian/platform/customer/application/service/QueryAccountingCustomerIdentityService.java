package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.AccountingCustomerIdentity;
import com.meridian.platform.customer.application.port.in.QueryAccountingCustomerIdentityUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryAccountingCustomerIdentityService implements QueryAccountingCustomerIdentityUseCase {
    private final CustomerRepository customers;

    public QueryAccountingCustomerIdentityService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountingCustomerIdentity> findByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        return customers.findById(customerId).map(customer -> new AccountingCustomerIdentity(
                customer.customerNumber(),
                customer.profile() == null ? null : customer.profile().fullName(),
                customer.profile() == null ? null : customer.profile().phoneNumber()
        ));
    }
}
