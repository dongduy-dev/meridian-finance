package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.QueryServicingCustomerContextUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryServicingCustomerContextService implements QueryServicingCustomerContextUseCase {
    private final CustomerRepository customers;

    public QueryServicingCustomerContextService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessIdentity> findBusinessIdentityByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId);
        return findCustomer(customerId).map(customer -> {
            var profile = requireProfile(customerId, customer);
            return new BusinessIdentity(customer.customerNumber(), profile.fullName());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrentContact> findCurrentContactByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId);
        return findCustomer(customerId).map(customer -> {
            var profile = requireProfile(customerId, customer);
            String phone = profile.phoneNumber();
            if (phone == null || phone.isBlank()) throw conflict();
            return new CurrentContact(customer.customerNumber(), profile.fullName(), phone);
        });
    }

    private Optional<Customer> findCustomer(UUID customerId) {
        try {
            return customers.findById(customerId);
        } catch (IllegalArgumentException | NullPointerException invalidSource) {
            throw conflict();
        }
    }

    private static CustomerProfile requireProfile(UUID customerId, Customer customer) {
        var profile = customer.profile();
        if (!customerId.equals(customer.id()) || profile == null
                || !customerId.equals(profile.customerId())
                || customer.customerNumber() == null || customer.customerNumber().isBlank()
                || profile.fullName() == null || profile.fullName().isBlank()) throw conflict();
        return profile;
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException("SYSTEM_STATE_CONFLICT",
                "Servicing Customer context is inconsistent.");
    }
}
