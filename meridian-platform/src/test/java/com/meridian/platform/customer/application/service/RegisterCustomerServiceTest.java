package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterCustomerServiceTest {

    @Test
    void createsAnActiveUnverifiedIncompleteCustomerWithoutPlaceholderProfileOrAccounts() {
        CapturingCustomerRepository customers = new CapturingCustomerRepository();
        RegisterCustomerService service = new RegisterCustomerService(
                customers,
                () -> 42L,
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC)
        );

        UUID customerId = service.registerCustomer().customerId();

        assertEquals(customerId, customers.saved.id());
        assertEquals("CUS-000000042", customers.saved.customerNumber());
        assertEquals(CustomerStatus.ACTIVE, customers.saved.status());
        assertEquals(VerificationStatus.UNVERIFIED, customers.saved.verificationStatus());
        assertEquals(ProfileCompletionStatus.INCOMPLETE, customers.saved.profileCompletionStatus());
        assertNull(customers.saved.profile());
        assertTrue(customers.saved.bankAccounts().isEmpty());
    }

    private static final class CapturingCustomerRepository implements CustomerRepository {

        private Customer saved;

        @Override
        public Customer save(Customer customer) {
            saved = customer;
            return customer;
        }

        @Override
        public Optional<Customer> findById(UUID customerId) {
            return Optional.empty();
        }

        @Override
        public Optional<Customer> findByIdForUpdate(UUID customerId) {
            return Optional.empty();
        }

        @Override
        public boolean existsByIdentityReferenceFingerprintAndCustomerIdNot(
                String fingerprint,
                UUID customerId
        ) {
            return false;
        }
    }
}
