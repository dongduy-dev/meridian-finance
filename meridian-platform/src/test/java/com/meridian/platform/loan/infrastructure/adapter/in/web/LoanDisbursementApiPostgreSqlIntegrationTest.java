package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtectionContext;
import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtector;
import com.meridian.platform.loan.application.port.out.ProtectedBankAccountEnvelope;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                LoanDisbursementApiPostgreSqlIntegrationTest.TestConfig.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
@AutoConfigureMockMvc
class LoanDisbursementApiPostgreSqlIntegrationTest {

    private static final String SCHEMA = "meridian_disbursement_api_v31_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID CUSTOMER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");
    private static final String FULL_ACCOUNT_NUMBER = "01234567890";
    private static final String TRANSFER_REFERENCE = "I4-BANK-REFERENCE";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DisbursementBankAccountProtector accountProtector;
    @Autowired TestCurrentUserProvider currentUser;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void resetActor() {
        currentUser.clear();
    }

    @Test
    void securedHttpWorkflowRevealsThenConfirmsReplaysAndQueriesWithoutLeaks()
            throws Exception {
        Fixture fixture = fixture();
        useAccounting();

        reveal(fixture.applicationId(), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONTRACT_VERSION_STALE"));
        reveal(fixture.applicationId(), 1)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.contractId").value(fixture.contractId().toString()))
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.bankCode").value("VCB"))
                .andExpect(jsonPath("$.bankName").value("Meridian Test Bank"))
                .andExpect(jsonPath("$.accountHolderName").value("MERIDIAN CUSTOMER"))
                .andExpect(jsonPath("$.accountNumber").value(FULL_ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.ciphertext").doesNotExist())
                .andExpect(jsonPath("$.nonce").doesNotExist())
                .andExpect(jsonPath("$.keyId").doesNotExist())
                .andExpect(jsonPath("$.aad").doesNotExist())
                .andExpect(jsonPath("$.fingerprint").doesNotExist());

        assertEquals(1, count("select count(*) from audit_events where entity_id = ? "
                        + "and action = 'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'",
                fixture.contractId()));
        String revealAudit = text("select payload::text from audit_events where entity_id = ? "
                        + "and action = 'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'",
                fixture.contractId());
        assertTrue(revealAudit.contains(fixture.applicationId().toString()));
        assertTrue(revealAudit.contains(fixture.contractId().toString()));
        assertFalse(revealAudit.contains(FULL_ACCOUNT_NUMBER));
        assertFalse(revealAudit.contains("MERIDIAN CUSTOMER"));
        assertFalse(revealAudit.contains("VCB"));

        UUID requestId = UUID.randomUUID();
        String firstBody = confirm(fixture.applicationId(), requestId, "  i4-bank-reference  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("DISBURSED"))
                .andExpect(jsonPath("$.loanAccountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.disbursedAmount").value(1000))
                .andExpect(jsonPath("$.scheduleType").value("FINAL"))
                .andExpect(jsonPath("$.scheduleVersion").value(1))
                .andExpect(jsonPath("$.scheduleItems.length()").value(2))
                .andExpect(jsonPath("$.scheduleItems[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.scheduleItems[0].dueDate").value("2026-08-28"))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.externalTransferReference").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(JsonPath.read(firstBody, "$.loanAccountId"));
        UUID scheduleId = UUID.fromString(JsonPath.read(firstBody, "$.repaymentScheduleId"));

        confirm(fixture.applicationId(), requestId, TRANSFER_REFERENCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountId").value(accountId.toString()))
                .andExpect(jsonPath("$.repaymentScheduleId").value(scheduleId.toString()))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        assertEquals("DISBURSED", text(
                "select status from loan_applications where id = ?", fixture.applicationId()));
        assertEquals(1, count("select count(*) from loan_accounts "
                + "where loan_application_id = ?", fixture.applicationId()));
        assertEquals(1, count("select count(*) from manual_disbursements "
                + "where loan_application_id = ?", fixture.applicationId()));
        assertEquals(TRANSFER_REFERENCE, text(
                "select external_transfer_reference from manual_disbursements "
                        + "where loan_application_id = ?", fixture.applicationId()));
        assertEquals(1, count("select count(*) from repayment_schedules "
                + "where loan_application_id = ?", fixture.applicationId()));
        assertEquals(2, count("select count(*) from repayment_schedule_items item "
                        + "join repayment_schedules schedule on schedule.id = item.repayment_schedule_id "
                        + "where schedule.loan_application_id = ?", fixture.applicationId()));
        assertEquals(1, count("select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? and movement_type = 'DISBURSED_TO_USED'",
                fixture.applicationId()));
        assertEquals(1, count("select count(*) from loan_application_status_transitions "
                        + "where loan_application_id = ? and action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                fixture.applicationId()));
        assertEquals(1, count("select count(*) from audit_events where entity_id = ? "
                        + "and action = 'MANUAL_DISBURSEMENT_CONFIRMED'",
                fixture.applicationId()));
        assertMoney("1000", money("select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()));
        assertMoney("0", money("select reserved_amount from salary_advance_limits where id = ?",
                fixture.limitId()));
        assertMoney("4000", money("select available_amount from salary_advance_limits where id = ?",
                fixture.limitId()));

        reveal(fixture.applicationId(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("DISBURSEMENT_DESTINATION_REVEAL_NOT_ALLOWED"));
        assertEquals(1, count("select count(*) from audit_events where entity_id = ? "
                        + "and action = 'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'",
                fixture.contractId()));

        useCustomer(fixture.customerId());
        String ownerBody = query(fixture.applicationId(), "loan:read:own")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountId").value(accountId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.originatedPrincipal").value(1000))
                .andExpect(jsonPath("$.approvedTermMonths").value(2))
                .andExpect(jsonPath("$.totalInterest").value(100))
                .andExpect(jsonPath("$.totalFee").value(0))
                .andExpect(jsonPath("$.totalRepayment").value(1100))
                .andExpect(jsonPath("$.disbursementDestination.maskedAccountNumber")
                        .value("********"))
                .andExpect(jsonPath("$.finalRepaymentSchedule.scheduleId")
                        .value(scheduleId.toString()))
                .andExpect(jsonPath("$.finalRepaymentSchedule.scheduleType").value("FINAL"))
                .andExpect(jsonPath("$.finalRepaymentSchedule.items.length()").value(2))
                .andExpect(jsonPath("$.externalTransferReference").doesNotExist())
                .andExpect(jsonPath("$.accountNumberCiphertext").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(ownerBody.contains(FULL_ACCOUNT_NUMBER));
        assertFalse(ownerBody.contains(TRANSFER_REFERENCE));
        assertFalse(ownerBody.contains("protection"));

        useCustomer(UUID.randomUUID());
        query(fixture.applicationId(), "loan:read:own")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_ACCOUNT_NOT_FOUND"));

        useStaff();
        query(fixture.applicationId(), "loan:read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountId").value(accountId.toString()));

        useAccounting();
        confirm(fixture.applicationId(), UUID.randomUUID(), "OTHER-REFERENCE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DISBURSEMENT_ALREADY_COMPLETED"));
    }

    @Test
    void customerMissingForeignAndMissingAccountResponsesAreIndistinguishable()
            throws Exception {
        UUID applicationId = UUID.randomUUID();
        UUID requestingCustomerId = UUID.randomUUID();

        useCustomer(requestingCustomerId);
        String missing = query(applicationId, "loan:read:own")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Loan Account was not found."))
                .andReturn().getResponse().getContentAsString();

        Fixture foreignFixture = fixture(applicationId);
        String foreign = query(applicationId, "loan:read:own")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Loan Account was not found."))
                .andReturn().getResponse().getContentAsString();

        useCustomer(foreignFixture.customerId());
        String missingAccount = query(applicationId, "loan:read:own")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Loan Account was not found."))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> expected = stableError(missing);
        assertEquals(expected, stableError(foreign));
        assertEquals(expected, stableError(missingAccount));
        assertFalse(missing.contains(requestingCustomerId.toString()));
        assertFalse(foreign.contains(foreignFixture.customerId().toString()));
        assertFalse(missingAccount.contains(foreignFixture.customerId().toString()));
    }

    @Test
    void confirmationWithoutCurrentContractReturnsNotFound() throws Exception {
        UUID applicationId = applicationWithoutContract();
        useAccounting();

        confirm(applicationId, UUID.randomUUID(), "NO-CURRENT-CONTRACT")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_CONTRACT_MISSING"))
                .andExpect(jsonPath("$.message").value("Current Loan contract is missing."));
    }

    private ResultActions reveal(UUID applicationId, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts/current/"
                        + "disbursement-destination/reveal", applicationId)
                .with(authority("loan:disburse"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedContractVersion\":" + version + "}"));
    }

    private ResultActions confirm(UUID applicationId, UUID requestId, String reference)
            throws Exception {
        return mockMvc.perform(post("/api/v1/loan-applications/{id}/disbursements", applicationId)
                .with(authority("loan:disburse"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "requestId":"%s",
                          "expectedContractVersion":1,
                          "externalTransferReference":"%s",
                          "disbursementValueDate":"2026-07-28",
                          "firstRepaymentDate":"2026-08-28"
                        }
                        """.formatted(requestId, reference)));
    }

    private ResultActions query(UUID applicationId, String permission) throws Exception {
        return mockMvc.perform(get("/api/v1/loan-applications/{id}/loan-account", applicationId)
                .with(authority(permission)));
    }

    private Fixture fixture() {
        return fixture(UUID.randomUUID());
    }

    private Fixture fixture(UUID applicationId) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> createFixture(applicationId));
    }

    private Fixture createFixture(UUID applicationId) {
        UUID customerId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        UUID approvedOfferId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        String token = applicationId.toString().replace("-", "").substring(0, 12).toUpperCase();
        byte[] plaintext = FULL_ACCOUNT_NUMBER.getBytes(StandardCharsets.UTF_8);
        ProtectedBankAccountEnvelope envelope;
        try {
            envelope = accountProtector.protect(
                    plaintext,
                    new DisbursementBankAccountProtectionContext(
                            contractId, applicationId, customerId, bankAccountId));
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }

        jdbc.update("insert into customers "
                        + "(id,customer_number,status,verification_status,profile_completion_status) "
                        + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')",
                customerId, "CUS-I4-" + token);
        jdbc.update("insert into customer_bank_accounts "
                        + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                        + "account_number_ciphertext,account_number_fingerprint,"
                        + "account_number_last_four,status,primary_account) "
                        + "values (?,?,'VCB','Meridian Test Bank','MERIDIAN CUSTOMER',"
                        + "?,?,'7890','ACTIVE',true)",
                bankAccountId, customerId, "customer-ciphertext-" + token,
                "customer-fingerprint-" + token);
        UUID productId = uuid("select id from loan_products where product_code = 'SALARY_ADVANCE'");
        UUID policyId = uuid("select id from loan_product_policies where loan_product_id = ? "
                + "and active = true order by created_at desc limit 1", productId);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        jdbc.update("insert into loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,product_code,"
                        + "product_type,status,requested_amount,requested_term_months,submitted_at) "
                        + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED',"
                        + "'DISBURSEMENT_PENDING',1000,2,?)",
                applicationId, customerId, productId, "I4-" + token, now.minusMonths(1));
        jdbc.update("insert into approved_offers "
                        + "(id,loan_application_id,source_loan_product_policy_id,status,"
                        + "approved_principal,approved_term_months,interest_calculation_method,"
                        + "flat_monthly_interest_rate,total_interest,fee_amount,"
                        + "total_repayment_amount,repayment_method,generated_at,expires_at,accepted_at) "
                        + "values (?,?,?,'ACCEPTED',1000,2,'FLAT_ORIGINAL_PRINCIPAL',"
                        + "0.05,100,0,1100,'ON_SALARY_DATE',?,?,?)",
                approvedOfferId, applicationId, policyId,
                now.minusDays(2), now.minusDays(1), now.minusDays(1));
        UUID offerItem1 = UUID.randomUUID();
        UUID offerItem2 = UUID.randomUUID();
        jdbc.update("insert into approved_offer_repayment_items "
                        + "(id,approved_offer_id,installment_number,principal_due,"
                        + "interest_due,fee_due,total_due) values "
                        + "(?,?,1,500,50,0,550),(?,?,2,500,50,0,550)",
                offerItem1, approvedOfferId, offerItem2, approvedOfferId);
        jdbc.update("insert into loan_contracts "
                        + "(id,loan_application_id,approved_offer_id,contract_reference,"
                        + "contract_version,status,approved_principal,approved_term_months,"
                        + "interest_calculation_method,flat_monthly_interest_rate,total_interest,"
                        + "fee_amount,total_repayment_amount,repayment_method,customer_id,"
                        + "source_bank_account_id,bank_code,bank_name_snapshot,account_holder_name,"
                        + "account_number_last_four,primary_at_capture,active_at_capture,"
                        + "account_captured_at,protection_scheme,protection_key_id,protection_nonce,"
                        + "protected_account_number,protection_aad_version,preparation_request_id,"
                        + "prepared_by_user_id,prepared_at,acknowledgment_request_id,"
                        + "acknowledged_by_user_id,acknowledged_at,confirmation_request_id,"
                        + "confirmed_by_user_id,confirmed_at) values "
                        + "(?,?,?, ?,1,'READY_FOR_DISBURSEMENT',1000,2,"
                        + "'FLAT_ORIGINAL_PRINCIPAL',0.05,100,0,1100,'ON_SALARY_DATE',"
                        + "?,?,'VCB','Meridian Test Bank','MERIDIAN CUSTOMER','7890',true,true,"
                        + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                contractId, applicationId, approvedOfferId, "MCT-I4-" + token,
                customerId, bankAccountId, now.minusHours(2), envelope.protectionScheme(),
                envelope.keyId(), envelope.nonce(), envelope.ciphertext(), envelope.aadVersion(),
                UUID.randomUUID(), ACCOUNTING_USER_ID, now.minusHours(2),
                UUID.randomUUID(), CUSTOMER_USER_ID, now.minusMinutes(90),
                UUID.randomUUID(), ACCOUNTING_USER_ID, now.minusHours(1));
        jdbc.update("insert into loan_contract_repayment_items "
                        + "(id,loan_contract_id,source_approved_offer_repayment_item_id,"
                        + "installment_number,principal_due,interest_due,fee_due,total_due) values "
                        + "(?,?,?,1,500,50,0,550),(?,?,?,2,500,50,0,550)",
                UUID.randomUUID(), contractId, offerItem1,
                UUID.randomUUID(), contractId, offerItem2);
        jdbc.update("insert into salary_advance_limits "
                        + "(id,customer_id,customer_partner_employee_link_id,total_limit,"
                        + "used_amount,reserved_amount,available_amount,status,last_refreshed_at) "
                        + "values (?,?,?,5000,0,1000,4000,'ACTIVE',?)",
                limitId, customerId, linkId, now.minusDays(1));
        jdbc.update("insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                        + "values (?,?,?,'RESERVED',1000,?)",
                UUID.randomUUID(), limitId, applicationId, now.minusDays(10));
        jdbc.update("insert into salary_advance_verifications "
                        + "(id,loan_application_id,verification_sequence,customer_id,"
                        + "customer_partner_employee_link_id,salary_advance_limit_id,partner_company_id,"
                        + "partner_employee_id,source_import_batch_id,employee_verification_outcome,"
                        + "product_verification_result,total_limit_snapshot,used_amount_snapshot,"
                        + "reserved_amount_snapshot,available_limit_snapshot,verified_at) values "
                        + "(?,?,1,?,?,?,?,?,?,'MATCHED_ACTIVE','VERIFIED',5000,0,1000,4000,?)",
                UUID.randomUUID(), applicationId, customerId, linkId, limitId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.minusDays(10));
        return new Fixture(customerId, applicationId, contractId, limitId);
    }

    private UUID applicationWithoutContract() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UUID customerId = UUID.randomUUID();
            UUID applicationId = UUID.randomUUID();
            String token = applicationId.toString().replace("-", "")
                    .substring(0, 12).toUpperCase();
            UUID productId = uuid(
                    "select id from loan_products where product_code = 'SALARY_ADVANCE'");
            jdbc.update("insert into customers "
                            + "(id,customer_number,status,verification_status,"
                            + "profile_completion_status) "
                            + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')",
                    customerId, "CUS-I4-NC-" + token);
            jdbc.update("insert into loan_applications "
                            + "(id,customer_id,loan_product_id,application_number,product_code,"
                            + "product_type,status,requested_amount,requested_term_months,"
                            + "submitted_at) "
                            + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED','DRAFT',1000,2,?)",
                    applicationId, customerId, productId, "I4-NC-" + token,
                    LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(1));
            return applicationId;
        });
    }

    private static Map<String, Object> stableError(String responseBody) {
        Map<String, Object> error = new LinkedHashMap<>(JsonPath.read(responseBody, "$"));
        error.remove("timestamp");
        return error;
    }

    private void useAccounting() {
        currentUser.set(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse", "loan:read")));
    }

    private void useStaff() {
        currentUser.set(new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:read")));
    }

    private void useCustomer(UUID customerId) {
        currentUser.set(new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("loan:read:own")));
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(String value) {
        return user("actor").authorities(new SimpleGrantedAuthority(value));
    }

    private int count(String sql, Object argument) {
        return jdbc.queryForObject(sql, Integer.class, argument);
    }

    private String text(String sql, Object argument) {
        return jdbc.queryForObject(sql, String.class, argument);
    }

    private UUID uuid(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private BigDecimal money(String sql, Object argument) {
        return jdbc.queryForObject(sql, BigDecimal.class, argument);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    record Fixture(UUID customerId, UUID applicationId, UUID contractId, UUID limitId) {
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        TestCurrentUserProvider testCurrentUserProvider() {
            return new TestCurrentUserProvider();
        }

        @Bean
        @Primary
        Clock incrementFourClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    static final class TestCurrentUserProvider implements CurrentUserProvider {
        private AuthenticatedUser current;

        @Override
        public AuthenticatedUser currentUser() {
            if (current == null) {
                throw new IllegalStateException("Test actor was not configured.");
            }
            return current;
        }

        void set(AuthenticatedUser actor) {
            current = actor;
        }

        void clear() {
            current = null;
        }
    }
}
