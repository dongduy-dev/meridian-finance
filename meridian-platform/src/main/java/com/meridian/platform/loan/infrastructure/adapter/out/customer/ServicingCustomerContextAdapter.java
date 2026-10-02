package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryServicingCustomerContextUseCase;
import com.meridian.platform.loan.application.port.out.ServicingCustomerContextPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ServicingCustomerContextAdapter implements ServicingCustomerContextPort {
    private final QueryServicingCustomerContextUseCase customers;

    public ServicingCustomerContextAdapter(QueryServicingCustomerContextUseCase customers) {
        this.customers = customers;
    }

    @Override
    public Optional<BusinessIdentity> findBusinessIdentityByCustomerId(UUID customerId) {
        return customers.findBusinessIdentityByCustomerId(customerId).map(value ->
                new BusinessIdentity(value.customerNumber(), value.fullName()));
    }

    @Override
    public Optional<CurrentContact> findCurrentContactByCustomerId(UUID customerId) {
        return customers.findCurrentContactByCustomerId(customerId).map(value ->
                new CurrentContact(value.customerNumber(), value.fullName(), value.phoneNumber()));
    }
}
