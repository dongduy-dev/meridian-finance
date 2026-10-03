package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.RevealCustomerIdentityReferenceUseCase;
import com.meridian.platform.loan.application.port.out.CustomerIdentityReferenceRevealPort;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CustomerIdentityReferenceRevealAdapter implements CustomerIdentityReferenceRevealPort {
    private final RevealCustomerIdentityReferenceUseCase customerReveal;

    public CustomerIdentityReferenceRevealAdapter(RevealCustomerIdentityReferenceUseCase customerReveal) {
        this.customerReveal = customerReveal;
    }

    @Override
    public Result reveal(UUID customerId, UUID loanApplicationId) {
        var result = customerReveal.reveal(new RevealCustomerIdentityReferenceUseCase.Command(
                customerId, loanApplicationId));
        return new Result(result.identityReference());
    }
}
