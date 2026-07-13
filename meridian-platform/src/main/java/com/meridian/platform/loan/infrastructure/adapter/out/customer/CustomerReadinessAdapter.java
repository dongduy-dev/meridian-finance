package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryCustomerReadinessUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerReadinessAdapter implements CustomerReadinessPort {

    private final QueryCustomerReadinessUseCase queryCustomerReadinessUseCase;

    public CustomerReadinessAdapter(QueryCustomerReadinessUseCase queryCustomerReadinessUseCase) {
        this.queryCustomerReadinessUseCase = queryCustomerReadinessUseCase;
    }

    @Override
    public Optional<CustomerReadinessSnapshot> findReadinessByCustomerId(UUID customerId) {
        return queryCustomerReadinessUseCase.findReadinessByCustomerId(customerId)
                .map(snapshot -> new CustomerReadinessSnapshot(
                        snapshot.customerId(),
                        snapshot.active(),
                        snapshot.profileComplete(),
                        snapshot.hasPrimaryActiveBankAccount(),
                        snapshot.verificationStatus()
                ));
    }
}