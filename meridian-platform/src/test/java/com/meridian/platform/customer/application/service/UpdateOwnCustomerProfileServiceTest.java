package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateOwnCustomerProfileServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);

    private FakeCustomerRepository customerRepository;
    private FakeSensitiveValueProtector sensitiveValueProtector;
    private FakeBusinessAuditPublisher auditPublisher;
    private UpdateOwnCustomerProfileService service;

    @BeforeEach
    void setUp() {
        customerRepository = new FakeCustomerRepository(incompleteCustomer());
        sensitiveValueProtector = new FakeSensitiveValueProtector();
        auditPublisher = new FakeBusinessAuditPublisher();
        service = new UpdateOwnCustomerProfileService(
                customerRepository,
                sensitiveValueProtector,
                new FixedCurrentUserProvider(),
                new CustomerMapper(),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-07-12T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void completesProfileWithProtectedIdentityAndSafeAudit() {
        CustomerDto result = service.updateOwnProfile(completeRequest("IDREF-MER-001"));

        assertEquals("COMPLETE", result.profileCompletionStatus());
        assertEquals("Customer Demo", result.profile().fullName());
        assertEquals("cipher:IDREF-MER-001", customerRepository.savedCustomer.profile().identityReference().ciphertext());
        assertEquals("hmac:IDREF-MER-001", customerRepository.savedCustomer.profile().identityReference().fingerprint());
        assertEquals(List.of(
                        BusinessAuditAction.CUSTOMER_PROFILE_CREATED,
                        BusinessAuditAction.CUSTOMER_PROFILE_COMPLETED
                ),
                auditPublisher.lastEvent.entries().stream().map(entry -> entry.action()).toList());
        assertEquals(CUSTOMER_ID.toString(), auditPublisher.lastEvent.entries().getFirst()
                .payload().values().get(BusinessAuditPayloadKey.CUSTOMER_ID.jsonName()));
        assertEquals("COMPLETE", auditPublisher.lastEvent.entries().getFirst()
                .payload().values().get(BusinessAuditPayloadKey.PROFILE_COMPLETION_STATUS.jsonName()));
        assertFalse(auditPublisher.lastEvent.entries().getFirst().payload().values().containsValue("IDREF-MER-001"));
    }

    @Test
    void rejectsDuplicateIdentityReferenceOwnedByAnotherCustomer() {
        customerRepository.duplicateIdentityFingerprints.add("hmac:IDREF-MER-001");

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.updateOwnProfile(completeRequest("IDREF-MER-001"))
        );

        assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", exception.getErrorCode());
        assertEquals("Identity reference is already associated with another customer.", exception.getMessage());
        assertFalse(exception.getMessage().contains("IDREF-MER-001"));
        assertEquals(1, customerRepository.duplicateIdentityLookupCount);
        assertTrue(customerRepository.savedCustomer == null);
        assertTrue(auditPublisher.events.isEmpty());
    }

    @Test
    void rejectsNormalizationEquivalentDuplicateIdentityReference() {
        customerRepository.duplicateIdentityFingerprints.add("hmac:IDREF-MER-001");

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.updateOwnProfile(completeRequest(" idref-mer-001 "))
        );

        assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", exception.getErrorCode());
        assertEquals(1, customerRepository.duplicateIdentityLookupCount);
        assertTrue(customerRepository.savedCustomer == null);
        assertTrue(auditPublisher.events.isEmpty());
    }

    @Test
    void resubmittingSameCurrentIdentityReferenceSkipsDuplicateOwnerCheck() {
        customerRepository.customer = Optional.of(incompleteCustomer()
                .updateProfile(completeProfile("IDREF-MER-001"), NOW));
        customerRepository.duplicateIdentityFingerprints.add("hmac:IDREF-MER-001");

        CustomerDto result = service.updateOwnProfile(completeRequest(" idref-mer-001 "));

        assertEquals("COMPLETE", result.profileCompletionStatus());
        assertEquals(0, customerRepository.duplicateIdentityLookupCount);
        assertEquals("hmac:IDREF-MER-001", customerRepository.savedCustomer.profile().identityReference().fingerprint());
    }
    @Test
    void allowsContactUpdateWithoutRepeatingIdentityReference() {
        customerRepository.customer = Optional.of(incompleteCustomer()
                .updateProfile(completeProfile("IDREF-MER-001"), NOW));

        CustomerDto result = service.updateOwnProfile(new UpdateCustomerProfileRequest(
                "Customer Demo",
                null,
                "0909999999",
                "2 Meridian Street",
                "SALARIED",
                "Meridian Partner Co",
                true,
                true
        ));

        assertEquals("0909999999", result.profile().phoneNumber());
        assertEquals("hmac:IDREF-MER-001", customerRepository.savedCustomer.profile().identityReference().fingerprint());
        assertEquals(0, customerRepository.duplicateIdentityLookupCount);
        assertEquals(List.of(BusinessAuditAction.CUSTOMER_PROFILE_UPDATED),
                auditPublisher.lastEvent.entries().stream().map(entry -> entry.action()).toList());
    }

    @Test
    void rejectsIdentityChangeAfterProfileCompletion() {
        customerRepository.customer = Optional.of(incompleteCustomer()
                .updateProfile(completeProfile("IDREF-MER-001"), NOW));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.updateOwnProfile(completeRequest("IDREF-MER-002"))
        );

        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", exception.getErrorCode());
        assertEquals(0, customerRepository.duplicateIdentityLookupCount);
        assertTrue(auditPublisher.events.isEmpty());
    }

    @Test
    void rejectsInitialCompletionWithoutIdentityReference() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.updateOwnProfile(new UpdateCustomerProfileRequest(
                        "Customer Demo",
                        null,
                        "0901234567",
                        "1 Meridian Street",
                        "SALARIED",
                        "Meridian Partner Co",
                        true,
                        true
                ))
        );

        assertEquals("PROFILE_INCOMPLETE", exception.getErrorCode());
    }

    @Test
    void rejectsInactiveCustomer() {
        customerRepository.customer = Optional.of(new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                CustomerStatus.SUSPENDED,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.INCOMPLETE,
                null,
                List.of(),
                NOW,
                NOW
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.updateOwnProfile(completeRequest("IDREF-MER-001"))
        );

        assertEquals("CUSTOMER_NOT_ACTIVE", exception.getErrorCode());
    }

    private static Customer incompleteCustomer() {
        return new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.INCOMPLETE,
                null,
                List.of(),
                NOW,
                NOW
        );
    }

    private CustomerProfile completeProfile(String identityReference) {
        return new CustomerProfile(
                UUID.randomUUID(),
                CUSTOMER_ID,
                "Customer Demo",
                sensitiveValueProtector.protectIdentityReference(identityReference),
                "0901234567",
                "1 Meridian Street",
                "SALARIED",
                "Meridian Partner Co",
                true,
                true,
                NOW,
                NOW
        );
    }

    private static UpdateCustomerProfileRequest completeRequest(String identityReference) {
        return new UpdateCustomerProfileRequest(
                "Customer Demo",
                identityReference,
                "0901234567",
                "1 Meridian Street",
                "SALARIED",
                "Meridian Partner Co",
                true,
                true
        );
    }

    private static class FixedCurrentUserProvider implements CurrentUserProvider {

        @Override
        public AuthenticatedUser currentUser() {
            return new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000301"),
                    "customer.demo@meridian.local",
                    "CUSTOMER",
                    CUSTOMER_ID,
                    Set.of("CUSTOMER"),
                    Set.of("customer:profile:write:own")
            );
        }
    }

    private static class FakeSensitiveValueProtector implements CustomerSensitiveValueProtector {

        @Override
        public ProtectedSensitiveValue protectIdentityReference(String identityReference) {
            String normalized = identityReference.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            return new ProtectedSensitiveValue(
                    "cipher:" + normalized,
                    "hmac:" + normalized,
                    normalized.substring(normalized.length() - 4)
            );
        }

        @Override
        public ProtectedSensitiveValue protectBankAccountNumber(String bankCode, String accountNumber) {
            return new ProtectedSensitiveValue("cipher:" + accountNumber, "hmac:" + bankCode + ":" + accountNumber, "0001");
        }

        @Override
        public String reveal(ProtectedSensitiveValue protectedValue) {
            return protectedValue.ciphertext().substring("cipher:".length());
        }
    }

    private static class FakeCustomerRepository implements CustomerRepository {

        private Optional<Customer> customer;
        private Customer savedCustomer;
        private final Set<String> duplicateIdentityFingerprints = new HashSet<>();
        private int duplicateIdentityLookupCount;

        private FakeCustomerRepository(Customer customer) {
            this.customer = Optional.of(customer);
        }

        @Override
        public Customer save(Customer customer) {
            savedCustomer = customer;
            this.customer = Optional.of(customer);
            return customer;
        }

        @Override
        public Optional<Customer> findById(UUID customerId) {
            return customer.filter(value -> value.id().equals(customerId));
        }

        @Override
        public Optional<Customer> findByIdForUpdate(UUID customerId) {
            return findById(customerId);
        }

        @Override
        public boolean existsByIdentityReferenceFingerprintAndCustomerIdNot(String fingerprint, UUID customerId) {
            duplicateIdentityLookupCount++;
            return duplicateIdentityFingerprints.contains(fingerprint);
        }
    }

    private static class FakeBusinessAuditPublisher implements BusinessAuditPublisher {

        private final List<BusinessAuditEvent> events = new java.util.ArrayList<>();
        private BusinessAuditEvent lastEvent;

        @Override
        public void publish(BusinessAuditEvent event) {
            lastEvent = event;
            events.add(event);
        }
    }
}
