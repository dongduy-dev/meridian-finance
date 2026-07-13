package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryOwnCustomerServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);

    @Test
    void returnsOwnCustomerWithoutSensitiveIdentityFields() {
        QueryOwnCustomerService service = new QueryOwnCustomerService(
                new FakeCustomerRepository(Optional.of(customer(CustomerStatus.ACTIVE))),
                new FixedCurrentUserProvider(),
                new CustomerMapper()
        );

        CustomerDto result = service.getOwnCustomer();

        assertEquals(CUSTOMER_ID, result.customerId());
        assertEquals("CUS-000000001", result.customerNumber());
        assertEquals("INCOMPLETE", result.profileCompletionStatus());
    }

    @Test
    void rejectsMissingCustomer() {
        QueryOwnCustomerService service = new QueryOwnCustomerService(
                new FakeCustomerRepository(Optional.empty()),
                new FixedCurrentUserProvider(),
                new CustomerMapper()
        );

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, service::getOwnCustomer);

        assertEquals("CUSTOMER_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void rejectsInactiveCustomer() {
        QueryOwnCustomerService service = new QueryOwnCustomerService(
                new FakeCustomerRepository(Optional.of(customer(CustomerStatus.SUSPENDED))),
                new FixedCurrentUserProvider(),
                new CustomerMapper()
        );

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                service::getOwnCustomer
        );

        assertEquals("CUSTOMER_NOT_ACTIVE", exception.getErrorCode());
    }

    private static Customer customer(CustomerStatus status) {
        return new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                status,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.INCOMPLETE,
                null,
                List.of(),
                NOW,
                NOW
        );
    }

    private static class FixedCurrentUserProvider implements CurrentUserProvider {

        @Override
        public AuthenticatedUser currentUser() {
            return new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000301"),
                    "customer.demo@meridian.local",
                    "CUSTOMER",
                    CUSTOMER_ID,
                    Set.of("CUSTOMER"),
                    Set.of("customer:profile:read:own")
            );
        }
    }

    private static class FakeCustomerRepository implements CustomerRepository {

        private final Optional<Customer> customer;

        private FakeCustomerRepository(Optional<Customer> customer) {
            this.customer = customer;
        }

        @Override
        public Customer save(Customer customer) {
            return customer;
        }

        @Override
        public Optional<Customer> findById(UUID customerId) {
            return customer.filter(value -> value.id().equals(customerId));
        }

        @Override
        public Optional<Customer> findByIdForUpdate(UUID customerId) {
            return findById(customerId);
        }
    }
}
