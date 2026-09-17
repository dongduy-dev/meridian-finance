package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CreateStaffAssistedCustomerRequest;
import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.StaffCustomerSearchRequest;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.out.CustomerNumberSequenceRepository;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerBankAccountStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffCustomerIntakeServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerNumberSequenceRepository numbers = mock(CustomerNumberSequenceRepository.class);
    private final CustomerSensitiveValueProtector protector = mock(CustomerSensitiveValueProtector.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID staffUserId = UUID.randomUUID();
    private StaffCustomerIntakeService service;

    @BeforeEach
    void setUp() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                staffUserId, "loan.officer@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("customer:read", "customer:intake:manage")));
        when(numbers.nextCustomerNumberSequence()).thenReturn(42L);
        when(protector.protectIdentityReference("012345678901")).thenReturn(
                new ProtectedSensitiveValue("ciphertext", "fingerprint", "8901"));
        when(customers.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new StaffCustomerIntakeService(
                customers, numbers, protector, users, new CustomerMapper(), audits,
                Clock.fixed(Instant.parse("2026-09-17T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsCustomerAndIdentityProfileAtomicallyAsUnverifiedWithPiiSafeAudit() {
        var result = service.createCustomer(request());

        assertEquals("CUS-000000042", result.customerNumber());
        assertEquals("UNVERIFIED", result.verificationStatus());
        assertEquals("COMPLETE", result.profileCompletionStatus());
        assertFalse(result.toString().contains("012345678901"));
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertEquals("fingerprint", saved.getValue().profile().identityReference().fingerprint());
        ArgumentCaptor<BusinessAuditEvent> audit = ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(audits).publish(audit.capture());
        assertEquals(BusinessAuditAction.STAFF_ASSISTED_CUSTOMER_CREATED,
                audit.getValue().entries().getFirst().action());
        assertEquals(staffUserId, audit.getValue().operationContext().actorUserId());
        assertFalse(audit.getValue().entries().getFirst().payload().values().containsValue("012345678901"));
    }

    @Test
    void duplicateIdentityStopsBeforeCustomerSave() {
        when(customers.existsByIdentityReferenceFingerprint("fingerprint")).thenReturn(true);
        assertThrows(BusinessStateConflictException.class, () -> service.createCustomer(request()));
        verify(customers, never()).save(any());
    }

    @Test
    void exactIdentitySearchUsesFingerprintAndNeverReturnsRawIdentity() {
        service = org.mockito.Mockito.spy(service);
        Customer created = captureCreatedCustomer();
        when(customers.findByIdentityReferenceFingerprint("fingerprint")).thenReturn(Optional.of(created));

        var result = service.search(new StaffCustomerSearchRequest(null, "012345678901"));

        verify(customers).findByIdentityReferenceFingerprint("fingerprint");
        assertFalse(result.toString().contains("012345678901"));
        assertFalse(result.toString().contains("fingerprint"));
    }

    @Test
    void exactCustomerNumberSearchUsesThePurposeLimitedRepositoryLookup() {
        Customer created = captureCreatedCustomer();
        when(customers.findByCustomerNumber("CUS-000000042")).thenReturn(Optional.of(created));

        var result = service.search(new StaffCustomerSearchRequest(" CUS-000000042 ", null));

        verify(customers).findByCustomerNumber("CUS-000000042");
        assertEquals(created.id(), result.customerId());
    }

    @Test
    void staffWithoutTheExactMutationPermissionIsRejected() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "reader@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("customer:read")));

        assertThrows(AuthorizationException.class, () -> service.createCustomer(request()));
        verify(customers, never()).save(any());
    }

    @Test
    void completedProfileIdentityRemainsImmutableThroughStaffCommand() {
        Customer created = captureCreatedCustomer();
        when(customers.findByIdForUpdate(created.id())).thenReturn(Optional.of(created));
        when(protector.protectIdentityReference("999999999999")).thenReturn(
                new ProtectedSensitiveValue("new-ciphertext", "new-fingerprint", "9999"));
        var update = new UpdateCustomerProfileRequest(
                "Paper Customer", "999999999999", "0900000000", "1 Meridian Street",
                "EMPLOYED", "Meridian Partner", true, true);

        assertThrows(BusinessStateConflictException.class, () -> service.updateProfile(created.id(), update));
    }

    @Test
    void staffBankCommandsReuseDuplicatePrimaryAndDeactivationInvariants() {
        Customer created = captureCreatedCustomer();
        ProtectedSensitiveValue account = new ProtectedSensitiveValue("bank-cipher", "bank-fingerprint", "7890");
        when(protector.protectBankAccountNumber("MER", "1234567890")).thenReturn(account);
        when(customers.findByIdForUpdate(created.id())).thenReturn(Optional.of(created));

        var added = service.addBankAccount(created.id(),
                new AddCustomerBankAccountRequest("MER", "Meridian Bank", "Paper Customer", "1234567890"));
        assertEquals(true, added.primaryAccount());

        Customer withPrimary = new Customer(
                created.id(), created.customerNumber(), created.status(), created.verificationStatus(),
                created.profileCompletionStatus(), created.profile(), List.of(new CustomerBankAccount(
                        added.customerBankAccountId(), created.id(), "MER", "Meridian Bank", "Paper Customer",
                        account, CustomerBankAccountStatus.ACTIVE, true,
                        LocalDateTime.now(), LocalDateTime.now(), null)),
                created.createdAt(), created.updatedAt());
        when(customers.findByIdForUpdate(created.id())).thenReturn(Optional.of(withPrimary));
        assertThrows(BusinessStateConflictException.class, () -> service.addBankAccount(created.id(),
                new AddCustomerBankAccountRequest("MER", "Meridian Bank", "Paper Customer", "1234567890")));
    }

    @Test
    void customerActorCannotUseStaffCommands() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.local", "CUSTOMER", UUID.randomUUID(),
                Set.of(), Set.of("customer:intake:manage")));
        assertThrows(AuthorizationException.class, () -> service.createCustomer(request()));
    }

    private Customer captureCreatedCustomer() {
        service.createCustomer(request());
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        return saved.getValue();
    }

    private static CreateStaffAssistedCustomerRequest request() {
        return new CreateStaffAssistedCustomerRequest(
                "Paper Customer", "012345678901", "0900000000", "1 Meridian Street",
                "EMPLOYED", "Meridian Partner", true, true);
    }
}
