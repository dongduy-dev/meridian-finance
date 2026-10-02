package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryAccountingCustomerIdentityServiceTest {
    @Test
    void returnsOnlyNumberNameAndCurrentPhoneFromCustomerOwnedRecord() {
        UUID id = UUID.randomUUID();
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = mock(Customer.class);
        CustomerProfile profile = mock(CustomerProfile.class);
        when(repository.findById(id)).thenReturn(Optional.of(customer));
        when(customer.customerNumber()).thenReturn("CUS-001");
        when(customer.profile()).thenReturn(profile);
        when(profile.fullName()).thenReturn("Ari Customer");
        when(profile.phoneNumber()).thenReturn("0901234567");

        var identity = new QueryAccountingCustomerIdentityService(repository).findByCustomerId(id).orElseThrow();
        assertEquals("CUS-001", identity.customerNumber());
        assertEquals("Ari Customer", identity.fullName());
        assertEquals("0901234567", identity.phoneNumber());
        assertEquals(3, identity.getClass().getRecordComponents().length);
        assertEquals("AccountingCustomerIdentity[identity=redacted]", identity.toString());
        when(profile.phoneNumber()).thenReturn("0912345678");
        assertEquals("0912345678", new QueryAccountingCustomerIdentityService(repository)
                .findByCustomerId(id).orElseThrow().phoneNumber());
    }

    @Test
    void missingCustomerReturnsNoIdentity() {
        UUID id = UUID.randomUUID();
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertTrue(new QueryAccountingCustomerIdentityService(repository).findByCustomerId(id).isEmpty());
    }
}
