package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CorrectIdentityReferenceRequest;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.out.*;
import com.meridian.platform.customer.domain.model.*;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.exception.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrectCustomerIdentityReferenceServiceTest {
    private final UUID id = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 6, 0, 0);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerSensitiveValueProtector protector = mock(CustomerSensitiveValueProtector.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final CorrectCustomerIdentityReferenceService service = new CorrectCustomerIdentityReferenceService(
            customers, protector, users, new CustomerMapper(), audits, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    private Customer customer;
    private final ProtectedSensitiveValue replacement = new ProtectedSensitiveValue("protected-new", "safe-new-fingerprint", "1234");
    @BeforeEach void setup() {
        var profile = new CustomerProfile(UUID.randomUUID(), id, "Fictional Customer", new ProtectedSensitiveValue("protected-old", "safe-old-fingerprint", "4321"),
                "0900000000", "Fictional address", "EMPLOYED", null, true, true, now, now);
        customer = new Customer(id, "CUS-000000001", CustomerStatus.ACTIVE, VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.COMPLETE, profile, List.of(), now, now);
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer));
        when(customers.save(any())).thenAnswer(call -> call.getArgument(0));
        when(protector.protectIdentityReference("FICTIONAL-1234")).thenReturn(replacement);
        actor("CUSTOMER", id, Set.of("customer:profile:write:own"));
    }
    private void actor(String type, UUID owner, Set<String> permissions) {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "fictional@example.test", type, owner, Set.of(), permissions));
    }
    private CorrectIdentityReferenceRequest request() { return new CorrectIdentityReferenceRequest("FICTIONAL-1234"); }
    @Test void correctionChangesOnlyProtectedReferenceAndSummaryAndAuditsWithoutSensitiveValues() {
        var response = service.correctOwn(request());
        assertEquals("UNVERIFIED", response.verificationStatus());
        assertEquals("COMPLETE", response.profileCompletionStatus());
        var captured = org.mockito.ArgumentCaptor.forClass(Customer.class); verify(customers).save(captured.capture());
        assertEquals(replacement, captured.getValue().profile().identityReference());
        assertEquals(customer.profile().fullName(), captured.getValue().profile().fullName());
        assertEquals(customer.profile().createdAt(), captured.getValue().profile().createdAt());
        var event = org.mockito.ArgumentCaptor.forClass(BusinessAuditEvent.class); verify(audits).publish(event.capture());
        assertFalse(event.getValue().toString().contains("FICTIONAL-1234"));
        assertFalse(event.getValue().toString().contains(replacement.fingerprint()));
        assertFalse(event.getValue().toString().contains(replacement.ciphertext()));
        assertFalse(response.toString().contains("FICTIONAL-1234"));
        assertFalse(request().toString().contains("FICTIONAL-1234"));
    }
    @Test void rejectedSummaryResetsAndStaffUsesTheSameControlledCommand() {
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer.withVerificationStatus(VerificationStatus.REJECTED, now)));
        actor("STAFF", null, Set.of("customer:intake:manage"));
        assertEquals("UNVERIFIED", service.correctForIntake(id, request()).verificationStatus());
    }
    @Test void verifiedInactiveIncompleteAndDuplicateRequestsDoNotSaveOrAudit() {
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer.withVerificationStatus(VerificationStatus.VERIFIED, now)));
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class, () -> service.correctOwn(request())).getErrorCode());
        var inactive = new Customer(id, customer.customerNumber(), CustomerStatus.SUSPENDED, VerificationStatus.UNVERIFIED, ProfileCompletionStatus.COMPLETE, customer.profile(), List.of(), now, now);
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(inactive));
        assertEquals("CUSTOMER_NOT_ACTIVE", assertThrows(BusinessStateConflictException.class, () -> service.correctOwn(request())).getErrorCode());
        var incomplete = new Customer(id, customer.customerNumber(), CustomerStatus.ACTIVE, VerificationStatus.UNVERIFIED, ProfileCompletionStatus.INCOMPLETE, null, List.of(), now, now);
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(incomplete));
        assertEquals("PROFILE_INCOMPLETE", assertThrows(BusinessRuleViolationException.class, () -> service.correctOwn(request())).getErrorCode());
        when(customers.findByIdForUpdate(id)).thenReturn(Optional.of(customer));
        when(customers.existsByIdentityReferenceFingerprintAndCustomerIdNot(replacement.fingerprint(), id)).thenReturn(true);
        assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", assertThrows(BusinessStateConflictException.class, () -> service.correctOwn(request())).getErrorCode());
        verify(customers, never()).save(any()); verifyNoInteractions(audits);
    }
    @Test void ordinaryProfileUpdateStillCannotChangeTheCompletedReference() {
        var p = customer.profile();
        var changed = new CustomerProfile(p.id(), id, p.fullName(), replacement, p.phoneNumber(), p.residentialAddress(), p.employmentStatus(), p.employerName(), true, true, now, now);
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class, () -> customer.updateProfile(changed, now)).getErrorCode());
    }
    @Test void incompleteProfileCannotBypassVerifiedIdentityLockout() {
        var p = customer.profile();
        var incomplete = new CustomerProfile(p.id(), id, p.fullName(), p.identityReference(), p.phoneNumber(),
                p.residentialAddress(), p.employmentStatus(), p.employerName(), false, true, now, now);
        var verified = new Customer(id, customer.customerNumber(), CustomerStatus.ACTIVE, VerificationStatus.VERIFIED,
                ProfileCompletionStatus.INCOMPLETE, incomplete, List.of(), now, now);
        var completed = new CustomerProfile(p.id(), id, p.fullName(), replacement, p.phoneNumber(),
                p.residentialAddress(), p.employmentStatus(), p.employerName(), true, true, now, now);
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class,
                () -> verified.updateProfile(completed, now)).getErrorCode());
    }
    @Test void wrongActorTypesAndGenericPermissionsFailBeforeCustomerAccess() {
        actor("STAFF", null, Set.of("customer:profile:write:own")); assertThrows(AuthorizationException.class, () -> service.correctOwn(request()));
        actor("CUSTOMER", id, Set.of("customer:intake:manage")); assertThrows(AuthorizationException.class, () -> service.correctForIntake(UUID.randomUUID(), request()));
        actor("STAFF", id, Set.of("customer:intake:manage")); assertThrows(AuthorizationException.class, () -> service.correctForIntake(id, request()));
        actor("STAFF", null, Set.of("customer:read", "customer:identity:verify")); assertThrows(AuthorizationException.class, () -> service.correctForIntake(id, request()));
        verifyNoInteractions(customers, audits);
    }
    @Test void identicalCorrectionDoesNotDuplicateAuditAndInvalidInputDoesNotReadCustomer() {
        when(protector.protectIdentityReference("FICTIONAL-1234")).thenReturn(customer.profile().identityReference());
        service.correctOwn(request()); verify(customers, never()).save(any()); verifyNoInteractions(audits);
        clearInvocations(customers);
        for (String invalid : List.of("", "   ", "X".repeat(101))) assertThrows(BusinessRuleViolationException.class, () -> service.correctOwn(new CorrectIdentityReferenceRequest(invalid)));
        verifyNoInteractions(customers);
    }
}
