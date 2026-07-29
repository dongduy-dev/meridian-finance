package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ManualDisbursementActivationPostgreSqlTestSupport {

    static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 10, 0);
    static final LocalDate VALUE_DATE = LocalDate.of(2026, 7, 28);
    static final LocalDate FIRST_REPAYMENT_DATE = LocalDate.of(2026, 8, 28);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    ManualDisbursementActivationPostgreSqlTestSupport(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    Fixture createFixture(boolean ready, ProductCode productCode) {
        return transactions.execute(status -> {
            UUID customerId = UUID.randomUUID();
            UUID customerUserId = UUID.randomUUID();
            UUID bankAccountId = UUID.randomUUID();
            UUID applicationId = UUID.randomUUID();
            UUID approvedOfferId = UUID.randomUUID();
            UUID contractId = UUID.randomUUID();
            UUID firstContractItemId = UUID.randomUUID();
            UUID secondContractItemId = UUID.randomUUID();
            UUID limitId = productCode == ProductCode.SALARY_ADVANCE
                    ? UUID.randomUUID() : null;
            UUID linkId = productCode == ProductCode.SALARY_ADVANCE
                    ? UUID.randomUUID() : null;
            String token = customerId.toString().replace("-", "")
                    .substring(0, 12).toUpperCase();

            jdbc.update(
                    "insert into customers "
                            + "(id,customer_number,status,verification_status,profile_completion_status) "
                            + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')",
                    customerId, "CUS-I3-" + token
            );
            jdbc.update(
                    "insert into users "
                            + "(id,email,normalized_email,password_hash,user_type,status,display_name,customer_id) "
                            + "values (?,?,?,'not-used','CUSTOMER','ACTIVE','Increment Customer',?)",
                    customerUserId,
                    "i3-" + token + "@meridian.test",
                    "i3-" + token + "@meridian.test",
                    customerId
            );
            jdbc.update(
                    "insert into customer_bank_accounts "
                            + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                            + "account_number_ciphertext,account_number_fingerprint,"
                            + "account_number_last_four,status,primary_account) "
                            + "values (?,?,'VCB','Meridian Test Bank','MERIDIAN CUSTOMER',"
                            + "?,?,'7890','ACTIVE',true)",
                    bankAccountId, customerId,
                    "ciphertext-" + token, "fingerprint-" + token
            );
            UUID productId = jdbc.queryForObject(
                    "select id from loan_products where product_code = ?",
                    UUID.class,
                    productCode.name()
            );
            UUID policyId = jdbc.queryForObject(
                    "select id from loan_product_policies where loan_product_id = ? "
                            + "and active = true order by created_at desc limit 1",
                    UUID.class,
                    productId
            );
            jdbc.update(
                    "insert into loan_applications "
                            + "(id,customer_id,loan_product_id,application_number,product_code,"
                            + "product_type,status,requested_amount,requested_term_months,submitted_at) "
                            + "values (?,?,?,?,?,?,?,1000,2,?)",
                    applicationId, customerId, productId, "I3-" + token,
                    productCode.name(),
                    productCode == ProductCode.SALARY_ADVANCE
                            ? "SALARY_BASED" : "UNSECURED",
                    ready ? "DISBURSEMENT_PENDING" : "CONTRACT_PENDING",
                    NOW.minusMonths(1)
            );
            jdbc.update(
                    "insert into document_checklists (id,loan_application_id,stage,created_at) "
                            + "values (?,?,'SUBMISSION',?)",
                    UUID.randomUUID(), applicationId, NOW.minusMonths(1)
            );
            jdbc.update(
                    "insert into approved_offers "
                            + "(id,loan_application_id,source_loan_product_policy_id,status,"
                            + "approved_principal,approved_term_months,interest_calculation_method,"
                            + "flat_monthly_interest_rate,total_interest,fee_amount,"
                            + "total_repayment_amount,repayment_method,generated_at,expires_at,accepted_at) "
                            + "values (?,?,?,'ACCEPTED',1000,2,'FLAT_ORIGINAL_PRINCIPAL',"
                            + "0.05,100,0,1100,'ON_SALARY_DATE',?,?,?)",
                    approvedOfferId, applicationId, policyId,
                    NOW.minusDays(2), NOW.minusDays(1), NOW.minusDays(1)
            );
            UUID firstOfferItemId = UUID.randomUUID();
            UUID secondOfferItemId = UUID.randomUUID();
            jdbc.update(
                    "insert into approved_offer_repayment_items "
                            + "(id,approved_offer_id,installment_number,principal_due,"
                            + "interest_due,fee_due,total_due) "
                            + "values (?,?,1,500,50,0,550),(?,?,2,500,50,0,550)",
                    firstOfferItemId, approvedOfferId,
                    secondOfferItemId, approvedOfferId
            );
            jdbc.update(
                    "insert into loan_contracts "
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
                            + "confirmed_by_user_id,confirmed_at) "
                            + "values (?,?,?, ?,1,?,1000,2,'FLAT_ORIGINAL_PRINCIPAL',0.05,"
                            + "100,0,1100,'ON_SALARY_DATE',?,?,'VCB','Meridian Test Bank',"
                            + "'MERIDIAN CUSTOMER','7890',true,true,?,'AES-256-GCM','v1',"
                            + "decode('000000000000000000000000','hex'),decode('01','hex'),"
                            + "'DISBURSEMENT_ACCOUNT_V1',?,?,?, ?,?,?, ?,?,?)",
                    contractId, applicationId, approvedOfferId,
                    "MCT-I3-" + token,
                    ready ? "READY_FOR_DISBURSEMENT" : "ACKNOWLEDGED",
                    customerId, bankAccountId, NOW.minusHours(2),
                    UUID.randomUUID(), ACCOUNTING_USER_ID, NOW.minusHours(2),
                    UUID.randomUUID(), customerUserId, NOW.minusMinutes(90),
                    ready ? UUID.randomUUID() : null,
                    ready ? ACCOUNTING_USER_ID : null,
                    ready ? NOW.minusHours(1) : null
            );
            jdbc.update(
                    "insert into loan_contract_repayment_items "
                            + "(id,loan_contract_id,source_approved_offer_repayment_item_id,"
                            + "installment_number,principal_due,interest_due,fee_due,total_due) "
                            + "values (?,?,?,1,500,50,0,550),(?,?,?,2,500,50,0,550)",
                    firstContractItemId, contractId, firstOfferItemId,
                    secondContractItemId, contractId, secondOfferItemId
            );

            if (productCode == ProductCode.SALARY_ADVANCE) {
                jdbc.update(
                        "insert into salary_advance_limits "
                                + "(id,customer_id,customer_partner_employee_link_id,total_limit,"
                                + "used_amount,reserved_amount,available_amount,status,last_refreshed_at) "
                                + "values (?,?,?,5000,0,1000,4000,'ACTIVE',?)",
                        limitId, customerId, linkId, NOW.minusDays(1)
                );
                jdbc.update(
                        "insert into salary_advance_limit_movements "
                                + "(id,salary_advance_limit_id,loan_application_id,movement_type,"
                                + "amount,occurred_at) values (?,?,?,'RESERVED',1000,?)",
                        UUID.randomUUID(), limitId, applicationId, NOW.minusDays(10)
                );
                jdbc.update(
                        "insert into salary_advance_verifications "
                                + "(id,loan_application_id,verification_sequence,customer_id,"
                                + "customer_partner_employee_link_id,salary_advance_limit_id,"
                                + "partner_company_id,partner_employee_id,source_import_batch_id,"
                                + "employee_verification_outcome,product_verification_result,"
                                + "total_limit_snapshot,used_amount_snapshot,reserved_amount_snapshot,"
                                + "available_limit_snapshot,verified_at) "
                                + "values (?,?,1,?,?,?,?,?,?,'MATCHED_ACTIVE','VERIFIED',"
                                + "5000,0,1000,4000,?)",
                        UUID.randomUUID(), applicationId, customerId, linkId, limitId,
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        NOW.minusDays(10)
                );
            }
            return new Fixture(
                    customerId, applicationId, contractId,
                    List.of(firstContractItemId, secondContractItemId),
                    limitId, linkId, token, ready
            );
        });
    }

    ConfirmManualDisbursementUseCase.Command command(
            Fixture fixture,
            UUID requestId,
            String reference
    ) {
        return new ConfirmManualDisbursementUseCase.Command(
                requestId,
                fixture.applicationId(),
                1,
                reference,
                VALUE_DATE,
                FIRST_REPAYMENT_DATE
        );
    }

    void assertNoActivation(Fixture fixture) {
        assertNoGenericActivation(fixture);
        if (fixture.limitId() != null) {
            assertMoney("0", money(
                    "select used_amount from salary_advance_limits where id = ?",
                    fixture.limitId()));
            assertMoney("1000", money(
                    "select reserved_amount from salary_advance_limits where id = ?",
                    fixture.limitId()));
            assertMoney("4000", money(
                    "select available_amount from salary_advance_limits where id = ?",
                    fixture.limitId()));
        }
    }

    void assertNoGenericActivation(Fixture fixture) {
        assertEquals(fixture.ready() ? "DISBURSEMENT_PENDING" : "CONTRACT_PENDING", value(
                "select status from loan_applications where id = ?", fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from loan_accounts where loan_application_id = ?",
                fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from manual_disbursements where loan_application_id = ?",
                fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from repayment_schedules where loan_application_id = ?",
                fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from repayment_installment_progress progress "
                        + "join loan_accounts account on account.id = progress.loan_account_id "
                        + "where account.loan_application_id = ?", fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from loan_account_status_transitions history "
                        + "join loan_accounts account on account.id = history.loan_account_id "
                        + "where account.loan_application_id = ?", fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_schedule_items item "
                        + "on item.id = history.repayment_schedule_item_id "
                        + "join repayment_schedules schedule "
                        + "on schedule.id = item.repayment_schedule_id "
                        + "where schedule.loan_application_id = ?", fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? and movement_type = 'DISBURSED_TO_USED'",
                fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from loan_application_status_transitions "
                        + "where loan_application_id = ? and action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                fixture.applicationId()));
        assertEquals(0, count(
                "select count(*) from audit_events where entity_id = ? "
                        + "and action = 'MANUAL_DISBURSEMENT_CONFIRMED'",
                fixture.applicationId()));
    }

    Counts counts(UUID applicationId) {
        return new Counts(
                count("select count(*) from loan_accounts where loan_application_id = ?", applicationId),
                count("select count(*) from manual_disbursements where loan_application_id = ?", applicationId),
                count("select count(*) from repayment_schedules where loan_application_id = ?", applicationId),
                count("select count(*) from repayment_schedule_items item join repayment_schedules schedule "
                        + "on schedule.id = item.repayment_schedule_id where schedule.loan_application_id = ?", applicationId),
                count("select count(*) from salary_advance_limit_movements where loan_application_id = ? "
                        + "and movement_type = 'DISBURSED_TO_USED'", applicationId),
                count("select count(*) from loan_application_status_transitions where loan_application_id = ? "
                        + "and action = 'CONFIRM_MANUAL_DISBURSEMENT'", applicationId),
                count("select count(*) from audit_events where entity_id = ? "
                        + "and action = 'MANUAL_DISBURSEMENT_CONFIRMED'", applicationId)
        );
    }

    int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    String value(String sql, Object argument) {
        return jdbc.queryForObject(sql, String.class, argument);
    }

    UUID uuid(String sql, Object argument) {
        return jdbc.queryForObject(sql, UUID.class, argument);
    }

    Integer integer(String sql, Object argument) {
        return jdbc.queryForObject(sql, Integer.class, argument);
    }

    BigDecimal money(String sql, Object argument) {
        return jdbc.queryForObject(sql, BigDecimal.class, argument);
    }

    LocalDate date(String sql, Object argument) {
        return jdbc.queryForObject(sql, LocalDate.class, argument);
    }

    LocalDateTime dateTime(String sql, Object argument) {
        return jdbc.queryForObject(sql, LocalDateTime.class, argument);
    }

    static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, moneyValue(expected).compareTo(actual));
    }

    static BigDecimal moneyValue(String value) {
        return new BigDecimal(value).setScale(2);
    }

    record Fixture(
            UUID customerId,
            UUID applicationId,
            UUID contractId,
            List<UUID> contractItemIds,
            UUID limitId,
            UUID linkId,
            String token,
            boolean ready
    ) {
    }

    record Counts(
            int accounts,
            int disbursements,
            int schedules,
            int scheduleItems,
            int conversions,
            int transitions,
            int audits
    ) {
    }
}
