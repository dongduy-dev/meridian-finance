package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.QueryServicingCustomerContextUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryServicingCustomerContextServiceTest {
    private final UUID id = UUID.randomUUID();
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final Customer customer = mock(Customer.class);
    private final CustomerProfile profile = mock(CustomerProfile.class);
    private final QueryServicingCustomerContextService service = new QueryServicingCustomerContextService(customers);

    @BeforeEach void setUp() {
        when(customers.findById(id)).thenReturn(Optional.of(customer));
        when(customer.id()).thenReturn(id);
        when(customer.customerNumber()).thenReturn("CUS-001");
        when(customer.profile()).thenReturn(profile);
        when(profile.customerId()).thenReturn(id);
        when(profile.fullName()).thenReturn("Ari Customer");
        when(profile.phoneNumber()).thenReturn("0901234567");
    }

    @Test void businessIdentityNeverReadsPhoneOrProtectedIdentity() {
        var value = service.findBusinessIdentityByCustomerId(id).orElseThrow();
        assertEquals(new QueryServicingCustomerContextUseCase.BusinessIdentity("CUS-001", "Ari Customer"), value);
        assertEquals(2, value.getClass().getRecordComponents().length);
        assertFalse(value.toString().contains("Ari"));
        verify(profile, never()).phoneNumber();
        verify(profile, never()).identityReference();
        verify(customer, never()).bankAccounts();
    }

    @Test void currentContactReadsOnlyTheThreeCurrentFacts() {
        var value = service.findCurrentContactByCustomerId(id).orElseThrow();
        assertEquals(new QueryServicingCustomerContextUseCase.CurrentContact("CUS-001", "Ari Customer", "0901234567"), value);
        assertEquals(3, value.getClass().getRecordComponents().length);
        assertFalse(value.toString().contains("0901234567"));
        verify(profile, never()).identityReference();
        verify(customer, never()).bankAccounts();
    }

    @Test void missingCustomerReturnsEmptyForEitherPurpose() {
        when(customers.findById(id)).thenReturn(Optional.empty());
        assertTrue(service.findBusinessIdentityByCustomerId(id).isEmpty());
        assertTrue(service.findCurrentContactByCustomerId(id).isEmpty());
    }

    @Test void missingOrForeignProfileAndWrongCustomerFailClosed() {
        when(customer.id()).thenReturn(UUID.randomUUID());
        conflictBoth();
        when(customer.id()).thenReturn(id);
        when(profile.customerId()).thenReturn(UUID.randomUUID());
        conflictBoth();
        when(profile.customerId()).thenReturn(id);
        when(customer.profile()).thenReturn(null);
        conflictBoth();
    }

    @Test void missingRequiredFactsAndUnmappableSourceFailClosed() {
        when(customer.customerNumber()).thenReturn(" ");
        conflictBoth();
        when(customer.customerNumber()).thenReturn("CUS-001");
        when(profile.fullName()).thenReturn(null);
        conflictBoth();
        when(profile.fullName()).thenReturn("Ari Customer");
        when(profile.phoneNumber()).thenReturn(" ");
        assertTrue(service.findBusinessIdentityByCustomerId(id).isPresent());
        assertConflict(() -> service.findCurrentContactByCustomerId(id));
        when(customers.findById(id)).thenThrow(new IllegalArgumentException("internal source detail"));
        conflictBoth();
    }

    private void conflictBoth() {
        assertConflict(() -> service.findBusinessIdentityByCustomerId(id));
        assertConflict(() -> service.findCurrentContactByCustomerId(id));
    }
    private static void assertConflict(org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(BusinessStateConflictException.class, action);
        assertEquals("SYSTEM_STATE_CONFLICT", error.getErrorCode());
        assertFalse(error.getMessage().contains("internal source detail"));
    }
}
