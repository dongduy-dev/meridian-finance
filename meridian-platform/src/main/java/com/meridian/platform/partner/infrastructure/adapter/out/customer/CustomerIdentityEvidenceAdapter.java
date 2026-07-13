package com.meridian.platform.partner.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryCustomerIdentityEvidenceUseCase;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerIdentityEvidenceAdapter implements CustomerIdentityEvidencePort {

    private final QueryCustomerIdentityEvidenceUseCase queryCustomerIdentityEvidenceUseCase;

    public CustomerIdentityEvidenceAdapter(QueryCustomerIdentityEvidenceUseCase queryCustomerIdentityEvidenceUseCase) {
        this.queryCustomerIdentityEvidenceUseCase = queryCustomerIdentityEvidenceUseCase;
    }

    @Override
    public Optional<CustomerIdentityEvidenceSnapshot> findIdentityEvidenceByCustomerId(UUID customerId) {
        return queryCustomerIdentityEvidenceUseCase.findIdentityEvidenceByCustomerId(customerId)
                .map(snapshot -> new CustomerIdentityEvidenceSnapshot(
                        snapshot.customerId(),
                        snapshot.active(),
                        snapshot.profileComplete(),
                        snapshot.identityReference()
                ));
    }
}