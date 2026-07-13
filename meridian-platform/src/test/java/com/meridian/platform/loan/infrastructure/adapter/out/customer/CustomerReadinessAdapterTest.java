package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryCustomerReadinessUseCase;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerReadinessAdapterTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Test
    void mapsCustomerPublicSnapshotToLoanOwnedSnapshot() {
        FakeQueryCustomerReadinessUseCase customerReadinessUseCase = new FakeQueryCustomerReadinessUseCase(
                Optional.of(new com.meridian.platform.customer.application.port.in.CustomerReadinessSnapshot(
                        CUSTOMER_ID,
                        true,
                        true,
                        true,
                        "UNVERIFIED"
                ))
        );
        CustomerReadinessAdapter adapter = new CustomerReadinessAdapter(customerReadinessUseCase);

        CustomerReadinessSnapshot snapshot = adapter.findReadinessByCustomerId(CUSTOMER_ID).orElseThrow();

        assertEquals(CUSTOMER_ID, snapshot.customerId());
        assertTrue(snapshot.active());
        assertTrue(snapshot.profileComplete());
        assertTrue(snapshot.hasPrimaryActiveBankAccount());
        assertEquals("UNVERIFIED", snapshot.verificationStatus());
    }

    @Test
    void returnsEmptyWhenCustomerContractHasNoSnapshot() {
        CustomerReadinessAdapter adapter = new CustomerReadinessAdapter(new FakeQueryCustomerReadinessUseCase(Optional.empty()));

        assertTrue(adapter.findReadinessByCustomerId(CUSTOMER_ID).isEmpty());
    }

    private static class FakeQueryCustomerReadinessUseCase implements QueryCustomerReadinessUseCase {

        private final Optional<com.meridian.platform.customer.application.port.in.CustomerReadinessSnapshot> snapshot;

        private FakeQueryCustomerReadinessUseCase(
                Optional<com.meridian.platform.customer.application.port.in.CustomerReadinessSnapshot> snapshot
        ) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<com.meridian.platform.customer.application.port.in.CustomerReadinessSnapshot> findReadinessByCustomerId(
                UUID customerId
        ) {
            return snapshot.filter(value -> value.customerId().equals(customerId));
        }
    }
}