package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.CustomerReadinessSnapshot;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerBankAccountStatus;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCustomerReadinessServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);

    @Test
    void returnsSafeReadinessFactsForReadyUnverifiedCustomer() {
        QueryCustomerReadinessService service = new QueryCustomerReadinessService(new FakeCustomerRepository(
                customer(
                        CustomerStatus.ACTIVE,
                        VerificationStatus.UNVERIFIED,
                        ProfileCompletionStatus.COMPLETE,
                        completeProfile(),
                        List.of(bankAccount(true, CustomerBankAccountStatus.ACTIVE))
                )
        ));

        CustomerReadinessSnapshot snapshot = service.findReadinessByCustomerId(CUSTOMER_ID).orElseThrow();

        assertEquals(CUSTOMER_ID, snapshot.customerId());
        assertTrue(snapshot.active());
        assertTrue(snapshot.profileComplete());
        assertTrue(snapshot.hasPrimaryActiveBankAccount());
        assertEquals("UNVERIFIED", snapshot.verificationStatus());
        assertFalse(snapshot.toString().contains("IDREF-MER-001"));
        assertFalse(snapshot.toString().contains("1234567890"));
    }

    @Test
    void returnsBlockingFactsForInactiveIncompleteCustomerWithoutPrimaryBankAccount() {
        QueryCustomerReadinessService service = new QueryCustomerReadinessService(new FakeCustomerRepository(
                customer(
                        CustomerStatus.SUSPENDED,
                        VerificationStatus.REJECTED,
                        ProfileCompletionStatus.INCOMPLETE,
                        null,
                        List.of()
                )
        ));

        CustomerReadinessSnapshot snapshot = service.findReadinessByCustomerId(CUSTOMER_ID).orElseThrow();

        assertFalse(snapshot.active());
        assertFalse(snapshot.profileComplete());
        assertFalse(snapshot.hasPrimaryActiveBankAccount());
        assertEquals("REJECTED", snapshot.verificationStatus());
    }

    @Test
    void returnsEmptyWhenCustomerIsMissing() {
        QueryCustomerReadinessService service = new QueryCustomerReadinessService(new FakeCustomerRepository(null));

        assertTrue(service.findReadinessByCustomerId(CUSTOMER_ID).isEmpty());
    }

    private static Customer customer(
            CustomerStatus status,
            VerificationStatus verificationStatus,
            ProfileCompletionStatus profileCompletionStatus,
            CustomerProfile profile,
            List<CustomerBankAccount> bankAccounts
    ) {
        return new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                status,
                verificationStatus,
                profileCompletionStatus,
                profile,
                bankAccounts,
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

    private static CustomerBankAccount bankAccount(boolean primaryAccount, CustomerBankAccountStatus status) {
        return new CustomerBankAccount(
                UUID.randomUUID(),
                CUSTOMER_ID,
                "VCB",
                "Vietcombank",
                "Customer Demo",
                new ProtectedSensitiveValue("cipher:1234567890", "hmac:VCB:1234567890", "7890"),
                status,
                primaryAccount,
                NOW,
                NOW,
                status == CustomerBankAccountStatus.DEACTIVATED ? NOW : null
        );
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