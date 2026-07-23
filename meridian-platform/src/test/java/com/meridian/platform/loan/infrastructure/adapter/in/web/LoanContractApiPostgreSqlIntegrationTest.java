package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.customer.application.port.out.CustomerSensitiveValueProtector;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                LoanContractApiPostgreSqlIntegrationTest.TestConfig.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
@AutoConfigureMockMvc
class LoanContractApiPostgreSqlIntegrationTest {

    private static final String SCHEMA = "meridian_contract_api_v26_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerSensitiveValueProtector customerProtector;
    @Autowired TestCurrentUserProvider currentUser;
    @Autowired ToggleAuditPublisher auditPublisher;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void reset() {
        currentUser.clear();
        auditPublisher.failReadiness = false;
    }

    @Test
    void securedApiCompletesReadinessAndReplaysCommandsWithoutSensitiveOrDisbursementEffects() throws Exception {
        Fixture fixture = fixture();
        UUID preparationRequestId = UUID.randomUUID();
        String firstPreparation = prepare(fixture, preparationRequestId, 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARED"))
                .andExpect(jsonPath("$.disbursementBankAccount.maskedAccountNumber").value("****7890"))
                .andExpect(jsonPath("$.disbursementBankAccount.ciphertext").doesNotExist())
                .andExpect(jsonPath("$.disbursementBankAccount.nonce").doesNotExist())
                .andExpect(jsonPath("$.disbursementBankAccount.keyId").doesNotExist())
                .andExpect(jsonPath("$.disbursementBankAccount.aadVersion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String contractId = JsonPath.read(firstPreparation, "$.contractId");

        String replayedPreparation = prepare(fixture, preparationRequestId, 0, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(contractId, JsonPath.read(replayedPreparation, "$.contractId"));
        assertEquals(1, auditCount(contractId, "LOAN_CONTRACT_PREPARED"));

        useCustomer(fixture);
        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current", fixture.applicationId)
                        .with(authority("loan:read:own")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(contractId))
                .andExpect(jsonPath("$.availableCustomerAction").value("ACKNOWLEDGE"));

        UUID acknowledgmentRequestId = UUID.randomUUID();
        acknowledge(fixture, acknowledgmentRequestId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        acknowledge(fixture, acknowledgmentRequestId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(contractId));
        assertEquals(1, auditCount(contractId, "LOAN_CONTRACT_ACKNOWLEDGED"));

        useAccounting();
        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current/readiness", fixture.applicationId)
                        .queryParam("expectedContractVersion", "1")
                        .with(authority("loan:contract:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.blockerCodes").isEmpty())
                .andExpect(jsonPath("$.calculationSemantics").value("POINT_IN_TIME_ADVISORY"))
                .andExpect(jsonPath("$.recomputedDuringConfirmation").value(true));

        UUID confirmationRequestId = UUID.randomUUID();
        confirm(fixture, confirmationRequestId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_DISBURSEMENT"));
        confirm(fixture, confirmationRequestId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(contractId));

        assertEquals("DISBURSEMENT_PENDING", scalar(
                "select status from loan_applications where id = ?",
                fixture.applicationId
        ));
        assertEquals("READY_FOR_DISBURSEMENT", scalar(
                "select status from loan_contracts where id = ?",
                UUID.fromString(contractId)
        ));
        assertEquals(1, count(
                "select count(*) from loan_contracts where loan_application_id = ? and status <> 'SUPERSEDED'",
                fixture.applicationId
        ));
        assertEquals(1, count(
                "select count(*) from loan_application_status_transitions "
                        + "where loan_application_id = ? and action = 'CONFIRM_DISBURSEMENT_READINESS'",
                fixture.applicationId
        ));
        assertEquals(1, auditCount(contractId, "LOAN_CONTRACT_READINESS_CONFIRMED"));
        assertEquals(List.of("RESERVED"), jdbc.queryForList(
                "select movement_type from salary_advance_limit_movements "
                        + "where loan_application_id = ? order by occurred_at",
                String.class,
                fixture.applicationId
        ));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? and movement_type <> 'RESERVED'",
                fixture.applicationId
        ));
        assertNull(jdbc.queryForObject("select to_regclass(?)", String.class, SCHEMA + ".loan_accounts"));
        assertNull(jdbc.queryForObject("select to_regclass(?)", String.class, SCHEMA + ".disbursements"));

        String auditPayloads = jdbc.queryForObject(
                "select coalesce(string_agg(payload::text, ''), '') from audit_events where entity_id = ?",
                String.class,
                UUID.fromString(contractId)
        );
        assertTrue(auditPayloads != null && !auditPayloads.contains("7890")
                && !auditPayloads.contains("MERIDIAN CUSTOMER")
                && !auditPayloads.contains("ciphertext"));
    }

    @Test
    void regenerationCapturesTheNewPrimaryDestinationAndRequiresFreshAcknowledgment() throws Exception {
        Fixture fixture = fixture();
        String firstBody = prepare(fixture, UUID.randomUUID(), 0, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID firstContractId = UUID.fromString(JsonPath.read(firstBody, "$.contractId"));
        acknowledge(fixture, UUID.randomUUID(), 1).andExpect(status().isOk());

        UUID alternateBankAccountId = UUID.randomUUID();
        ProtectedSensitiveValue alternate = customerProtector.protectBankAccountNumber("ACB", "9876543210");
        jdbc.update(
                "insert into customer_bank_accounts "
                        + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                        + "account_number_ciphertext,account_number_fingerprint,account_number_last_four,"
                        + "status,primary_account) values (?,?, 'ACB','Asia Commercial Bank','MERIDIAN CUSTOMER',"
                        + "?,?,?,'ACTIVE',FALSE)",
                alternateBankAccountId,
                fixture.customerId,
                alternate.ciphertext(),
                alternate.fingerprint(),
                alternate.lastFour()
        );
        jdbc.update("update customer_bank_accounts set primary_account = false where id = ?", fixture.bankAccountId);
        jdbc.update("update customer_bank_accounts set primary_account = true where id = ?", alternateBankAccountId);

        String regeneratedBody = prepare(
                fixture,
                UUID.randomUUID(),
                1,
                "DISBURSEMENT_ACCOUNT_REFRESH"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value(2))
                .andExpect(jsonPath("$.status").value("PREPARED"))
                .andExpect(jsonPath("$.disbursementBankAccount.bankCode").value("ACB"))
                .andExpect(jsonPath("$.disbursementBankAccount.maskedAccountNumber").value("****3210"))
                .andExpect(jsonPath("$.acknowledgedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID secondContractId = UUID.fromString(JsonPath.read(regeneratedBody, "$.contractId"));

        assertEquals("SUPERSEDED", scalar("select status from loan_contracts where id = ?", firstContractId));
        assertEquals(alternateBankAccountId, jdbc.queryForObject(
                "select source_bank_account_id from loan_contracts where id = ?",
                UUID.class,
                secondContractId
        ));
        assertEquals(1, count(
                """
                        select count(*)
                        from loan_contracts first_contract
                        join loan_contracts second_contract
                          on second_contract.loan_application_id = first_contract.loan_application_id
                        where first_contract.id = ? and second_contract.id = ?
                          and first_contract.approved_offer_id = second_contract.approved_offer_id
                          and first_contract.approved_principal = second_contract.approved_principal
                          and first_contract.approved_term_months = second_contract.approved_term_months
                          and first_contract.total_interest = second_contract.total_interest
                          and first_contract.fee_amount = second_contract.fee_amount
                          and first_contract.total_repayment_amount = second_contract.total_repayment_amount
                        """,
                firstContractId,
                secondContractId
        ));
        assertEquals(
                repaymentSnapshot(firstContractId),
                repaymentSnapshot(secondContractId)
        );

        acknowledge(fixture, UUID.randomUUID(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONTRACT_VERSION_STALE"));
        acknowledge(fixture, UUID.randomUUID(), 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        confirm(fixture, UUID.randomUUID(), 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_DISBURSEMENT"));
        prepare(fixture, UUID.randomUUID(), 2, "DISBURSEMENT_ACCOUNT_REFRESH")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_APPLICATION_STATE"));
    }

    @Test
    void ownershipAndChangingReadinessInputsBlockUnsafeProgress() throws Exception {
        Fixture accountFixture = fixture();
        prepare(accountFixture, UUID.randomUUID(), 0, null).andExpect(status().isOk());

        currentUser.set(otherCustomer());
        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current", accountFixture.applicationId)
                        .with(authority("loan:read:own")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_ACCESS_DENIED"));
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/acknowledgment",
                        accountFixture.applicationId)
                        .with(authority("loan:contract:acknowledge:own"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acknowledgmentBody(UUID.randomUUID(), 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_ACCESS_DENIED"));

        acknowledge(accountFixture, UUID.randomUUID(), 1).andExpect(status().isOk());
        jdbc.update(
                "update customer_bank_accounts set status = 'DEACTIVATED', primary_account = false, "
                        + "deactivated_at = current_timestamp where id = ?",
                accountFixture.bankAccountId
        );
        readiness(accountFixture, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockerCodes[0]").value("CAPTURED_ACCOUNT_INACTIVE"));
        confirm(accountFixture, UUID.randomUUID(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CAPTURED_ACCOUNT_INACTIVE"));
        assertPendingAndAcknowledged(accountFixture);

        Fixture documentFixture = fixture();
        prepare(documentFixture, UUID.randomUUID(), 0, null).andExpect(status().isOk());
        acknowledge(documentFixture, UUID.randomUUID(), 1).andExpect(status().isOk());
        UUID checklistId = jdbc.queryForObject(
                "select id from document_checklists where loan_application_id = ? and stage = 'SUBMISSION'",
                UUID.class,
                documentFixture.applicationId
        );
        jdbc.update(
                "insert into document_checklist_items "
                        + "(id,checklist_id,document_type,requirement_status,created_at,updated_at) "
                        + "values (?,?,'RECENT_PAYSLIP','REQUIRED',current_timestamp,current_timestamp)",
                UUID.randomUUID(),
                checklistId
        );
        readiness(documentFixture, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockerCodes[0]").value("DOCUMENTS_NOT_PROCESSING_READY"));
        confirm(documentFixture, UUID.randomUUID(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENTS_NOT_PROCESSING_READY"));
        assertPendingAndAcknowledged(documentFixture);
    }

    @Test
    void concurrentConfirmationReplayCreatesOneExternallyVisibleTransition() throws Exception {
        Fixture fixture = fixture();
        String prepared = prepare(fixture, UUID.randomUUID(), 0, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String contractId = JsonPath.read(prepared, "$.contractId");
        acknowledge(fixture, UUID.randomUUID(), 1).andExpect(status().isOk());
        UUID confirmationRequestId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(() -> concurrentConfirmation(
                    fixture, confirmationRequestId, start));
            Future<Integer> second = pool.submit(() -> concurrentConfirmation(
                    fixture, confirmationRequestId, start));
            start.countDown();
            assertEquals(200, first.get(30, TimeUnit.SECONDS));
            assertEquals(200, second.get(30, TimeUnit.SECONDS));
        }

        assertEquals(1, count(
                "select count(*) from loan_application_status_transitions "
                        + "where loan_application_id = ? and action = 'CONFIRM_DISBURSEMENT_READINESS'",
                fixture.applicationId
        ));
        assertEquals(1, auditCount(contractId, "LOAN_CONTRACT_READINESS_CONFIRMED"));
    }

    @Test
    void auditFailureRollsBackAndNoPartialTransitionIsVisibleThroughTheApi() throws Exception {
        Fixture fixture = fixture();
        prepare(fixture, UUID.randomUUID(), 0, null).andExpect(status().isOk());
        acknowledge(fixture, UUID.randomUUID(), 1).andExpect(status().isOk());
        auditPublisher.failReadiness = true;

        assertThrows(ServletException.class, () ->
                confirm(fixture, UUID.randomUUID(), 1).andReturn());

        auditPublisher.failReadiness = false;
        assertPendingAndAcknowledged(fixture);
        useCustomer(fixture);
        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current", fixture.applicationId)
                        .with(authority("loan:read:own")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    private ResultActions prepare(
            Fixture fixture,
            UUID requestId,
            int expectedVersion,
            String reason
    ) throws Exception {
        useAccounting();
        String reasonField = reason == null
                ? "\"supersessionReasonCode\": null"
                : "\"supersessionReasonCode\": \"" + reason + "\"";
        return mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts", fixture.applicationId)
                .with(authority("loan:contract:prepare"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "preparationRequestId": "%s",
                          "expectedCurrentContractVersion": %d,
                          %s
                        }
                        """.formatted(requestId, expectedVersion, reasonField)));
    }

    private ResultActions acknowledge(Fixture fixture, UUID requestId, int version) throws Exception {
        useCustomer(fixture);
        return mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/acknowledgment",
                        fixture.applicationId)
                .with(authority("loan:contract:acknowledge:own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(acknowledgmentBody(requestId, version)));
    }

    private ResultActions readiness(Fixture fixture, int version) throws Exception {
        useAccounting();
        return mockMvc.perform(get(
                        "/api/v1/loan-applications/{id}/contracts/current/readiness",
                        fixture.applicationId)
                .queryParam("expectedContractVersion", Integer.toString(version))
                .with(authority("loan:contract:read")));
    }

    private ResultActions confirm(Fixture fixture, UUID requestId, int version) throws Exception {
        useAccounting();
        return mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/readiness/confirm",
                        fixture.applicationId)
                .with(authority("loan:disbursement:prepare"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmationBody(requestId, version)));
    }

    private int concurrentConfirmation(
            Fixture fixture,
            UUID requestId,
            CountDownLatch start
    ) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        useAccounting();
        try {
            return mockMvc.perform(post(
                            "/api/v1/loan-applications/{id}/contracts/current/readiness/confirm",
                            fixture.applicationId)
                    .with(authority("loan:disbursement:prepare"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(confirmationBody(requestId, 1)))
                    .andReturn().getResponse().getStatus();
        } finally {
            currentUser.clear();
        }
    }

    private void assertPendingAndAcknowledged(Fixture fixture) {
        assertEquals("CONTRACT_PENDING", scalar(
                "select status from loan_applications where id = ?",
                fixture.applicationId
        ));
        assertEquals("ACKNOWLEDGED", scalar(
                "select status from loan_contracts where loan_application_id = ? and status <> 'SUPERSEDED'",
                fixture.applicationId
        ));
        assertEquals(0, count(
                "select count(*) from loan_application_status_transitions "
                        + "where loan_application_id = ? and action = 'CONFIRM_DISBURSEMENT_READINESS'",
                fixture.applicationId
        ));
    }

    private Fixture fixture() {
        UUID customerId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID offerItemId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        String token = customerId.toString().replace("-", "");

        jdbc.update(
                "insert into customers "
                        + "(id,customer_number,status,verification_status,profile_completion_status) "
                        + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')",
                customerId,
                "CUS-API-" + token.substring(0, 12)
        );
        jdbc.update(
                "insert into users "
                        + "(id,email,normalized_email,password_hash,user_type,status,display_name,customer_id) "
                        + "values (?,?,?,'not-used','CUSTOMER','ACTIVE','Contract API Customer',?)",
                customerUserId,
                "contract-api-" + token + "@meridian.test",
                "contract-api-" + token + "@meridian.test",
                customerId
        );
        ProtectedSensitiveValue bank = customerProtector.protectBankAccountNumber("VCB", "1234567890");
        jdbc.update(
                "insert into customer_bank_accounts "
                        + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                        + "account_number_ciphertext,account_number_fingerprint,account_number_last_four,"
                        + "status,primary_account) values (?,?, 'VCB','Vietcombank','MERIDIAN CUSTOMER',"
                        + "?,?,?,'ACTIVE',TRUE)",
                bankAccountId,
                customerId,
                bank.ciphertext(),
                bank.fingerprint(),
                bank.lastFour()
        );
        UUID productId = jdbc.queryForObject(
                "select id from loan_products where product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
        UUID policyId = jdbc.queryForObject(
                "select id from loan_product_policies "
                        + "where loan_product_id = ? and policy_code = 'DEFAULT_POLICY'",
                UUID.class,
                productId
        );
        jdbc.update(
                "insert into loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,product_code,product_type,status,"
                        + "requested_amount,requested_term_months,submitted_at) "
                        + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED','CONTRACT_PENDING',1000,1,current_timestamp)",
                applicationId,
                customerId,
                productId,
                "SA-API-" + token.substring(0, 16)
        );
        jdbc.update(
                "insert into approved_offers "
                        + "(id,loan_application_id,source_loan_product_policy_id,status,approved_principal,"
                        + "approved_term_months,interest_calculation_method,flat_monthly_interest_rate,"
                        + "total_interest,fee_amount,total_repayment_amount,repayment_method,"
                        + "generated_at,expires_at,accepted_at) "
                        + "values (?,?,?,'ACCEPTED',1000,1,'FLAT_ORIGINAL_PRINCIPAL',0.1,100,0,1100,"
                        + "'ON_SALARY_DATE',current_timestamp-interval '2 day',"
                        + "current_timestamp-interval '1 day',current_timestamp)",
                offerId,
                applicationId,
                policyId
        );
        jdbc.update(
                "insert into approved_offer_repayment_items "
                        + "(id,approved_offer_id,installment_number,principal_due,interest_due,fee_due,total_due) "
                        + "values (?,?,1,1000,100,0,1100)",
                offerItemId,
                offerId
        );
        jdbc.update(
                "insert into document_checklists (id,loan_application_id,stage,created_at) "
                        + "values (?,?,'SUBMISSION',current_timestamp)",
                UUID.randomUUID(),
                applicationId
        );
        jdbc.update(
                "insert into salary_advance_limits "
                        + "(id,customer_id,customer_partner_employee_link_id,total_limit,used_amount,"
                        + "reserved_amount,available_amount,status,last_refreshed_at) "
                        + "values (?,?,?,2000,0,1000,1000,'ACTIVE',current_timestamp)",
                limitId,
                customerId,
                linkId
        );
        jdbc.update(
                "insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                        + "values (?,?,?,'RESERVED',1000,current_timestamp)",
                UUID.randomUUID(),
                limitId,
                applicationId
        );
        jdbc.update(
                "insert into salary_advance_verifications "
                        + "(id,loan_application_id,verification_sequence,customer_id,"
                        + "customer_partner_employee_link_id,salary_advance_limit_id,partner_company_id,"
                        + "partner_employee_id,source_import_batch_id,employee_verification_outcome,"
                        + "product_verification_result,total_limit_snapshot,used_amount_snapshot,"
                        + "reserved_amount_snapshot,available_limit_snapshot,verified_at) "
                        + "values (?,?,1,?,?,?,?,?,?,'MATCHED_ACTIVE','VERIFIED',2000,0,1000,1000,current_timestamp)",
                UUID.randomUUID(),
                applicationId,
                customerId,
                linkId,
                limitId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        return new Fixture(customerId, customerUserId, bankAccountId, applicationId);
    }

    private List<java.util.Map<String, Object>> repaymentSnapshot(UUID contractId) {
        return jdbc.queryForList(
                "select installment_number,principal_due,interest_due,fee_due,total_due "
                        + "from loan_contract_repayment_items where loan_contract_id = ? "
                        + "order by installment_number",
                contractId
        );
    }

    private int auditCount(String contractId, String action) {
        return count(
                "select count(*) from audit_events where entity_id = ? and action = ?",
                UUID.fromString(contractId),
                action
        );
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String scalar(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private void useAccounting() {
        currentUser.set(accounting());
    }

    private void useCustomer(Fixture fixture) {
        currentUser.set(new AuthenticatedUser(
                fixture.customerUserId,
                "customer@meridian.test",
                "CUSTOMER",
                fixture.customerId,
                Set.of("CUSTOMER"),
                Set.of("loan:read:own", "loan:contract:acknowledge:own")
        ));
    }

    private static AuthenticatedUser accounting() {
        return new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting.officer@meridian.test",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:contract:prepare", "loan:contract:read", "loan:disbursement:prepare")
        );
    }

    private static AuthenticatedUser otherCustomer() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "other.customer@meridian.test",
                "CUSTOMER",
                UUID.randomUUID(),
                Set.of("CUSTOMER"),
                Set.of("loan:read:own", "loan:contract:acknowledge:own")
        );
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor authority(
            String authority
    ) {
        return user("api-actor").authorities(new SimpleGrantedAuthority(authority));
    }

    private static String acknowledgmentBody(UUID requestId, int version) {
        return """
                {
                  "acknowledgmentRequestId": "%s",
                  "expectedContractVersion": %d
                }
                """.formatted(requestId, version);
    }

    private static String confirmationBody(UUID requestId, int version) {
        return """
                {
                  "confirmationRequestId": "%s",
                  "expectedContractVersion": %d
                }
                """.formatted(requestId, version);
    }

    private record Fixture(
            UUID customerId,
            UUID customerUserId,
            UUID bankAccountId,
            UUID applicationId
    ) {
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
        ToggleAuditPublisher toggleAuditPublisher(ApplicationEventPublisher publisher) {
            return new ToggleAuditPublisher(publisher);
        }
    }

    static final class TestCurrentUserProvider implements CurrentUserProvider {

        private final ThreadLocal<AuthenticatedUser> current = new ThreadLocal<>();

        void set(AuthenticatedUser user) {
            current.set(user);
        }

        void clear() {
            current.remove();
        }

        @Override
        public AuthenticatedUser currentUser() {
            return Objects.requireNonNull(current.get(), "Test user is required.");
        }
    }

    static final class ToggleAuditPublisher implements BusinessAuditPublisher {

        private final ApplicationEventPublisher publisher;
        volatile boolean failReadiness;

        ToggleAuditPublisher(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void publish(BusinessAuditEvent event) {
            if (failReadiness && event.entries().stream().anyMatch(
                    entry -> entry.action() == BusinessAuditAction.LOAN_CONTRACT_READINESS_CONFIRMED
            )) {
                throw new IllegalStateException("Injected readiness-audit failure");
            }
            publisher.publishEvent(event);
        }
    }
}
