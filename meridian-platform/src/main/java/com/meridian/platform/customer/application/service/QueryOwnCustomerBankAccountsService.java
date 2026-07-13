package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerBankAccountsUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QueryOwnCustomerBankAccountsService implements QueryOwnCustomerBankAccountsUseCase {

    private final CustomerRepository customerRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerMapper customerMapper;

    public QueryOwnCustomerBankAccountsService(
            CustomerRepository customerRepository,
            CurrentUserProvider currentUserProvider,
            CustomerMapper customerMapper
    ) {
        this.customerRepository = customerRepository;
        this.currentUserProvider = currentUserProvider;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerBankAccountDto> getOwnBankAccounts() {
        UUID customerId = currentUserProvider.currentUser().requireCustomerId();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer was not found."
                ));
        if (!customer.isActive()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must be active for this operation."
            );
        }
        return customer.bankAccounts().stream()
                .map(customerMapper::toBankAccountDto)
                .toList();
    }
}
