package com.meridian.platform.loan.application.service;

import com.meridian.platform.customer.application.port.out.CustomerRepository;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanAccountCustomerContextUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false"})
@Import(RecordRepaymentPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class StaffLoanAccountCustomerContextPostgreSqlIntegrationTest {
    private static final String SCHEMA = "servicing_customer_context_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired QueryStaffLoanAccountCustomerContextUseCase context;
    @Autowired QueryLoanAccountUseCase accounts;
    @Autowired CustomerRepository customers;
    @Autowired CustomerSensitiveValueProtector protector;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider users;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test void exactPersistedCustomerProvidesCurrentPurposeLimitedContextWithoutChangingSharedAccount() {
        actor(Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse", "loan:read", "repayment:update"));
        var support = new ManualDisbursementActivationPostgreSqlTestSupport(jdbc, transactionManager);
        var fixture = support.createFixture(true, ProductCode.UNSECURED_CONSUMER_LOAN);
        var activation = disbursements.confirm(support.command(fixture, UUID.randomUUID(), "CONTEXT-" + fixture.token()));
        var before = accounts.query(fixture.applicationId());
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(BusinessStateConflictException.class,
                () -> context.query(fixture.applicationId())).getErrorCode());
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            var customer = customers.findById(fixture.customerId()).orElseThrow();
            var profile = new CustomerProfile(UUID.randomUUID(), customer.id(), "Ari Customer",
                    protector.protectIdentityReference("SYNTHETIC-CONTEXT-" + fixture.token()),
                    "0901234567", "Fictional address", "EMPLOYED", null, true, true, null, null);
            customers.save(customer.updateProfile(profile, LocalDateTime.of(2026, 9, 10, 10, 0)));
        });
        var contact = context.query(fixture.applicationId());
        assertEquals(activation.loanAccountId(), contact.loanAccountId());
        assertEquals("CUS-I3-" + fixture.token(), contact.customer().customerNumber());
        assertEquals("Ari Customer", contact.customer().fullName());
        assertEquals("0901234567", contact.customer().phoneNumber());
        actor(Set.of("APPROVER"), Set.of("loan:read", "loan:settlement:approve"));
        assertNull(context.query(fixture.applicationId()).customer().phoneNumber());
        actor(Set.of(), Set.of("loan:read"));
        assertNull(context.query(fixture.applicationId()).customer());
        actor(Set.of("LOAN_OFFICER"), Set.of("loan:read", "customer:read"));
        assertEquals("0901234567", context.query(fixture.applicationId()).customer().phoneNumber());
        transaction.executeWithoutResult(status -> jdbc.update("update customer_profiles set phone_number = ? where customer_id = ?",
                "0907654321", fixture.customerId()));
        assertEquals("0907654321", context.query(fixture.applicationId()).customer().phoneNumber());
        assertEquals(before, accounts.query(fixture.applicationId()));
    }

    private void actor(Set<String> roles, Set<String> permissions) {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(ACCOUNTING_USER_ID, "staff@meridian.test", "STAFF", null, roles, permissions));
    }
}
