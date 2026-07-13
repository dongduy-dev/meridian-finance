package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCustomerIdentityEvidenceServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);

    @Test
    void revealsIdentityReferenceOnlyThroughInternalSnapshot() {
        FakeSensitiveValueProtector sensitiveValueProtector = new FakeSensitiveValueProtector();
        QueryCustomerIdentityEvidenceService service = new QueryCustomerIdentityEvidenceService(
                new FakeCustomerRepository(customer(CustomerStatus.ACTIVE, ProfileCompletionStatus.COMPLETE, completeProfile())),
                sensitiveValueProtector
        );

        CustomerIdentityEvidenceSnapshot snapshot = service.findIdentityEvidenceByCustomerId(CUSTOMER_ID).orElseThrow();

        assertTrue(snapshot.active());
        assertTrue(snapshot.profileComplete());
        assertEquals("IDREF-MER-001", snapshot.identityReference());
        assertEquals(1, sensitiveValueProtector.revealCalls);
        assertFalse(snapshot.toString().contains("IDREF-MER-001"));
    }

    @Test
    void doesNotRevealIdentityReferenceWhenProfileIsIncomplete() {
        FakeSensitiveValueProtector sensitiveValueProtector = new FakeSensitiveValueProtector();
        QueryCustomerIdentityEvidenceService service = new QueryCustomerIdentityEvidenceService(
                new FakeCustomerRepository(customer(CustomerStatus.ACTIVE, ProfileCompletionStatus.INCOMPLETE, null)),
                sensitiveValueProtector
        );

        CustomerIdentityEvidenceSnapshot snapshot = service.findIdentityEvidenceByCustomerId(CUSTOMER_ID).orElseThrow();

        assertTrue(snapshot.active());
        assertFalse(snapshot.profileComplete());
        assertNull(snapshot.identityReference());
        assertEquals(0, sensitiveValueProtector.revealCalls);
    }

    @Test
    void returnsEmptyWhenCustomerIsMissing() {
        QueryCustomerIdentityEvidenceService service = new QueryCustomerIdentityEvidenceService(
                new FakeCustomerRepository(null),
                new FakeSensitiveValueProtector()
        );

        assertTrue(service.findIdentityEvidenceByCustomerId(CUSTOMER_ID).isEmpty());
    }

    private static Customer customer(
            CustomerStatus status,
            ProfileCompletionStatus profileCompletionStatus,
            CustomerProfile profile
    ) {
        return new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                status,
                VerificationStatus.UNVERIFIED,
                profileCompletionStatus,
                profile,
                List.<CustomerBankAccount>of(),
                NOW,
                NOW
        );
    }

    private static CustomerProfile completeProfile() {
        return new CustomerProfile(
                UUID.randomUUID(),
                CUSTOMER_ID,
                "Customer Demo",
                new ProtectedSensitiveValue("cipher:IDREF-MER-001", "hmac:IDREF-MER-001", "-001"),
                "0901234567",
                "1 Meridian Street",
                "SALARIED",
                "Meridian Partner Co",
                true,
                true,
                NOW,
                NOW
        );
    }

    private static class FakeSensitiveValueProtector implements CustomerSensitiveValueProtector {

        private int revealCalls;

        @Override
        public ProtectedSensitiveValue protectIdentityReference(String identityReference) {
            return new ProtectedSensitiveValue("cipher:" + identityReference, "hmac:" + identityReference, "-001");
        }

        @Override
        public ProtectedSensitiveValue protectBankAccountNumber(String bankCode, String accountNumber) {
            return new ProtectedSensitiveValue("cipher:" + accountNumber, "hmac:" + bankCode + ":" + accountNumber, "7890");
        }

        @Override
        public String reveal(ProtectedSensitiveValue protectedValue) {
            revealCalls++;
            return protectedValue.ciphertext().substring("cipher:".length());
        }
    }

    private static class FakeCustomerRepository implements CustomerRepository {

        private final Customer customer;

        private FakeCustomerRepository(Customer customer) {
            this.customer = customer;
        }

        @Override
        public Customer save(Customer customer) {
            return customer;
        }

        @Override
        public Optional<Customer> findById(UUID customerId) {
            return Optional.ofNullable(customer)
                    .filter(value -> value.id().equals(customerId));
        }

        @Override
        public Optional<Customer> findByIdForUpdate(UUID customerId) {
            return findById(customerId);
        }
    }
}