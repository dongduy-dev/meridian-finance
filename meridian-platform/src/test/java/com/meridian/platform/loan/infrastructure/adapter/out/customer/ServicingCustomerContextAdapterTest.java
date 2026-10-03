package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.QueryServicingCustomerContextUseCase;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicingCustomerContextAdapterTest {
    @Test void mapsOnlyTheSelectedServicingPurposeFromTheExactCustomerContract() {
        var customers = mock(QueryServicingCustomerContextUseCase.class);
        var adapter = new ServicingCustomerContextAdapter(customers);
        UUID id = UUID.randomUUID();
        when(customers.findBusinessIdentityByCustomerId(id)).thenReturn(Optional.of(
                new QueryServicingCustomerContextUseCase.BusinessIdentity("CUS-001", "Ari Customer")));
        var identity = adapter.findBusinessIdentityByCustomerId(id).orElseThrow();
        assertEquals("CUS-001", identity.customerNumber());
        assertEquals("Ari Customer", identity.fullName());
        verify(customers, never()).findCurrentContactByCustomerId(any());
        when(customers.findCurrentContactByCustomerId(id)).thenReturn(Optional.of(
                new QueryServicingCustomerContextUseCase.CurrentContact("CUS-001", "Ari Customer", "0901234567")));
        assertEquals("0901234567", adapter.findCurrentContactByCustomerId(id).orElseThrow().phoneNumber());
        when(customers.findBusinessIdentityByCustomerId(id)).thenReturn(Optional.empty());
        when(customers.findCurrentContactByCustomerId(id)).thenReturn(Optional.empty());
        assertTrue(adapter.findBusinessIdentityByCustomerId(id).isEmpty());
        assertTrue(adapter.findCurrentContactByCustomerId(id).isEmpty());
        assertFalse(identity.toString().contains("Ari Customer"));
    }
}
