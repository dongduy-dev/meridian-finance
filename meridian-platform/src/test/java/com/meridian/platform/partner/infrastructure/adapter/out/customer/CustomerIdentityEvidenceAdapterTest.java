package com.meridian.platform.partner.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryCustomerIdentityEvidenceUseCase;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerIdentityEvidenceAdapterTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Test
    void mapsCustomerPublicIdentityEvidenceToPartnerOwnedSnapshot() {
        FakeQueryCustomerIdentityEvidenceUseCase customerIdentityEvidenceUseCase = new FakeQueryCustomerIdentityEvidenceUseCase(
                Optional.of(new com.meridian.platform.customer.application.port.in.CustomerIdentityEvidenceSnapshot(
                        CUSTOMER_ID,
                        true,
                        true,
                        "IDREF-MER-001"
                ))
        );
        CustomerIdentityEvidenceAdapter adapter = new CustomerIdentityEvidenceAdapter(customerIdentityEvidenceUseCase);

        CustomerIdentityEvidenceSnapshot snapshot = adapter.findIdentityEvidenceByCustomerId(CUSTOMER_ID).orElseThrow();

        assertEquals(CUSTOMER_ID, snapshot.customerId());
        assertTrue(snapshot.active());
        assertTrue(snapshot.profileComplete());
        assertEquals("IDREF-MER-001", snapshot.identityReference());
        assertFalse(snapshot.toString().contains("IDREF-MER-001"));
    }

    @Test
    void returnsEmptyWhenCustomerContractHasNoIdentityEvidence() {
        CustomerIdentityEvidenceAdapter adapter = new CustomerIdentityEvidenceAdapter(
                new FakeQueryCustomerIdentityEvidenceUseCase(Optional.empty())
        );

        assertTrue(adapter.findIdentityEvidenceByCustomerId(CUSTOMER_ID).isEmpty());
    }

    private static class FakeQueryCustomerIdentityEvidenceUseCase implements QueryCustomerIdentityEvidenceUseCase {

        private final Optional<com.meridian.platform.customer.application.port.in.CustomerIdentityEvidenceSnapshot> snapshot;

        private FakeQueryCustomerIdentityEvidenceUseCase(
                Optional<com.meridian.platform.customer.application.port.in.CustomerIdentityEvidenceSnapshot> snapshot
        ) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<com.meridian.platform.customer.application.port.in.CustomerIdentityEvidenceSnapshot> findIdentityEvidenceByCustomerId(
                UUID customerId
        ) {
            return snapshot.filter(value -> value.customerId().equals(customerId));
        }
    }
}