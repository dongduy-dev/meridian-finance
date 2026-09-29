package com.meridian.platform.identity.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.VerifyCustomerDigitalAccessUseCase;
import com.meridian.platform.identity.application.port.out.CustomerDigitalAccessVerificationPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerDigitalAccessVerificationAdapter implements CustomerDigitalAccessVerificationPort {
    private final VerifyCustomerDigitalAccessUseCase customer;

    public CustomerDigitalAccessVerificationAdapter(VerifyCustomerDigitalAccessUseCase customer) {
        this.customer = customer;
    }

    @Override
    public void requireExistingCustomer(UUID customerId) {
        customer.requireExistingCustomer(customerId);
    }

    @Override
    public VerifiedCustomer verifyForActivation(UUID customerId, String identityReference) {
        var candidate = customer.verifyForActivation(customerId, identityReference);
        return new VerifiedCustomer(candidate.customerId(), candidate.displayName());
    }
}
