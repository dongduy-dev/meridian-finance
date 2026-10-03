package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.port.in.RevealCustomerIdentityReferenceUseCase;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.*;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.audit.*;
import com.meridian.platform.shared.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RevealCustomerIdentityReferenceServiceTest {
    private final UUID customerId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerSensitiveValueProtector protector = mock(CustomerSensitiveValueProtector.class);
    private final BusinessAuditPublisher audit = mock(BusinessAuditPublisher.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final ProtectedSensitiveValue protectedValue = new ProtectedSensitiveValue("ciphertext", "fingerprint", "8901");
    private final RevealCustomerIdentityReferenceService service = new RevealCustomerIdentityReferenceService(
            customers, protector, audit, users, Clock.systemUTC());

    private void setup() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "fictional@meridian.test",
                "STAFF", null, Set.of("LOAN_OFFICER"), Set.of("loan:read", "customer:read", "customer:identity:reveal")));
        var customer = mock(Customer.class);
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));
        when(customer.id()).thenReturn(customerId);
        when(customer.hasCompleteProfile()).thenReturn(true);
        var profile = mock(CustomerProfile.class);
        when(customer.profile()).thenReturn(profile);
        when(profile.customerId()).thenReturn(customerId);
        when(profile.identityReference()).thenReturn(protectedValue);
    }
    private RevealCustomerIdentityReferenceUseCase.Result reveal() {
        return service.reveal(new RevealCustomerIdentityReferenceUseCase.Command(customerId, applicationId));
    }

    @Test void successfulExplicitRevealClearsBytesAndAuditsOnlySafePurposeIdentifiers() {
        setup();
        byte[] bytes = "FICTIONAL-ID-8901".getBytes(StandardCharsets.UTF_8);
        when(protector.revealToBytes(protectedValue)).thenReturn(bytes);
        var result = reveal();
        assertEquals("FICTIONAL-ID-8901", result.identityReference());
        assertFalse(result.toString().contains(result.identityReference()));
        assertArrayEquals(new byte[bytes.length], bytes);
        verify(protector).revealToBytes(protectedValue);
        verify(protector, never()).reveal(any());
        var event = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audit).publish(event.capture());
        assertEquals(1, event.getValue().entries().size());
        var entry = event.getValue().entries().getFirst();
        assertEquals(BusinessAuditAction.CUSTOMER_IDENTITY_REFERENCE_REVEALED, entry.action());
        assertEquals(BusinessAuditEntityType.CUSTOMER, entry.entityType());
        assertEquals(customerId, entry.entityId());
        assertEquals(Map.of("customerId", customerId.toString(), "loanApplicationId", applicationId.toString()), entry.payload().values());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "DAMAGED-9999", "CONTROL\n8901", "CONTROL\u007f8901"})
    void invalidContentIsUnavailableClearedAndNeverAudited(String content) {
        setup();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        when(protector.revealToBytes(protectedValue)).thenReturn(bytes);
        assertEquals("CUSTOMER_IDENTITY_REFERENCE_UNAVAILABLE", assertThrows(BusinessStateConflictException.class, this::reveal).getErrorCode());
        assertArrayEquals(new byte[bytes.length], bytes);
        verifyNoInteractions(audit);
    }

    @Test void oversizedMalformedUtf8AndCryptoFailureAreSafeUnavailableErrors() {
        setup();
        for (byte[] bytes : List.of(("A".repeat(100) + "8901").getBytes(StandardCharsets.UTF_8), new byte[]{(byte)0xc3, 0x28})) {
            when(protector.revealToBytes(protectedValue)).thenReturn(bytes);
            assertEquals("CUSTOMER_IDENTITY_REFERENCE_UNAVAILABLE", assertThrows(BusinessStateConflictException.class, this::reveal).getErrorCode());
            assertArrayEquals(new byte[bytes.length], bytes);
        }
        when(protector.revealToBytes(protectedValue)).thenThrow(new IllegalStateException("crypto internals"));
        var failure = assertThrows(BusinessStateConflictException.class, this::reveal);
        assertFalse(failure.getMessage().contains("crypto"));
        verifyNoInteractions(audit);
    }

    @Test void missingOrIncompleteProfileDoesNotDecrypt() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "fictional@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:read", "customer:read", "customer:identity:reveal")));
        when(customers.findById(customerId)).thenReturn(Optional.empty());
        assertThrows(BusinessStateConflictException.class, this::reveal);
        when(customers.findById(customerId)).thenReturn(Optional.of(mock(Customer.class)));
        assertThrows(BusinessStateConflictException.class, this::reveal);
        verifyNoInteractions(protector, audit);
    }

    @Test void auditFailurePreventsReturningPlaintext() {
        setup();
        byte[] bytes = "FICTIONAL-ID-8901".getBytes(StandardCharsets.UTF_8);
        when(protector.revealToBytes(protectedValue)).thenReturn(bytes);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).publish(any());
        assertThrows(IllegalStateException.class, this::reveal);
        assertArrayEquals(new byte[bytes.length], bytes);
    }

    @Test void customerBoundaryRequiresDedicatedLoanOfficerAuthorityBeforeLoadingTheSubject() {
        for (String role : List.of("LOAN_OFFICER", "APPROVER", "ACCOUNTING_OFFICER", "BACK_OFFICE_ADMIN")) {
            when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "fictional@meridian.test", "STAFF", null,
                    Set.of(role), role.equals("LOAN_OFFICER")
                    ? Set.of("loan:read", "customer:read", "customer:identity:verify")
                    : Set.of("loan:read", "customer:read", "customer:identity:reveal")));
            assertEquals("CUSTOMER_IDENTITY_REFERENCE_ACCESS_DENIED", assertThrows(AuthorizationException.class, this::reveal).getErrorCode());
        }
        verifyNoInteractions(customers, protector, audit);
    }

    @Test void ordinaryContactReadNeverInvokesProtector() {
        var customer = mock(Customer.class);
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));
        var profile = mock(CustomerProfile.class);
        when(customer.profile()).thenReturn(profile);
        when(profile.identityReference()).thenReturn(protectedValue);
        var contact = new QueryCustomerLoanCaseContactService(customers).findByCustomerId(customerId).orElseThrow();
        assertEquals("****8901", contact.maskedIdentityReference());
        verifyNoInteractions(protector, audit);
    }
}
