package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryCustomerLoanCaseContactServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");

    @Test
    void returnsOnlyCurrentContactFieldsAndAllowsMissingProfile() {
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = mock(Customer.class);
        CustomerProfile profile = mock(CustomerProfile.class);
        when(repository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(customer.customerNumber()).thenReturn("CUST-001");
        when(customer.profile()).thenReturn(profile);
        when(profile.fullName()).thenReturn("Nguyen Van A");
        when(profile.phoneNumber()).thenReturn("0901234567");
        var service = new QueryCustomerLoanCaseContactService(repository);

        var contact = service.findByCustomerId(CUSTOMER_ID).orElseThrow();
        assertEquals("CUST-001", contact.customerNumber());
        assertEquals("Nguyen Van A", contact.fullName());
        assertEquals("0901234567", contact.phoneNumber());
        assertEquals(3, contact.getClass().getRecordComponents().length);

        when(customer.profile()).thenReturn(null);
        var withoutProfile = service.findByCustomerId(CUSTOMER_ID).orElseThrow();
        assertEquals("CUST-001", withoutProfile.customerNumber());
        assertNull(withoutProfile.fullName());
        assertNull(withoutProfile.phoneNumber());
    }

    @Test
    void missingCustomerHasNoContactContext() {
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());
        assertTrue(new QueryCustomerLoanCaseContactService(repository).findByCustomerId(CUSTOMER_ID).isEmpty());
    }
}
