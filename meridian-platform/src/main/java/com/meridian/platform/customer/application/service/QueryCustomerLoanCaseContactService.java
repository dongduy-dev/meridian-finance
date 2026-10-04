package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.CustomerLoanCaseContact;
import com.meridian.platform.customer.application.port.in.QueryCustomerLoanCaseContactUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryCustomerLoanCaseContactService implements QueryCustomerLoanCaseContactUseCase {

    private final CustomerRepository customers;

    public QueryCustomerLoanCaseContactService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerLoanCaseContact> findByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        return customers.findById(customerId).map(QueryCustomerLoanCaseContactService::toContact);
    }

    private static CustomerLoanCaseContact toContact(Customer customer) {
        var profile = customer.profile();
        return new CustomerLoanCaseContact(
                customer.customerNumber(),
                profile == null ? null : profile.fullName(),
                profile == null ? null : profile.phoneNumber(),
                profile == null ? null : "****" + profile.identityReference().lastFour()
        );
    }
}
