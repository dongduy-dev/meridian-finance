package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QueryOwnCustomerService implements QueryOwnCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerMapper customerMapper;

    public QueryOwnCustomerService(
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
    public CustomerDto getOwnCustomer() {
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
        return customerMapper.toCustomerDto(customer);
    }
}
