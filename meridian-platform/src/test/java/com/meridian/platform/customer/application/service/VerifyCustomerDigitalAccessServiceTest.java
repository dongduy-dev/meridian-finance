package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerifyCustomerDigitalAccessServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerSensitiveValueProtector protector = mock(CustomerSensitiveValueProtector.class);
    private final VerifyCustomerDigitalAccessService service = new VerifyCustomerDigitalAccessService(customers, protector);

    @Test
    void acceptsExactProtectedIdentityEvenWhenBusinessProfileIsIncomplete() {
        UUID id = UUID.randomUUID();
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer(id, CustomerStatus.ACTIVE, true)));
        when(protector.protectIdentityReference("presented")).thenReturn(new ProtectedSensitiveValue("other-ciphertext", "fingerprint", "1234"));
        var candidate = service.verifyForActivation(id, "presented");
        assertEquals(id, candidate.customerId());
        assertEquals("Existing Customer", candidate.displayName());
        verify(customers).findByIdForUpdate(id);
        verify(protector, never()).reveal(any());
    }

    @Test
    void missingInactiveProfilelessAndWrongReferenceFailWithoutSensitiveDetails() {
        UUID id = UUID.randomUUID();
        assertEquals("CUSTOMER_NOT_FOUND", assertThrows(EntityNotFoundException.class,
                () -> service.verifyForActivation(id, "presented")).getErrorCode());
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer(id, CustomerStatus.DISABLED, true)));
        assertEquals("CUSTOMER_NOT_ACTIVE", assertThrows(BusinessStateConflictException.class,
                () -> service.verifyForActivation(id, "presented")).getErrorCode());
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer(id, CustomerStatus.ACTIVE, false)));
        assertEquals("CUSTOMER_DIGITAL_ACCESS_OWNERSHIP_NOT_VERIFIED", assertThrows(
                BusinessRuleViolationException.class,
                () -> service.verifyForActivation(id, "presented")).getErrorCode());
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer(id, CustomerStatus.ACTIVE, true)));
        when(protector.protectIdentityReference("presented")).thenReturn(new ProtectedSensitiveValue("cipher", "wrong", "1234"));
        var mismatch = assertThrows(BusinessRuleViolationException.class,
                () -> service.verifyForActivation(id, "presented"));
        assertEquals("CUSTOMER_DIGITAL_ACCESS_OWNERSHIP_NOT_VERIFIED", mismatch.getErrorCode());
        assertFalse(mismatch.getMessage().contains("presented"));
    }

    private static Customer customer(UUID id, CustomerStatus status, boolean withProfile) {
        var identity = new ProtectedSensitiveValue("stored-ciphertext", "fingerprint", "1234");
        var profile = withProfile ? new CustomerProfile(UUID.randomUUID(), id, "Existing Customer", identity,
                "0900000000", "Meridian Street", "EMPLOYED", null, false, false, null, null) : null;
        return new Customer(id, "CUS-000000001", status, VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.INCOMPLETE, profile, List.of(), null, null);
    }
}
