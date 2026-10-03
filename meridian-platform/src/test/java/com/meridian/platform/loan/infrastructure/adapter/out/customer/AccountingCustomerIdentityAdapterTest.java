package com.meridian.platform.loan.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.AccountingCustomerIdentity;
import com.meridian.platform.customer.application.port.in.QueryAccountingCustomerIdentityUseCase;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountingCustomerIdentityAdapterTest {
    @Test
    void propagatesCurrentContactWithoutRetainingAnEarlierPhone() {
        UUID id = UUID.randomUUID();
        var customers = mock(QueryAccountingCustomerIdentityUseCase.class);
        var adapter = new AccountingCustomerIdentityAdapter(customers);
        when(customers.findByCustomerId(id)).thenReturn(Optional.of(
                new AccountingCustomerIdentity("CUS-001", "Ari Customer", "0901234567")));
        var result = adapter.findByCustomerId(id).orElseThrow();
        assertEquals("CUS-001", result.customerNumber());
        assertEquals("Ari Customer", result.fullName());
        assertEquals("0901234567", result.phoneNumber());
        assertEquals("AccountingCustomerIdentitySnapshot[identity=redacted]", result.toString());
        when(customers.findByCustomerId(id)).thenReturn(Optional.of(
                new AccountingCustomerIdentity("CUS-001", "Ari Customer", "0912345678")));
        assertEquals("0912345678", adapter.findByCustomerId(id).orElseThrow().phoneNumber());
        when(customers.findByCustomerId(id)).thenReturn(Optional.empty());
        assertTrue(adapter.findByCustomerId(id).isEmpty());
    }
}
