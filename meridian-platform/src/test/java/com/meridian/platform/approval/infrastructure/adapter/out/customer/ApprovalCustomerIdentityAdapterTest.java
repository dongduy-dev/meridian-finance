package com.meridian.platform.approval.infrastructure.adapter.out.customer;

import com.meridian.platform.customer.application.port.in.ApprovalCustomerIdentity;
import com.meridian.platform.customer.application.port.in.QueryApprovalCustomerIdentityUseCase;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalCustomerIdentityAdapterTest {
    @Test
    void translatesThePurposeSpecificContractWithoutCustomerPersistence() {
        UUID id = UUID.randomUUID();
        var customers = mock(QueryApprovalCustomerIdentityUseCase.class);
        var adapter = new ApprovalCustomerIdentityAdapter(customers);
        when(customers.findByCustomerId(id)).thenReturn(Optional.of(new ApprovalCustomerIdentity("CUS-001", "Ari Customer")));
        var result = adapter.findByCustomerId(id).orElseThrow();
        assertEquals("CUS-001", result.customerNumber());
        assertEquals("Ari Customer", result.fullName());
        assertEquals("ApprovalCustomerIdentitySnapshot[identity=redacted]", result.toString());
        verify(customers).findByCustomerId(id);
        when(customers.findByCustomerId(id)).thenReturn(Optional.empty());
        assertTrue(adapter.findByCustomerId(id).isEmpty());
    }
}
