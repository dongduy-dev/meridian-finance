package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.RegisterCustomerUseCase;
import com.meridian.platform.customer.application.port.in.RegisteredCustomer;
import com.meridian.platform.customer.application.port.out.CustomerNumberSequenceRepository;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RegisterCustomerService implements RegisterCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerNumberSequenceRepository customerNumberSequenceRepository;
    private final Clock clock;

    public RegisterCustomerService(
            CustomerRepository customerRepository,
            CustomerNumberSequenceRepository customerNumberSequenceRepository,
            Clock clock
    ) {
        this.customerRepository = Objects.requireNonNull(customerRepository);
        this.customerNumberSequenceRepository = Objects.requireNonNull(customerNumberSequenceRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public RegisteredCustomer registerCustomer() {
        LocalDateTime now = LocalDateTime.now(clock);
        long sequence = customerNumberSequenceRepository.nextCustomerNumberSequence();
        Customer customer = customerRepository.save(new Customer(
                UUID.randomUUID(),
                "CUS-%09d".formatted(sequence),
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.INCOMPLETE,
                null,
                List.of(),
                now,
                now
        ));
        return new RegisteredCustomer(customer.id());
    }
}
