package com.meridian.platform.approval.infrastructure.adapter.out.customer;

import com.meridian.platform.approval.application.port.out.ApprovalCustomerIdentityPort;
import com.meridian.platform.approval.application.port.out.ApprovalCustomerIdentitySnapshot;
import com.meridian.platform.customer.application.port.in.QueryApprovalCustomerIdentityUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalCustomerIdentityAdapter implements ApprovalCustomerIdentityPort {
    private final QueryApprovalCustomerIdentityUseCase customers;

    public ApprovalCustomerIdentityAdapter(QueryApprovalCustomerIdentityUseCase customers) {
        this.customers = customers;
    }

    @Override
    public Optional<ApprovalCustomerIdentitySnapshot> findByCustomerId(UUID customerId) {
        return customers.findByCustomerId(customerId).map(identity ->
                new ApprovalCustomerIdentitySnapshot(identity.customerNumber(), identity.fullName()));
    }
}
