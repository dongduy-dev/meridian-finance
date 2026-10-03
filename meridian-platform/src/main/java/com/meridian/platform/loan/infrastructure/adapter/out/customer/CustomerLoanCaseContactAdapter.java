package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryCustomerLoanCaseContactUseCase;
import com.meridian.platform.loan.application.port.out.CustomerLoanCaseContactPort;
import com.meridian.platform.loan.application.port.out.CustomerLoanCaseContactSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerLoanCaseContactAdapter implements CustomerLoanCaseContactPort {

    private final QueryCustomerLoanCaseContactUseCase customerContacts;

    public CustomerLoanCaseContactAdapter(QueryCustomerLoanCaseContactUseCase customerContacts) {
        this.customerContacts = customerContacts;
    }

    @Override
    public Optional<CustomerLoanCaseContactSnapshot> findByCustomerId(UUID customerId) {
        return customerContacts.findByCustomerId(customerId)
                .map(contact -> new CustomerLoanCaseContactSnapshot(
                        contact.customerNumber(), contact.fullName(), contact.phoneNumber(),
                        contact.maskedIdentityReference()
                ));
    }
}
