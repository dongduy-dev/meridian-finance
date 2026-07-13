package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.mapper.CustomerMapper;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerBankAccountStatus;
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
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManageOwnCustomerBankAccountServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);

    private FakeCustomerRepository customerRepository;
    private FakeBusinessAuditPublisher auditPublisher;
    private ManageOwnCustomerBankAccountService service;

    @BeforeEach
    void setUp() {
        customerRepository = new FakeCustomerRepository(customerWithAccounts(List.of()));
        auditPublisher = new FakeBusinessAuditPublisher();
        service = new ManageOwnCustomerBankAccountService(
                customerRepository,
                new FakeSensitiveValueProtector(),
                new FixedCurrentUserProvider(),
                new CustomerMapper(),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-07-12T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void firstAddedAccountBecomesPrimaryAndResponseIsMasked() {
        CustomerBankAccountDto result = service.addBankAccount(request("VCB", "1234567890"));

        assertTrue(result.primaryAccount());
        assertEquals("ACTIVE", result.status());
        assertEquals("****7890", result.maskedAccountNumber());
        assertEquals("7890", result.accountNumberLastFour());
        assertFalse(result.toString().contains("1234567890"));
        assertEquals("cipher:1234567890",
                customerRepository.customer.orElseThrow().bankAccounts().getFirst().accountNumber().ciphertext());
        assertEquals(BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_ADDED,
                auditPublisher.lastEvent.entries().getFirst().action());
        assertEquals(result.customerBankAccountId().toString(), auditPublisher.lastEvent.entries().getFirst()
                .payload().values().get(BusinessAuditPayloadKey.CUSTOMER_BANK_ACCOUNT_ID.jsonName()));
        assertFalse(auditPublisher.lastEvent.entries().getFirst().payload().values().containsValue("1234567890"));
        assertFalse(auditPublisher.lastEvent.entries().getFirst().payload().values().containsValue("****7890"));
    }

    @Test
    void rejectsShortNormalizedAccountNumber() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addBankAccount(request("VCB", "12-34"))
        );

        assertEquals("accountNumber must contain at least 6 characters after normalization", exception.getMessage());
        assertTrue(customerRepository.customer.orElseThrow().bankAccounts().isEmpty());
        assertTrue(auditPublisher.events.isEmpty());
    }

    @Test
    void whitespaceCannotBypassAccountNumberMinimum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addBankAccount(request("VCB", "1 2 3 4"))
        );

        assertEquals("accountNumber must contain at least 6 characters after normalization", exception.getMessage());
        assertTrue(customerRepository.customer.orElseThrow().bankAccounts().isEmpty());
        assertTrue(auditPublisher.events.isEmpty());
    }

    @Test
    void acceptsExactlySixNormalizedAccountNumberCharacters() {
        CustomerBankAccountDto result = service.addBankAccount(request("VCB", "12-3456"));

        assertTrue(result.primaryAccount());
        assertEquals("****3456", result.maskedAccountNumber());
        assertEquals("3456", result.accountNumberLastFour());
        assertFalse(result.toString().contains("123456"));
        assertEquals("cipher:123456",
                customerRepository.customer.orElseThrow().bankAccounts().getFirst().accountNumber().ciphertext());
    }
    @Test
    void duplicateActiveAccountIsRejected() {
        service.addBankAccount(request("VCB", "1234567890"));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.addBankAccount(request("vcb", "1234-567890"))
        );

        assertEquals("DUPLICATE_BANK_ACCOUNT", exception.getErrorCode());
    }

    @Test
    void makePrimaryDemotesPreviousPrimaryAndAuditsSafeIds() {
        CustomerBankAccount first = activeAccount(UUID.randomUUID(), "VCB:11110000", true);
        CustomerBankAccount second = activeAccount(UUID.randomUUID(), "VCB:22220000", false);
        customerRepository.customer = Optional.of(customerWithAccounts(List.of(first, second)));

        CustomerBankAccountDto result = service.makePrimary(second.id());

        assertTrue(result.primaryAccount());
        assertFalse(customerRepository.customer.orElseThrow().bankAccounts().stream()
                .filter(account -> account.id().equals(first.id()))
                .findFirst()
                .orElseThrow()
                .primaryAccount());
        assertEquals(BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY,
                auditPublisher.lastEvent.entries().getFirst().action());
        assertEquals(first.id().toString(), auditPublisher.lastEvent.entries().getFirst()
                .payload().values().get(BusinessAuditPayloadKey.PREVIOUS_PRIMARY_BANK_ACCOUNT_ID.jsonName()));
        assertEquals(second.id().toString(), auditPublisher.lastEvent.entries().getFirst()
                .payload().values().get(BusinessAuditPayloadKey.NEW_PRIMARY_BANK_ACCOUNT_ID.jsonName()));
    }

    @Test
    void deactivatePrimaryRequiresSwitchWhenAnotherActiveAccountExists() {
        CustomerBankAccount first = activeAccount(UUID.randomUUID(), "VCB:11110000", true);
        CustomerBankAccount second = activeAccount(UUID.randomUUID(), "VCB:22220000", false);
        customerRepository.customer = Optional.of(customerWithAccounts(List.of(first, second)));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.deactivate(first.id())
        );

        assertEquals("BANK_ACCOUNT_UPDATE_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(auditPublisher.events.isEmpty());
    }

    @Test
    void deactivationIsIdempotentAfterFirstSuccessfulChange() {
        CustomerBankAccount account = activeAccount(UUID.randomUUID(), "VCB:11110000", true);
        customerRepository.customer = Optional.of(customerWithAccounts(List.of(account)));

        CustomerBankAccountDto first = service.deactivate(account.id());
        CustomerBankAccountDto second = service.deactivate(account.id());

        assertEquals("DEACTIVATED", first.status());
        assertEquals("DEACTIVATED", second.status());
        assertEquals(1, auditPublisher.events.size());
        assertEquals(BusinessAuditAction.CUSTOMER_BANK_ACCOUNT_DEACTIVATED,
                auditPublisher.lastEvent.entries().getFirst().action());
    }

    private static AddCustomerBankAccountRequest request(String bankCode, String accountNumber) {
        return new AddCustomerBankAccountRequest(
                bankCode,
                "Vietcombank",
                "Customer Demo",
                accountNumber
        );
    }

    private static Customer customerWithAccounts(List<CustomerBankAccount> accounts) {
        return new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.COMPLETE,
                new CustomerProfile(
                        UUID.randomUUID(),
                        CUSTOMER_ID,
                        "Customer Demo",
                        new ProtectedSensitiveValue("cipher:IDREF-MER-001", "hmac:IDREF-MER-001", "-001"),
                        "0901234567",
                        "1 Meridian Street",
                        "SALARIED",
                        "Meridian Partner Co",
                        true,
                        true,
                        NOW,
                        NOW
                ),
                accounts,
                NOW,
                NOW
        );
    }

    private static CustomerBankAccount activeAccount(UUID id, String fingerprint, boolean primaryAccount) {
        return new CustomerBankAccount(
                id,
                CUSTOMER_ID,
                "VCB",
                "Vietcombank",
                "Customer Demo",
                new ProtectedSensitiveValue("cipher:" + fingerprint, "hmac:" + fingerprint, fingerprint.substring(fingerprint.length() - 4)),
                CustomerBankAccountStatus.ACTIVE,
                primaryAccount,
                NOW,
                NOW,
                null
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
                    Set.of("customer:bank-account:write:own")
            );
        }
    }

    private static class FakeSensitiveValueProtector implements CustomerSensitiveValueProtector {

        @Override
        public ProtectedSensitiveValue protectIdentityReference(String identityReference) {
            return new ProtectedSensitiveValue("cipher:" + identityReference, "hmac:" + identityReference, "-001");
        }

        @Override
        public ProtectedSensitiveValue protectBankAccountNumber(String bankCode, String accountNumber) {
            String normalizedBankCode = bankCode.trim().toUpperCase();
            String normalizedAccountNumber = accountNumber.trim().replaceAll("[\\s-]+", "");
            if (normalizedAccountNumber.length() < 6) {
                throw new IllegalArgumentException("accountNumber must contain at least 6 characters after normalization");
            }
            return new ProtectedSensitiveValue(
                    "cipher:" + normalizedAccountNumber,
                    "hmac:" + normalizedBankCode + ":" + normalizedAccountNumber,
                    normalizedAccountNumber.substring(normalizedAccountNumber.length() - 4)
            );
        }

        @Override
        public String reveal(ProtectedSensitiveValue protectedValue) {
            return protectedValue.ciphertext().substring("cipher:".length());
        }
    }

    private static class FakeCustomerRepository implements CustomerRepository {

        private Optional<Customer> customer;

        private FakeCustomerRepository(Customer customer) {
            this.customer = Optional.of(customer);
        }

        @Override
        public Customer save(Customer customer) {
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
            return false;
        }
    }

    private static class FakeBusinessAuditPublisher implements BusinessAuditPublisher {

        private final List<BusinessAuditEvent> events = new ArrayList<>();
        private BusinessAuditEvent lastEvent;

        @Override
        public void publish(BusinessAuditEvent event) {
            lastEvent = event;
            events.add(event);
        }
    }
}
