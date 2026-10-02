package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.ApprovalCustomerIdentity;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryApprovalCustomerIdentityServiceTest {
    @Test
    void publishesOnlyNumberAndNameFromTheExactCustomerOwnedProfile() {
        UUID id = UUID.randomUUID();
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = mock(Customer.class);
        CustomerProfile profile = mock(CustomerProfile.class);
        when(repository.findById(id)).thenReturn(Optional.of(customer));
        when(customer.id()).thenReturn(id);
        when(customer.customerNumber()).thenReturn("CUS-001");
        when(customer.profile()).thenReturn(profile);
        when(profile.customerId()).thenReturn(id);
        when(profile.fullName()).thenReturn("Ari Customer");
        var service = new QueryApprovalCustomerIdentityService(repository);

        var result = service.findByCustomerId(id).orElseThrow();
        assertEquals("CUS-001", result.customerNumber());
        assertEquals("Ari Customer", result.fullName());
        assertEquals(Set.of("customerNumber", "fullName"), Arrays.stream(
                ApprovalCustomerIdentity.class.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet()));
        assertEquals("ApprovalCustomerIdentity[identity=redacted]", result.toString());
        verify(profile, never()).phoneNumber();
        verify(profile, never()).identityReference();

        when(profile.customerId()).thenReturn(UUID.randomUUID());
        assertConflict(() -> service.findByCustomerId(id));
        when(profile.customerId()).thenReturn(id);
        when(customer.id()).thenReturn(UUID.randomUUID());
        assertConflict(() -> service.findByCustomerId(id));
        when(customer.id()).thenReturn(id);
        when(profile.fullName()).thenReturn(" ");
        assertConflict(() -> service.findByCustomerId(id));
        when(profile.fullName()).thenReturn("Ari Customer");
        when(customer.customerNumber()).thenReturn(null);
        assertConflict(() -> service.findByCustomerId(id));
        when(customer.profile()).thenReturn(null);
        assertConflict(() -> service.findByCustomerId(id));
    }

    @Test
    void missingCustomerReturnsNoIdentity() {
        UUID id = UUID.randomUUID();
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertTrue(new QueryApprovalCustomerIdentityService(repository).findByCustomerId(id).isEmpty());
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable action) {
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(BusinessStateConflictException.class, action).getErrorCode());
    }
}
