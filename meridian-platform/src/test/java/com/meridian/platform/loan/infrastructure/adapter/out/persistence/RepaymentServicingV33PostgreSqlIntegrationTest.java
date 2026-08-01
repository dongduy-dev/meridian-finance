package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class RepaymentServicingV33PostgreSqlIntegrationTest {

    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final LocalDateTime ACTIVATED_AT =
            LocalDateTime.of(2026, 1, 10, 9, 30);
    private static final LocalDate FIRST_DUE_DATE =
            LocalDate.of(2026, 1, 20);
    private static final LocalDate LAST_DUE_DATE =
            LocalDate.of(2026, 2, 20);

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void cleanV1ThroughV33InstallsThePhysicalFoundation() {
        String schema = schemaName("clean");
        try {
            migrateTo(schema, "33");

            assertEquals("33", latestVersion(schema));
            for (String table : List.of(
                    "repayment_transactions",
                    "repayment_allocations",
                    "repayment_installment_progress",
                    "loan_account_status_transitions",
                    "repayment_installment_status_transitions"
            )) {
                assertNotNull(jdbc.queryForObject(
                        "select to_regclass(?)",
                        String.class,
                        schema + "." + table
                ));
            }
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void upgradesV32ToV33WithDeterministicActivationDateBackfill() {
        String schema = schemaName("backfill");
        try {
            migrateTo(schema, "32");
            Fixture fixture = insertActivatedSalaryAdvance(schema);
            List<String> immutableScheduleBefore = scheduleRows(schema, fixture);
            int auditBefore = count(
                    schema,
                    "audit_events",
                    "action in ('REPAYMENT_RECORDED',"
                            + "'LOAN_ACCOUNT_STATUS_CHANGED')"
            );

            migrateTo(schema, "33");

            assertEquals("33", latestVersion(schema));
            assertEquals(immutableScheduleBefore, scheduleRows(schema, fixture));
            assertEquals(2, count(
                    schema,
                    "repayment_installment_progress",
                    "repayment_schedule_id = '" + fixture.scheduleId() + "'"
            ));
            assertEquals(2, count(
                    schema,
                    "repayment_installment_progress",
                    "repayment_schedule_id = '" + fixture.scheduleId() + "' "
                            + "and status = 'NOT_DUE' "
                            + "and principal_paid = 0 "
                            + "and interest_paid = 0 and fee_paid = 0 "
                            + "and servicing_evaluation_date = date '2026-01-10'"
            ));
            assertEquals("ACTIVE", value(
                    schema,
                    "select status from loan_accounts where id = ?",
                    fixture.accountId()
            ));
            assertEquals(LocalDate.of(2026, 1, 10), jdbc.queryForObject(
                    "select servicing_evaluation_date from " + schema
                            + ".loan_accounts where id = ?",
                    LocalDate.class,
                    fixture.accountId()
            ));
            assertEquals("0.00|0.00|0.00|0.00|1000.00|100.00|0.00|1100.00",
                    value(
                            schema,
                            "select concat(principal_paid,'|',interest_paid,'|',"
                                    + "fee_paid,'|',total_paid,'|',"
                                    + "principal_outstanding,'|',"
                                    + "interest_outstanding,'|',"
                                    + "fee_outstanding,'|',total_outstanding) "
                                    + "from loan_accounts where id = ?",
                            fixture.accountId()
                    ));
            assertEquals(1, count(
                    schema,
                    "loan_account_status_transitions",
                    "loan_account_id = '" + fixture.accountId() + "' "
                            + "and sequence_number = 1 "
                            + "and from_status is null and to_status = 'ACTIVE' "
                            + "and action = 'ACTIVATION_INITIALIZED'"
            ));
            assertEquals(2, count(
                    schema,
                    "repayment_installment_status_transitions history "
                            + "join " + schema + ".repayment_schedule_items item "
                            + "on item.id = history.repayment_schedule_item_id",
                    "item.repayment_schedule_id = '" + fixture.scheduleId() + "' "
                            + "and history.sequence_number = 1 "
                            + "and history.from_status is null "
                            + "and history.to_status = 'NOT_DUE' "
                            + "and history.action = 'ACTIVATION_INITIALIZED'"
            ));
            assertEquals(auditBefore, count(
                    schema,
                    "audit_events",
                    "action in ('REPAYMENT_RECORDED',"
                            + "'LOAN_ACCOUNT_STATUS_CHANGED')"
            ));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void rejectsEveryDeclaredIncompatiblePreflightState() {
        assertPreflightRejected(
                "unexpected_table",
                false,
                (schema, fixture) -> jdbc.execute(
                        "create table " + schema
                                + ".repayment_transactions (id uuid)"
                ),
                "unexpectedly exists"
        );
        assertPreflightRejected(
                "released",
                true,
                (schema, fixture) -> replicationDisabled(schema, () -> jdbc.update(
                        "update " + schema
                                + ".salary_advance_limit_movements "
                                + "set movement_type = 'REPAID_RELEASED' "
                                + "where movement_type = 'DISBURSED_TO_USED' "
                                + "and loan_application_id = ?",
                        fixture.applicationId()
                )),
                "REPAID_RELEASED movement lacks repayment evidence"
        );
        assertPreflightRejected(
                "unsupported",
                true,
                (schema, fixture) -> replicationDisabled(schema, () -> jdbc.update(
                        "update " + schema + ".loan_applications "
                                + "set product_code = 'UNSECURED_CONSUMER_LOAN', "
                                + "product_type = 'UNSECURED' where id = ?",
                        fixture.applicationId()
                )),
                "unsupported-product activated LoanAccount"
        );
        assertPreflightRejected(
                "settled",
                true,
                (schema, fixture) -> replicationDisabled(schema, () -> jdbc.update(
                        "update " + schema
                                + ".loan_accounts set status = 'SETTLED' where id = ?",
                        fixture.accountId()
                )),
                "settled or closed LoanAccount"
        );
        assertPreflightRejected(
                "activation",
                true,
                (schema, fixture) -> replicationDisabled(schema, () -> jdbc.update(
                        "delete from " + schema
                                + ".manual_disbursements where loan_account_id = ?",
                        fixture.accountId()
                )),
                "lacks complete activation evidence"
        );
        assertPreflightRejected(
                "schedule",
                true,
                (schema, fixture) -> replicationDisabled(schema, () -> jdbc.update(
                        "delete from " + schema
                                + ".repayment_schedule_items "
                                + "where repayment_schedule_id = ? "
                                + "and installment_number = 2",
                        fixture.scheduleId()
                )),
                "final schedule for LoanAccount"
        );
        assertPreflightRejected(
                "limit",
                true,
                (schema, fixture) -> replicationDisabled(schema, () -> jdbc.update(
                        "update " + schema + ".salary_advance_limits "
                                + "set used_amount = 999, available_amount = 4001 "
                                + "where id = ?",
                        fixture.limitId()
                )),
                "Salary Advance limit"
        );
        assertPreflightRejected(
                "permission",
                false,
                (schema, fixture) -> jdbc.update(
                        "delete from " + schema + ".role_permissions "
                                + "where permission_id = (select id from " + schema
                                + ".permissions where code = 'repayment:update') "
                                + "and role_id = (select id from " + schema
                                + ".roles where code = 'ACCOUNTING_OFFICER')"
                ),
                "repayment:update permission or current grant"
        );
    }

    private void assertPreflightRejected(
            String suffix,
            boolean activatedFixture,
            BiConsumer<String, Fixture> mutation,
            String expectedMessage
    ) {
        String schema = schemaName(suffix);
        try {
            migrateTo(schema, "32");
            Fixture fixture = activatedFixture
                    ? insertActivatedSalaryAdvance(schema)
                    : Fixture.empty();
            mutation.accept(schema, fixture);

            FlywayException failure = assertThrows(
                    FlywayException.class,
                    () -> migrateTo(schema, "33")
            );

            assertTrue(
                    failure.getMessage().contains(expectedMessage),
                    () -> "Expected migration failure containing: "
                            + expectedMessage + " but was: " + failure.getMessage()
            );
            assertEquals("32", latestVersion(schema));
        } finally {
            dropSchema(schema);
        }
    }

    private Fixture insertActivatedSalaryAdvance(String schema) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(ignored -> {
            jdbc.execute("set local search_path to " + schema);
            String prefix = schema + ".";
        UUID customerId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID firstContractItemId = UUID.randomUUID();
        UUID secondContractItemId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        String token = customerId.toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        jdbc.update(
                "insert into " + prefix + "customers "
                        + "(id,customer_number,status,verification_status,"
                        + "profile_completion_status) "
                        + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')",
                customerId,
                "CUS-V33-" + token
        );
        jdbc.update(
                "insert into " + prefix + "customer_bank_accounts "
                        + "(id,customer_id,bank_code,bank_name_snapshot,"
                        + "account_holder_name,account_number_ciphertext,"
                        + "account_number_fingerprint,account_number_last_four,"
                        + "status,primary_account) "
                        + "values (?,?,'VCB','Meridian Test Bank',"
                        + "'MERIDIAN CUSTOMER',?,?,'7890','ACTIVE',true)",
                bankAccountId,
                customerId,
                "ciphertext-" + token,
                "fingerprint-" + token
        );
        UUID productId = jdbc.queryForObject(
                "select id from " + prefix
                        + "loan_products where product_code = 'SALARY_ADVANCE'",
                UUID.class
        );
        UUID policyId = jdbc.queryForObject(
                "select id from " + prefix
                        + "loan_product_policies where loan_product_id = ? "
                        + "and active order by created_at desc limit 1",
                UUID.class,
                productId
        );
        jdbc.update(
                "insert into " + prefix + "loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,"
                        + "product_code,product_type,status,requested_amount,"
                        + "requested_term_months,submitted_at) "
                        + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED',"
                        + "'DISBURSED',1000,2,?)",
                applicationId,
                customerId,
                productId,
                "SA-V33-" + token,
                ACTIVATED_AT.minusMonths(1)
        );
        jdbc.update(
                "insert into " + prefix + "approved_offers "
                        + "(id,loan_application_id,"
                        + "source_loan_product_policy_id,status,"
                        + "approved_principal,approved_term_months,"
                        + "interest_calculation_method,"
                        + "flat_monthly_interest_rate,total_interest,fee_amount,"
                        + "total_repayment_amount,repayment_method,generated_at,"
                        + "expires_at,accepted_at) values "
                        + "(?,?,?,'ACCEPTED',1000,2,"
                        + "'FLAT_ORIGINAL_PRINCIPAL',0.05,100,0,1100,"
                        + "'ON_SALARY_DATE',?,?,?)",
                offerId,
                applicationId,
                policyId,
                ACTIVATED_AT.minusDays(2),
                ACTIVATED_AT.minusDays(1),
                ACTIVATED_AT.minusDays(1)
        );
        UUID firstOfferItemId = UUID.randomUUID();
        UUID secondOfferItemId = UUID.randomUUID();
        jdbc.update(
                "insert into " + prefix + "approved_offer_repayment_items "
                        + "(id,approved_offer_id,installment_number,"
                        + "principal_due,interest_due,fee_due,total_due) values "
                        + "(?,?,1,500,50,0,550),(?,?,2,500,50,0,550)",
                firstOfferItemId,
                offerId,
                secondOfferItemId,
                offerId
        );
        jdbc.update(
                "insert into " + prefix + "loan_contracts "
                        + "(id,loan_application_id,approved_offer_id,"
                        + "contract_reference,contract_version,status,"
                        + "approved_principal,approved_term_months,"
                        + "interest_calculation_method,"
                        + "flat_monthly_interest_rate,total_interest,fee_amount,"
                        + "total_repayment_amount,repayment_method,customer_id,"
                        + "source_bank_account_id,bank_code,bank_name_snapshot,"
                        + "account_holder_name,account_number_last_four,"
                        + "primary_at_capture,active_at_capture,"
                        + "account_captured_at,protection_scheme,"
                        + "protection_key_id,protection_nonce,"
                        + "protected_account_number,protection_aad_version,"
                        + "preparation_request_id,prepared_by_user_id,"
                        + "prepared_at,acknowledgment_request_id,"
                        + "acknowledged_by_user_id,acknowledged_at,"
                        + "confirmation_request_id,confirmed_by_user_id,"
                        + "confirmed_at) values "
                        + "(?,?,?, ?,1,'READY_FOR_DISBURSEMENT',1000,2,"
                        + "'FLAT_ORIGINAL_PRINCIPAL',0.05,100,0,1100,"
                        + "'ON_SALARY_DATE',?,?,'VCB','Meridian Test Bank',"
                        + "'MERIDIAN CUSTOMER','7890',true,true,?,"
                        + "'AES-256-GCM','v1',"
                        + "decode('000000000000000000000000','hex'),"
                        + "decode('01','hex'),'DISBURSEMENT_ACCOUNT_V1',"
                        + "?,?,?,?,?,?,?,?,?)",
                contractId,
                applicationId,
                offerId,
                "MCT-V33-" + token,
                customerId,
                bankAccountId,
                ACTIVATED_AT.minusHours(2),
                UUID.randomUUID(),
                ACCOUNTING_USER_ID,
                ACTIVATED_AT.minusHours(2),
                UUID.randomUUID(),
                ACCOUNTING_USER_ID,
                ACTIVATED_AT.minusMinutes(90),
                UUID.randomUUID(),
                ACCOUNTING_USER_ID,
                ACTIVATED_AT.minusHours(1)
        );
        jdbc.update(
                "insert into " + prefix + "loan_contract_repayment_items "
                        + "(id,loan_contract_id,"
                        + "source_approved_offer_repayment_item_id,"
                        + "installment_number,principal_due,interest_due,"
                        + "fee_due,total_due) values "
                        + "(?,?,?,1,500,50,0,550),(?,?,?,2,500,50,0,550)",
                firstContractItemId,
                contractId,
                firstOfferItemId,
                secondContractItemId,
                contractId,
                secondOfferItemId
        );
        jdbc.update(
                "insert into " + prefix + "loan_accounts "
                        + "(id,loan_application_id,loan_contract_id,customer_id,"
                        + "account_number,status,approved_principal,"
                        + "approved_term_months,total_interest,fee_amount,"
                        + "total_repayment_amount,activated_at) values "
                        + "(?,?,?,?,'LA-' || upper(replace(?::text,'-','')),"
                        + "'ACTIVE',1000,2,100,0,1100,?)",
                accountId,
                applicationId,
                contractId,
                customerId,
                accountId,
                ACTIVATED_AT
        );
        jdbc.update(
                "insert into " + prefix + "manual_disbursements "
                        + "(id,loan_application_id,loan_contract_id,"
                        + "loan_account_id,request_id,"
                        + "expected_contract_version,"
                        + "external_transfer_reference,disbursed_amount,"
                        + "disbursement_value_date,first_repayment_date,"
                        + "confirmed_by_user_id,confirmed_at) values "
                        + "(?,?,?,?,?,1,?,1000,?,?,?,?)",
                UUID.randomUUID(),
                applicationId,
                contractId,
                accountId,
                UUID.randomUUID(),
                "TRANSFER-" + token,
                ACTIVATED_AT.toLocalDate(),
                FIRST_DUE_DATE,
                ACCOUNTING_USER_ID,
                ACTIVATED_AT
        );
        jdbc.update(
                "insert into " + prefix + "repayment_schedules "
                        + "(id,loan_application_id,loan_contract_id,"
                        + "loan_account_id,schedule_type,version,"
                        + "approved_term_months,approved_principal,"
                        + "total_interest,fee_amount,total_repayment_amount,"
                        + "first_due_date,last_due_date,generated_at) values "
                        + "(?,?,?,?,'FINAL',1,2,1000,100,0,1100,?,?,?)",
                scheduleId,
                applicationId,
                contractId,
                accountId,
                FIRST_DUE_DATE,
                LAST_DUE_DATE,
                ACTIVATED_AT
        );
        jdbc.update(
                "insert into " + prefix + "repayment_schedule_items "
                        + "(id,repayment_schedule_id,"
                        + "source_loan_contract_repayment_item_id,"
                        + "installment_number,due_date,principal_due,"
                        + "interest_due,fee_due,total_due) values "
                        + "(?,?,?,1,?,500,50,0,550),"
                        + "(?,?,?,2,?,500,50,0,550)",
                UUID.randomUUID(),
                scheduleId,
                firstContractItemId,
                FIRST_DUE_DATE,
                UUID.randomUUID(),
                scheduleId,
                secondContractItemId,
                LAST_DUE_DATE
        );
        jdbc.update(
                "insert into " + prefix + "salary_advance_limits "
                        + "(id,customer_id,customer_partner_employee_link_id,"
                        + "total_limit,used_amount,reserved_amount,"
                        + "available_amount,status,last_refreshed_at) values "
                        + "(?,?,?,5000,1000,0,4000,'ACTIVE',?)",
                limitId,
                customerId,
                UUID.randomUUID(),
                ACTIVATED_AT
        );
        jdbc.update(
                "insert into " + prefix + "salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,"
                        + "loan_account_id,movement_type,amount,occurred_at) values "
                        + "(?,?,?,null,'RESERVED',1000,?),"
                        + "(?,?,?,?,'DISBURSED_TO_USED',1000,?)",
                UUID.randomUUID(),
                limitId,
                applicationId,
                ACTIVATED_AT.minusDays(10),
                UUID.randomUUID(),
                limitId,
                applicationId,
                accountId,
                ACTIVATED_AT
        );
            return new Fixture(
                    customerId,
                    applicationId,
                    accountId,
                    scheduleId,
                    limitId
            );
        });
    }

    private void replicationDisabled(String schema, Runnable mutation) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(ignored -> {
            jdbc.execute("set local session_replication_role = replica");
            mutation.run();
            jdbc.execute("set local session_replication_role = origin");
        });
    }

    private List<String> scheduleRows(String schema, Fixture fixture) {
        return jdbc.query(
                "select schedule.id,schedule.schedule_type,schedule.version,"
                        + "schedule.approved_term_months,"
                        + "schedule.approved_principal,"
                        + "schedule.total_interest,schedule.fee_amount,"
                        + "schedule.total_repayment_amount,"
                        + "schedule.first_due_date,schedule.last_due_date,"
                        + "item.id,item.installment_number,item.due_date,"
                        + "item.principal_due,item.interest_due,item.fee_due,"
                        + "item.total_due "
                        + "from " + schema + ".repayment_schedules schedule "
                        + "join " + schema + ".repayment_schedule_items item "
                        + "on item.repayment_schedule_id = schedule.id "
                        + "where schedule.id = ? "
                        + "order by item.installment_number",
                (resultSet, rowNumber) -> resultSet.getString(1)
                        + "|" + resultSet.getString(2)
                        + "|" + resultSet.getString(3)
                        + "|" + resultSet.getString(4)
                        + "|" + resultSet.getString(5)
                        + "|" + resultSet.getString(6)
                        + "|" + resultSet.getString(7)
                        + "|" + resultSet.getString(8)
                        + "|" + resultSet.getString(9)
                        + "|" + resultSet.getString(10)
                        + "|" + resultSet.getString(11)
                        + "|" + resultSet.getString(12)
                        + "|" + resultSet.getString(13)
                        + "|" + resultSet.getString(14)
                        + "|" + resultSet.getString(15)
                        + "|" + resultSet.getString(16)
                        + "|" + resultSet.getString(17),
                fixture.scheduleId()
        );
    }

    private int count(String schema, String tableExpression, String predicate) {
        return jdbc.queryForObject(
                "select count(*) from " + schema + "." + tableExpression
                        + " where " + predicate,
                Integer.class
        );
    }

    private String value(String schema, String sql, Object argument) {
        return jdbc.queryForObject(
                sql.replace(" from ", " from " + schema + "."),
                String.class,
                argument
        );
    }

    private String latestVersion(String schema) {
        return jdbc.queryForObject(
                "select version from " + schema
                        + ".flyway_schema_history where success "
                        + "order by installed_rank desc limit 1",
                String.class
        );
    }

    private void migrateTo(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void dropSchema(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String schemaName(String suffix) {
        return "md_v33_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private record Fixture(
            UUID customerId,
            UUID applicationId,
            UUID accountId,
            UUID scheduleId,
            UUID limitId
    ) {
        private static Fixture empty() {
            return new Fixture(null, null, null, null, null);
        }
    }
}
