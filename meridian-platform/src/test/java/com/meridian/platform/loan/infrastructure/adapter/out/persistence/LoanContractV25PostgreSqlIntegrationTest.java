package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.loan.application.port.in.*;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {MeridianPlatformApplication.class,
        LoanContractV25PostgreSqlIntegrationTest.TestConfig.class}, properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class LoanContractV25PostgreSqlIntegrationTest {
    private static final String SCHEMA = "meridian_contract_v25_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID ACCOUNTING = UUID.fromString("00000000-0000-0000-0000-000000000304");
    @Autowired JdbcTemplate jdbc;
    @Autowired PrepareLoanContractUseCase prepare;
    @Autowired AcknowledgeLoanContractUseCase acknowledge;
    @Autowired ConfirmContractReadinessUseCase confirm;
    @Autowired CustomerSensitiveValueProtector customerProtector;
    @Autowired TestCurrentUserProvider currentUser;
    @Autowired ToggleAuditPublisher audit;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach void reset() { audit.failReadiness = false; currentUser.clear(); }

    @Test void migrationAndInternalWorkflowReachDisbursementPendingWithSafeExactOnceEvidence() {
        assertEquals(1, jdbc.queryForObject("select count(*) from " + SCHEMA
                + ".flyway_schema_history where version = '25' and success", Integer.class));
        Fixture f = fixture();
        currentUser.set(staff());
        UUID preparationRequest = UUID.randomUUID();
        LoanContract prepared = prepare.prepare(new PrepareLoanContractUseCase.Command(
                preparationRequest, f.applicationId, 0, null));
        assertEquals(prepared.id(), prepare.prepare(new PrepareLoanContractUseCase.Command(
                preparationRequest, f.applicationId, 0, null)).id());
        currentUser.set(customer(f));
        UUID acknowledgmentRequest = UUID.randomUUID();
        LoanContract acknowledged = acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                acknowledgmentRequest, f.applicationId, prepared.id(), 1));
        assertEquals(acknowledged.id(), acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                acknowledgmentRequest, f.applicationId, prepared.id(), 1)).id());
        currentUser.set(staff());
        UUID confirmationRequest = UUID.randomUUID();
        LoanContract ready = confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                confirmationRequest, f.applicationId, prepared.id(), 1));
        assertEquals(ready.id(), confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                confirmationRequest, f.applicationId, prepared.id(), 1)).id());

        assertEquals("DISBURSEMENT_PENDING", scalar("select status from loan_applications where id = ?", f.applicationId));
        assertEquals("READY_FOR_DISBURSEMENT", scalar("select status from loan_contracts where id = ?", ready.id()));
        assertEquals(1, count("select count(*) from loan_contracts where loan_application_id = ?", f.applicationId));
        assertEquals(1, count("select count(*) from loan_application_status_transitions where loan_application_id = ? and action = 'CONFIRM_DISBURSEMENT_READINESS'", f.applicationId));
        assertEquals(1, count("select count(*) from audit_events where entity_id = ? and action = 'LOAN_CONTRACT_READINESS_CONFIRMED'", ready.id()));
        assertEquals("INCOMPLETE", scalar("select profile_completion_status from customers where id = ?", f.customerId));
        String audits = jdbc.queryForObject("select coalesce(string_agg(payload::text, ''), '') from audit_events where entity_id = ?", String.class, ready.id());
        assertFalse(audits.contains("7890")); assertFalse(audits.contains("MERIDIAN CUSTOMER"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update loan_contracts set approved_principal = 999 where id = ?", ready.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update loan_contracts set protected_account_number = decode('00','hex') where id = ?", ready.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update loan_contract_repayment_items set total_due = total_due + 1 where loan_contract_id = ?", ready.id()));
        assertThrows(BusinessStateConflictException.class, () -> confirm.confirm(
                new ConfirmContractReadinessUseCase.Command(UUID.randomUUID(), f.applicationId, ready.id(), 1)));
    }

    @Test void simultaneousFirstPreparationCreatesExactlyOneCurrentContract() throws Exception {
        Fixture f = fixture();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Object> action = () -> {
                currentUser.set(staff()); start.await(10, TimeUnit.SECONDS);
                try {
                    return prepare.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.applicationId, 0, null));
                } catch (BusinessStateConflictException exception) {
                    return exception.getErrorCode();
                } finally { currentUser.clear(); }
            };
            Future<Object> one = pool.submit(action); Future<Object> two = pool.submit(action); start.countDown();
            List<Object> outcomes = List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(LoanContract.class::isInstance).count());
            assertEquals(1, outcomes.stream().filter("CONTRACT_VERSION_STALE"::equals).count());
        }
        assertEquals(1, count("select count(*) from loan_contracts where loan_application_id = ? and status <> 'SUPERSEDED'", f.applicationId));
    }

    @Test void auditFailureRollsBackContractApplicationAndHistoryTogether() {
        Fixture f = fixture(); currentUser.set(staff());
        LoanContract prepared = prepare.prepare(new PrepareLoanContractUseCase.Command(UUID.randomUUID(), f.applicationId, 0, null));
        currentUser.set(customer(f));
        acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(UUID.randomUUID(), f.applicationId, prepared.id(), 1));
        currentUser.set(staff()); audit.failReadiness = true;
        assertThrows(IllegalStateException.class, () -> confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                UUID.randomUUID(), f.applicationId, prepared.id(), 1)));
        assertEquals("CONTRACT_PENDING", scalar("select status from loan_applications where id = ?", f.applicationId));
        assertEquals("ACKNOWLEDGED", scalar("select status from loan_contracts where id = ?", prepared.id()));
        assertEquals(0, count("select count(*) from loan_application_status_transitions where loan_application_id = ? and action = 'CONFIRM_DISBURSEMENT_READINESS'", f.applicationId));
    }

    @Test void controlledRegenerationSupersedesExactlyOneVersionAndRequiresAcknowledgmentAgain() {
        Fixture f = fixture(); currentUser.set(staff());
        LoanContract first = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), f.applicationId, 0, null));
        LoanContract second = prepare.prepare(new PrepareLoanContractUseCase.Command(
                UUID.randomUUID(), f.applicationId, 1,
                com.meridian.platform.loan.domain.model.ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH));
        assertEquals(2, second.contractVersion());
        assertEquals("SUPERSEDED", scalar("select status from loan_contracts where id = ?", first.id()));
        assertEquals("PREPARED", scalar("select status from loan_contracts where id = ?", second.id()));
        currentUser.set(customer(f));
        BusinessStateConflictException stale = assertThrows(BusinessStateConflictException.class,
                () -> acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                        UUID.randomUUID(), f.applicationId, first.id(), 1)));
        assertEquals("CONTRACT_VERSION_STALE", stale.getErrorCode());
        acknowledge.acknowledge(new AcknowledgeLoanContractUseCase.Command(
                UUID.randomUUID(), f.applicationId, second.id(), 2));
        currentUser.set(staff());
        confirm.confirm(new ConfirmContractReadinessUseCase.Command(
                UUID.randomUUID(), f.applicationId, second.id(), 2));
        BusinessStateConflictException regeneration = assertThrows(BusinessStateConflictException.class,
                () -> prepare.prepare(new PrepareLoanContractUseCase.Command(
                        UUID.randomUUID(), f.applicationId, 2,
                        com.meridian.platform.loan.domain.model.ContractSupersessionReason.DISBURSEMENT_ACCOUNT_REFRESH)));
        assertEquals("INVALID_APPLICATION_STATE", regeneration.getErrorCode());
        assertEquals(1, count("select count(*) from loan_contracts where loan_application_id = ? and status <> 'SUPERSEDED'", f.applicationId));
    }
    private Fixture fixture() {
        UUID customerId = UUID.randomUUID(), customerUserId = UUID.randomUUID(), bankId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID(), offerId = UUID.randomUUID(), offerItemId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID(), limitId = UUID.randomUUID(); String token = customerId.toString().replace("-", "");
        jdbc.update("insert into customers (id, customer_number, status, verification_status, profile_completion_status) values (?, ?, 'ACTIVE', 'UNVERIFIED', 'INCOMPLETE')",
                customerId, "CUS-C-" + token.substring(0, 12));
        jdbc.update("insert into users (id,email,normalized_email,password_hash,user_type,status,display_name,customer_id) values (?,?,?,'not-used','CUSTOMER','ACTIVE','Contract Customer',?)",
                customerUserId, "contract-" + token + "@meridian.test", "contract-" + token + "@meridian.test", customerId);
        ProtectedSensitiveValue bank = customerProtector.protectBankAccountNumber("VCB", "1234567890");
        jdbc.update("insert into customer_bank_accounts (id,customer_id,bank_code,bank_name_snapshot,account_holder_name,account_number_ciphertext,account_number_fingerprint,account_number_last_four,status,primary_account) values (?,?, 'VCB','Vietcombank','MERIDIAN CUSTOMER',?,?,?,'ACTIVE',TRUE)",
                bankId, customerId, bank.ciphertext(), bank.fingerprint(), bank.lastFour());
        UUID productId = jdbc.queryForObject("select id from loan_products where product_code='SALARY_ADVANCE'", UUID.class);
        UUID policyId = jdbc.queryForObject("select id from loan_product_policies where loan_product_id=? and policy_code='DEFAULT_POLICY'", UUID.class, productId);
        jdbc.update("insert into loan_applications (id,customer_id,loan_product_id,application_number,product_code,product_type,status,requested_amount,requested_term_months,submitted_at) values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED','CONTRACT_PENDING',1000,1,current_timestamp)",
                applicationId, customerId, productId, "SA-C-" + token.substring(0, 16));
        jdbc.update("insert into approved_offers (id,loan_application_id,source_loan_product_policy_id,status,approved_principal,approved_term_months,interest_calculation_method,flat_monthly_interest_rate,total_interest,fee_amount,total_repayment_amount,repayment_method,generated_at,expires_at,accepted_at) values (?,?,?,'ACCEPTED',1000,1,'FLAT_ORIGINAL_PRINCIPAL',0.1,100,0,1100,'ON_SALARY_DATE',current_timestamp-interval '2 day',current_timestamp-interval '1 day',current_timestamp)",
                offerId, applicationId, policyId);
        jdbc.update("insert into approved_offer_repayment_items (id,approved_offer_id,installment_number,principal_due,interest_due,fee_due,total_due) values (?,?,1,1000,100,0,1100)", offerItemId, offerId);
        jdbc.update("insert into document_checklists (id,loan_application_id,stage,created_at) values (?,?, 'SUBMISSION',current_timestamp)", UUID.randomUUID(), applicationId);
        jdbc.update("insert into salary_advance_limits (id,customer_id,customer_partner_employee_link_id,total_limit,used_amount,reserved_amount,available_amount,status,last_refreshed_at) values (?,?,?,2000,0,1000,1000,'ACTIVE',current_timestamp)", limitId, customerId, linkId);
        jdbc.update("insert into salary_advance_limit_movements (id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) values (?,?,?,'RESERVED',1000,current_timestamp)", UUID.randomUUID(), limitId, applicationId);
        jdbc.update("insert into salary_advance_verifications (id,loan_application_id,verification_sequence,customer_id,customer_partner_employee_link_id,salary_advance_limit_id,partner_company_id,partner_employee_id,source_import_batch_id,employee_verification_outcome,product_verification_result,total_limit_snapshot,used_amount_snapshot,reserved_amount_snapshot,available_limit_snapshot,verified_at) values (?,?,1,?,?,?,?,?,?, 'MATCHED_ACTIVE','VERIFIED',2000,0,1000,1000,current_timestamp)",
                UUID.randomUUID(), applicationId, customerId, linkId, limitId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return new Fixture(customerId, customerUserId, applicationId);
    }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private String scalar(String sql, Object... args) { return jdbc.queryForObject(sql, String.class, args); }
    private static AuthenticatedUser staff() { return new AuthenticatedUser(ACCOUNTING, "accounting.officer@meridian.local", "STAFF", null, Set.of("ACCOUNTING_OFFICER"), Set.of()); }
    private static AuthenticatedUser customer(Fixture f) { return new AuthenticatedUser(f.customerUserId, "customer@meridian.test", "CUSTOMER", f.customerId, Set.of("CUSTOMER"), Set.of()); }
    private record Fixture(UUID customerId, UUID customerUserId, UUID applicationId) {}

    @TestConfiguration static class TestConfig {
        @Bean @Primary TestCurrentUserProvider testCurrentUserProvider() { return new TestCurrentUserProvider(); }
        @Bean @Primary ToggleAuditPublisher toggleAuditPublisher(ApplicationEventPublisher publisher) { return new ToggleAuditPublisher(publisher); }
    }
    static final class TestCurrentUserProvider implements CurrentUserProvider {
        private final ThreadLocal<AuthenticatedUser> value = new ThreadLocal<>();
        void set(AuthenticatedUser user) { value.set(user); }
        void clear() { value.remove(); }
        @Override public AuthenticatedUser currentUser() { return Objects.requireNonNull(value.get(), "Test user is required."); }
    }
    static final class ToggleAuditPublisher implements BusinessAuditPublisher {
        private final ApplicationEventPublisher publisher; volatile boolean failReadiness;
        ToggleAuditPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
        @Override public void publish(BusinessAuditEvent event) {
            if (failReadiness && event.entries().stream().anyMatch(entry ->
                    entry.action() == BusinessAuditAction.LOAN_CONTRACT_READINESS_CONFIRMED))
                throw new IllegalStateException("Injected audit failure");
            publisher.publishEvent(event);
        }
    }
}
