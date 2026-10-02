package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryAccountingCustomerIdentityUseCase;
import com.meridian.platform.loan.application.port.out.AccountingCustomerIdentityPort;
import com.meridian.platform.loan.application.port.out.AccountingCustomerIdentitySnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AccountingCustomerIdentityAdapter implements AccountingCustomerIdentityPort {
    private final QueryAccountingCustomerIdentityUseCase customers;

    public AccountingCustomerIdentityAdapter(QueryAccountingCustomerIdentityUseCase customers) {
        this.customers = customers;
    }

    @Override
    public Optional<AccountingCustomerIdentitySnapshot> findByCustomerId(UUID customerId) {
        return customers.findByCustomerId(customerId).map(identity ->
                new AccountingCustomerIdentitySnapshot(identity.customerNumber(), identity.fullName(), identity.phoneNumber()));
    }
}
