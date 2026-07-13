package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.application.service.ManageOwnCustomerBankAccountService;
import com.meridian.platform.customer.application.service.UpdateOwnCustomerProfileService;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.customer.domain.model.CustomerStatus;
import com.meridian.platform.customer.domain.model.ProfileCompletionStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.customer.domain.model.VerificationStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "meridian.loan.offer-expiry.enabled=false")
class CustomerIdentityAndBankAccountPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UpdateOwnCustomerProfileService updateOwnCustomerProfileService;

    @Autowired
    private ManageOwnCustomerBankAccountService manageOwnCustomerBankAccountService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerSensitiveValueProtector sensitiveValueProtector;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void duplicateIdentityReferenceIsRejectedWithoutPartialProfileOrAudit() {
        UUID firstCustomerId = insertCustomer("CUST-DUP-1");
        UUID secondCustomerId = insertCustomer("CUST-DUP-2");
        authenticateCustomer(firstCustomerId);
        updateOwnCustomerProfileService.updateOwnProfile(profileRequest("IDREF-MER-DUP"));
        int auditCountAfterFirstProfile = countRows("audit_events");

        authenticateCustomer(secondCustomerId);
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> updateOwnCustomerProfileService.updateOwnProfile(profileRequest(" idref-mer-dup "))
        );

        assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", exception.getErrorCode());
        assertEquals("Identity reference is already associated with another customer.", exception.getMessage());
        assertFalse(exception.getMessage().contains("IDREF-MER-DUP"));
        assertEquals(0, countRows("customer_profiles", "customer_id", secondCustomerId));
        assertEquals(auditCountAfterFirstProfile, countRows("audit_events"));
    }

    @Test
    void identityReferenceUniqueConstraintFallbackTranslatesRaceEquivalentDuplicate() {
        UUID firstCustomerId = insertCustomer("CUST-RACE-1");
        UUID secondCustomerId = insertCustomer("CUST-RACE-2");
        ProtectedSensitiveValue firstIdentity = sensitiveValueProtector.protectIdentityReference("IDREF-MER-RACE");
        ProtectedSensitiveValue secondIdentity = sensitiveValueProtector.protectIdentityReference(" idref-mer-race ");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> customerRepository.save(customerWithProfile(
                firstCustomerId,
                "CUST-RACE-1",
                firstIdentity
        )));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> transactionTemplate.executeWithoutResult(status -> customerRepository.save(customerWithProfile(
                        secondCustomerId,
                        "CUST-RACE-2",
                        secondIdentity
                )))
        );

        assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", exception.getErrorCode());
        assertEquals(1, countRows("customer_profiles", "identity_reference_fingerprint", firstIdentity.fingerprint()));
        assertEquals(0, countRows("customer_profiles", "customer_id", secondCustomerId));
    }

    @Test
    void validBankAccountPersistsEncryptedAndMaskedWhileShortNumbersAreRejected() {
        UUID customerId = insertCustomer("CUST-BANK-1");
        authenticateCustomer(customerId);
        int auditCountBeforeInvalidAttempt = countRows("audit_events");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> manageOwnCustomerBankAccountService.addBankAccount(bankAccountRequest("12-34"))
        );

        assertEquals("accountNumber must contain at least 6 characters after normalization", exception.getMessage());
        assertEquals(0, countRows("customer_bank_accounts", "customer_id", customerId));
        assertEquals(auditCountBeforeInvalidAttempt, countRows("audit_events"));

        CustomerBankAccountDto result = manageOwnCustomerBankAccountService.addBankAccount(bankAccountRequest("12-3456"));

        assertEquals("****3456", result.maskedAccountNumber());
        assertEquals("3456", result.accountNumberLastFour());
        assertFalse(result.toString().contains("123456"));
        String ciphertext = jdbcTemplate.queryForObject(
                "select account_number_ciphertext from " + table("customer_bank_accounts") + " where customer_id = ?",
                String.class,
                customerId
        );
        String fingerprint = jdbcTemplate.queryForObject(
                "select account_number_fingerprint from " + table("customer_bank_accounts") + " where customer_id = ?",
                String.class,
                customerId
        );
        assertFalse(ciphertext.contains("123456"));
        assertFalse(fingerprint.contains("123456"));
    }

    private UUID insertCustomer(String customerNumber) {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into %s.customers (
                            id,
                            customer_number,
                            status,
                            verification_status,
                            profile_completion_status
                        ) values (?, ?, 'ACTIVE', 'UNVERIFIED', 'INCOMPLETE')
                        """.formatted(TEST_SCHEMA),
                customerId,
                customerNumber
        );
        return customerId;
    }

    private static Customer customerWithProfile(
            UUID customerId,
            String customerNumber,
            ProtectedSensitiveValue identityReference
    ) {
        return new Customer(
                customerId,
                customerNumber,
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.COMPLETE,
                new CustomerProfile(
                        UUID.randomUUID(),
                        customerId,
                        "Customer Demo",
                        identityReference,
                        "0901234567",
                        "1 Meridian Street",
                        "SALARIED",
                        "Meridian Partner Co",
                        true,
                        true,
                        NOW,
                        NOW
                ),
                List.of(),
                NOW,
                NOW
        );
    }

    private static UpdateCustomerProfileRequest profileRequest(String identityReference) {
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

    private static AddCustomerBankAccountRequest bankAccountRequest(String accountNumber) {
        return new AddCustomerBankAccountRequest(
                "VCB",
                "Vietcombank",
                "Customer Demo",
                accountNumber
        );
    }

    private void authenticateCustomer(UUID customerId) {
        AuthenticatedUser customer = new AuthenticatedUser(
                CUSTOMER_USER_ID,
                "customer." + customerId + "@meridian.local",
                "CUSTOMER",
                customerId,
                Set.of("CUSTOMER"),
                Set.of("customer:profile:write:own", "customer:bank-account:write:own")
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(customer, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table(tableName),
                Integer.class
        );
    }

    private int countRows(String tableName, String columnName, Object value) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table(tableName) + " where " + columnName + " = ?",
                Integer.class,
                value
        );
    }

    private String table(String tableName) {
        return TEST_SCHEMA + "." + tableName;
    }
}