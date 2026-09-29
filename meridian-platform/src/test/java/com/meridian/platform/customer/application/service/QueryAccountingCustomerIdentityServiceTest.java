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
    void returnsOnlyNumberAndNameFromCustomerOwnedRecord() {
        UUID id = UUID.randomUUID();
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = mock(Customer.class);
        CustomerProfile profile = mock(CustomerProfile.class);
        when(repository.findById(id)).thenReturn(Optional.of(customer));
        when(customer.customerNumber()).thenReturn("CUS-001");
        when(customer.profile()).thenReturn(profile);
        when(profile.fullName()).thenReturn("Ari Customer");

        var identity = new QueryAccountingCustomerIdentityService(repository).findByCustomerId(id).orElseThrow();
        assertEquals("CUS-001", identity.customerNumber());
        assertEquals("Ari Customer", identity.fullName());
        assertEquals(2, identity.getClass().getRecordComponents().length);
    }

    @Test
    void missingCustomerReturnsNoIdentity() {
        UUID id = UUID.randomUUID();
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertTrue(new QueryAccountingCustomerIdentityService(repository).findByCustomerId(id).isEmpty());
    }
}
