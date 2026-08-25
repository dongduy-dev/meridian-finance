package com.meridian.platform.identity.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.RegisterCustomerUseCase;
import com.meridian.platform.identity.application.port.out.CustomerRegistrationPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerRegistrationAdapter implements CustomerRegistrationPort {

    private final RegisterCustomerUseCase registerCustomerUseCase;

    public CustomerRegistrationAdapter(RegisterCustomerUseCase registerCustomerUseCase) {
        this.registerCustomerUseCase = registerCustomerUseCase;
    }

    @Override
    public UUID registerCustomer() {
        return registerCustomerUseCase.registerCustomer().customerId();
    }
}
