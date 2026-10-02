package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.ApprovalCustomerIdentity;
import com.meridian.platform.customer.application.port.in.QueryApprovalCustomerIdentityUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryApprovalCustomerIdentityService implements QueryApprovalCustomerIdentityUseCase {
    private final CustomerRepository customers;

    public QueryApprovalCustomerIdentityService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApprovalCustomerIdentity> findByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        return customers.findById(customerId).map(customer -> {
            var profile = customer.profile();
            if (!customerId.equals(customer.id()) || profile == null
                    || !customerId.equals(profile.customerId())
                    || customer.customerNumber() == null || customer.customerNumber().isBlank()
                    || profile.fullName() == null || profile.fullName().isBlank()) {
                throw new BusinessStateConflictException(
                        "SYSTEM_STATE_CONFLICT", "Approval Customer identity is inconsistent.");
            }
            return new ApprovalCustomerIdentity(customer.customerNumber(), profile.fullName());
        });
    }
}
