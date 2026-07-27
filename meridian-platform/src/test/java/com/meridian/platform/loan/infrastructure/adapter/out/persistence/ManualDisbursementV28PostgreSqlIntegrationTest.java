package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class ManualDisbursementV28PostgreSqlIntegrationTest {

    private static final String SCHEMA = schemaName("installed");
    private static final UUID ACCOUNTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void installsV28WithGenericNumericAndTimestampFoundation() {
        assertEquals(1, count(
                "select count(*) from " + SCHEMA
                        + ".flyway_schema_history where version = '28' and success"
        ));

        for (String table : List.of(
                "loan_accounts",
                "manual_disbursements",
                "repayment_schedules",
                "repayment_schedule_items"
        )) {
            assertNotNull(jdbc.queryForObject(
                    "select to_regclass(?)",
                    String.class,
                    SCHEMA + "." + table
            ));
        }

        List<String> monetaryColumns = jdbc.queryForList("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = ?
                  and table_name in (
                    'loan_accounts',
                    'manual_disbursements',
                    'repayment_schedules',
                    'repayment_schedule_items'
                  )
                  and data_type = 'numeric'
                order by table_name, ordinal_position
                """, String.class, SCHEMA);
        assertFalse(monetaryColumns.isEmpty());
        for (String monetaryColumn : monetaryColumns) {
            String[] parts = monetaryColumn.split("\\.");
            assertEquals(19, jdbc.queryForObject("""
                    select numeric_precision
                    from information_schema.columns
                    where table_schema = ? and table_name = ? and column_name = ?
                    """, Integer.class, SCHEMA, parts[0], parts[1]));
            assertEquals(2, jdbc.queryForObject("""
                    select numeric_scale
                    from information_schema.columns
                    where table_schema = ? and table_name = ? and column_name = ?
                    """, Integer.class, SCHEMA, parts[0], parts[1]));
        }

        assertEquals(0, count("""
                select count(*)
                from information_schema.columns
                where table_schema = ?
                  and table_name in (
                    'loan_accounts',
                    'manual_disbursements',
                    'repayment_schedules',
                    'repayment_schedule_items'
                  )
                  and data_type = 'timestamp with time zone'
                """, SCHEMA));
        assertTrue(count("""
                select count(*)
                from information_schema.columns
                where table_schema = ?
                  and table_name in (
                    'loan_accounts',
                    'manual_disbursements',
                    'repayment_schedules',
                    'repayment_schedule_items'
                  )
                  and data_type = 'timestamp without time zone'
                """, SCHEMA) > 0);

        List<String> genericColumns = jdbc.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = ?
                  and table_name in (
                    'loan_accounts',
                    'manual_disbursements',
                    'repayment_schedules',
                    'repayment_schedule_items'
                  )
                """, String.class, SCHEMA);
        assertFalse(genericColumns.stream().anyMatch(column ->
                column.contains("salary")
                        || column.contains("employee")
                        || column.contains("partner")
                        || column.contains("reservation")));
    }

    @Test
    void upgradesCleanV27SchemaToV28() {
        String schema = schemaName("upgrade");
        try {
            migrateTo(schema, "27");
            migrateLatest(schema);

            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from " + schema
                            + ".flyway_schema_history where version = '28' and success",
                    Integer.class
            ));
            assertNotNull(jdbc.queryForObject(
                    "select to_regclass(?)",
                    String.class,
                    schema + ".loan_accounts"
            ));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void preflightRejectsExistingDisbursedApplication() {
        String schema = schemaName("preflight_disbursed");
        try {
            migrateTo(schema, "27");
            UUID customerId = jdbc.queryForObject(
                    "select id from " + schema + ".customers order by customer_number limit 1",
                    UUID.class
            );
            UUID productId = jdbc.queryForObject(
                    "select id from " + schema
                            + ".loan_products where product_code = 'SALARY_ADVANCE'",
                    UUID.class
            );
            UUID applicationId = UUID.randomUUID();
            jdbc.update(
                    "insert into " + schema + ".loan_applications "
                            + "(id,customer_id,loan_product_id,application_number,product_code,product_type,"
                            + "status,requested_amount,requested_term_months,submitted_at) "
                            + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED','DISBURSED',1000,1,current_timestamp)",
                    applicationId,
                    customerId,
                    productId,
                    "SA-V28-PREFLIGHT-" + applicationId.toString().replace("-", "").substring(0, 20)
            );

            FlywayException failure = assertThrows(FlywayException.class, () -> migrateLatest(schema));

            assertTrue(allMessages(failure).contains(
                    "V28 cannot create activation evidence for an existing DISBURSED Loan Application"
            ));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void preflightRejectsLegacyLoanAccountReferencesAndConversionMovementStates() {
        assertV28PreflightRejects(
                "pf_account_ref",
                schema -> {
                    UUID limitId = insertPreflightLimit(schema, "0", "0", "2000");
                    jdbc.update(
                            "insert into " + schema + ".salary_advance_limit_movements "
                                    + "(id,salary_advance_limit_id,loan_account_id,movement_type,"
                                    + "amount,occurred_at) "
                                    + "values (?,?,?,'INITIALIZED',2000,current_timestamp)",
                            UUID.randomUUID(),
                            limitId,
                            UUID.randomUUID()
                    );
                },
                "V28 cannot attach pre-existing Salary Advance movement LoanAccount references"
        );
        assertV28PreflightRejects(
                "pf_conversion",
                schema -> {
                    UUID limitId = insertPreflightLimit(schema, "0", "0", "2000");
                    jdbc.update(
                            "insert into " + schema + ".salary_advance_limit_movements "
                                    + "(id,salary_advance_limit_id,movement_type,amount,occurred_at) "
                                    + "values (?,?,'DISBURSED_TO_USED',0,current_timestamp)",
                            UUID.randomUUID(),
                            limitId
                    );
                },
                "V28 cannot reconcile pre-existing Salary Advance conversion or repayment-release movements"
        );
        assertV28PreflightRejects(
                "pf_repaid",
                schema -> {
                    UUID limitId = insertPreflightLimit(schema, "0", "0", "2000");
                    jdbc.update(
                            "insert into " + schema + ".salary_advance_limit_movements "
                                    + "(id,salary_advance_limit_id,movement_type,amount,occurred_at) "
                                    + "values (?,?,'REPAID_RELEASED',0,current_timestamp)",
                            UUID.randomUUID(),
                            limitId
                    );
                },
                "V28 cannot reconcile pre-existing Salary Advance conversion or repayment-release movements"
        );
    }

    @Test
    void preflightRejectsUsedAndUnreconciledReservedExposure() {
        assertV28PreflightRejects(
                "pf_used",
                schema -> insertPreflightLimit(schema, "100", "0", "1900"),
                "V28 cannot reconcile pre-existing used Salary Advance exposure"
        );
        assertV28PreflightRejects(
                "pf_reserved",
                schema -> insertPreflightLimit(schema, "0", "100", "1900"),
                "V28 cannot reconcile existing Salary Advance reservation evidence"
        );
    }

    @Test
    void preflightRejectsDisbursementPendingApplicationWithoutOneReadyContract() {
        assertV28PreflightRejects(
                "pf_ready_pair",
                schema -> {
                    UUID customerId = jdbc.queryForObject(
                            "select id from " + schema
                                    + ".customers order by customer_number limit 1",
                            UUID.class
                    );
                    UUID productId = jdbc.queryForObject(
                            "select id from " + schema
                                    + ".loan_products where product_code = 'SALARY_ADVANCE'",
                            UUID.class
                    );
                    UUID applicationId = UUID.randomUUID();
                    jdbc.update(
                            "insert into " + schema + ".loan_applications "
                                    + "(id,customer_id,loan_product_id,application_number,"
                                    + "product_code,product_type,status,requested_amount,"
                                    + "requested_term_months,submitted_at) "
                                    + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED',"
                                    + "'DISBURSEMENT_PENDING',1000,1,current_timestamp)",
                            applicationId,
                            customerId,
                            productId,
                            "SA-V28-READY-" + applicationId.toString()
                                    .replace("-", "")
                                    .substring(0, 20)
                    );
                },
                "V28 requires one ready contract for existing DISBURSEMENT_PENDING Loan Application"
        );
    }

    @Test
    void acceptsCompleteDeferredFoundationAndRejectsPartialOrMismatchedEvidence() {
        Fixture complete = createReadyFixture(false);
        Activation activation = insertCompleteActivation(complete, "TRANSFER-" + complete.token(), false);

        assertEquals(1, count(
                "select count(*) from loan_accounts where id = ?",
                activation.loanAccountId()
        ));
        assertEquals(1, count(
                "select count(*) from manual_disbursements where loan_account_id = ?",
                activation.loanAccountId()
        ));
        assertEquals(2, count(
                "select count(*) from repayment_schedule_items where repayment_schedule_id = ?",
                activation.scheduleId()
        ));
        assertExactActivation(complete, activation, "TRANSFER-" + complete.token());

        Fixture partial = createReadyFixture(false);
        DataAccessException partialFailure = assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(status -> insertLoanAccount(partial, UUID.randomUUID()))
        );
        assertTrue(allMessages(partialFailure).contains("Loan activation foundation evidence is incomplete"));

        Fixture mismatch = createReadyFixture(false);
        DataAccessException mismatchFailure = assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(status -> {
                    UUID accountId = UUID.randomUUID();
                    insertLoanAccount(mismatch, accountId);
                    insertManualDisbursement(
                            mismatch,
                            accountId,
                            UUID.randomUUID(),
                            "TRANSFER-" + mismatch.token(),
                            "999"
                    );
                    insertSchedule(mismatch, accountId, UUID.randomUUID(), false, false);
                })
        );
        assertTrue(allMessages(mismatchFailure).contains(
                "Manual disbursement evidence does not match the Loan Account"
        ));
    }

    @Test
    void rejectsInvalidScheduleShapeMoneyDatesAndSourceCopiesAtCommit() {
        Fixture badSchedule = createReadyFixture(false);
        DataAccessException scheduleFailure = assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(status -> {
                    UUID accountId = UUID.randomUUID();
                    insertLoanAccount(badSchedule, accountId);
                    insertManualDisbursement(
                            badSchedule,
                            accountId,
                            UUID.randomUUID(),
                            "TRANSFER-" + badSchedule.token(),
                            "1000"
                    );
                    insertSchedule(badSchedule, accountId, UUID.randomUUID(), true, false);
                })
        );
        assertTrue(allMessages(scheduleFailure).contains(
                "Final repayment schedule does not reconcile to its source contract"
        ));

        Fixture badDate = createReadyFixture(false);
        DataAccessException dateFailure = assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(status -> {
                    UUID accountId = UUID.randomUUID();
                    insertLoanAccount(badDate, accountId);
                    insertManualDisbursementWithDates(
                            badDate,
                            accountId,
                            UUID.randomUUID(),
                            "TRANSFER-" + badDate.token(),
                            LocalDate.of(2026, 1, 31),
                            LocalDate.of(2026, 3, 1)
                    );
                })
        );
        assertTrue(allMessages(dateFailure).contains("chk_manual_disbursements_repayment_dates"));

        Fixture badType = createReadyFixture(false);
        DataAccessException typeFailure = assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(status -> {
                    UUID accountId = UUID.randomUUID();
                    insertLoanAccount(badType, accountId);
                    insertManualDisbursement(
                            badType,
                            accountId,
                            UUID.randomUUID(),
                            "TRANSFER-" + badType.token(),
                            "1000"
                    );
                    insertSchedule(badType, accountId, UUID.randomUUID(), false, true);
                })
        );
        assertTrue(allMessages(typeFailure).contains("chk_repayment_schedules_type_version"));
    }

    @Test
    void enforcesUniquenessWholeVndAndImmutableEvidence() {
        Fixture first = createReadyFixture(false);
        UUID requestId = UUID.randomUUID();
        String transferReference = "TRANSFER-" + first.token();
        Activation activation = insertCompleteActivation(first, transferReference, false, requestId);

        assertThrows(DataAccessException.class, () ->
                jdbc.update("update loan_accounts set approved_principal = 999 where id = ?",
                        activation.loanAccountId()));
        assertThrows(DataAccessException.class, () ->
                jdbc.update("delete from manual_disbursements where loan_account_id = ?",
                        activation.loanAccountId()));
        assertThrows(DataAccessException.class, () ->
                jdbc.update("update repayment_schedule_items set principal_due = 499 "
                                + "where repayment_schedule_id = ? and installment_number = 1",
                        activation.scheduleId()));

        Fixture duplicateRequest = createReadyFixture(false);
        DataAccessException requestFailure = assertThrows(DataAccessException.class, () ->
                insertCompleteActivation(
                        duplicateRequest,
                        "TRANSFER-" + duplicateRequest.token(),
                        false,
                        requestId
                )
        );
        assertTrue(allMessages(requestFailure).contains("uq_manual_disbursements_request"));

        Fixture duplicateReference = createReadyFixture(false);
        DataAccessException referenceFailure = assertThrows(DataAccessException.class, () ->
                insertCompleteActivation(
                        duplicateReference,
                        transferReference,
                        false
                )
        );
        assertTrue(allMessages(referenceFailure).contains(
                "uq_manual_disbursements_transfer_reference"
        ));

        Fixture fractional = createReadyFixture(false);
        DataAccessException fractionalFailure = assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(status -> insertLoanAccount(
                        fractional,
                        UUID.randomUUID(),
                        "1000.50"
                ))
        );
        assertTrue(allMessages(fractionalFailure).contains("chk_loan_accounts_terms"));
    }

    @Test
    void permitsOnlyLoanAccountStatusAndUpdatedAtMutation() {
        Fixture fixture = createReadyFixture(false);
        Activation activation = insertCompleteActivation(
                fixture,
                "TRANSFER-" + fixture.token(),
                false
        );

        assertEquals(1, jdbc.update(
                "update loan_accounts "
                        + "set status = 'OVERDUE', updated_at = updated_at + interval '1 second' "
                        + "where id = ?",
                activation.loanAccountId()
        ));
        assertEquals("OVERDUE", jdbc.queryForObject(
                "select status from loan_accounts where id = ?",
                String.class,
                activation.loanAccountId()
        ));

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update loan_accounts set status = 'SETTLED', approved_principal = 999 where id = ?",
                activation.loanAccountId()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update loan_accounts set total_interest = 99 where id = ?",
                activation.loanAccountId()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update loan_accounts set customer_id = ? where id = ?",
                UUID.randomUUID(),
                activation.loanAccountId()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "delete from loan_accounts where id = ?",
                activation.loanAccountId()
        ));
        assertEquals(amount("1000"), jdbc.queryForObject(
                "select approved_principal from loan_accounts where id = ?",
                BigDecimal.class,
                activation.loanAccountId()
        ));
    }

    @Test
    void enforcesOneReconciledDisbursedToUsedMovementWithoutStatusGate() {
        Fixture fixture = createReadyFixture(true);
        Activation activation = insertCompleteActivation(
                fixture,
                "TRANSFER-" + fixture.token(),
                false
        );
        UUID movementId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            jdbc.update(
                    "update salary_advance_limits "
                            + "set used_amount = 1000, reserved_amount = 0, available_amount = 1000 "
                            + "where id = ?",
                    fixture.limitId()
            );
            jdbc.update(
                    "insert into salary_advance_limit_movements "
                            + "(id,salary_advance_limit_id,loan_application_id,loan_account_id,"
                            + "movement_type,amount,occurred_at) "
                            + "values (?,?,?,?, 'DISBURSED_TO_USED',1000,current_timestamp)",
                    movementId,
                    fixture.limitId(),
                    fixture.applicationId(),
                    activation.loanAccountId()
            );
        });

        assertEquals("SUSPENDED", jdbc.queryForObject(
                "select status from salary_advance_limits where id = ?",
                String.class,
                fixture.limitId()
        ));
        assertEquals("1000.00", jdbc.queryForObject(
                "select used_amount::text from salary_advance_limits where id = ?",
                String.class,
                fixture.limitId()
        ));
        assertEquals("0.00", jdbc.queryForObject(
                "select reserved_amount::text from salary_advance_limits where id = ?",
                String.class,
                fixture.limitId()
        ));
        assertEquals("1000.00", jdbc.queryForObject(
                "select available_amount::text from salary_advance_limits where id = ?",
                String.class,
                fixture.limitId()
        ));
        Map<String, Object> movement = jdbc.queryForMap(
                "select salary_advance_limit_id,loan_application_id,loan_account_id,amount "
                        + "from salary_advance_limit_movements where id = ?",
                movementId
        );
        assertEquals(fixture.limitId(), movement.get("salary_advance_limit_id"));
        assertEquals(fixture.applicationId(), movement.get("loan_application_id"));
        assertEquals(activation.loanAccountId(), movement.get("loan_account_id"));
        assertEquals(amount("1000"), movement.get("amount"));

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,loan_account_id,"
                        + "movement_type,amount,occurred_at) "
                        + "values (?,?,?,?, 'DISBURSED_TO_USED',1000,current_timestamp)",
                UUID.randomUUID(),
                fixture.limitId(),
                fixture.applicationId(),
                activation.loanAccountId()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update salary_advance_limit_movements "
                        + "set movement_type = 'MANUAL_ADJUSTMENT' where id = ?",
                movementId
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update salary_advance_limit_movements set amount = 999 where id = ?",
                movementId
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "delete from salary_advance_limit_movements where id = ?",
                movementId
        ));
    }

    @Test
    void rejectsMissingWrongAndMutatedSalaryAdvanceConversionReferences() {
        Fixture fixture = createReadyFixture(true);
        Activation activation = insertCompleteActivation(
                fixture,
                "TRANSFER-" + fixture.token(),
                false
        );
        Fixture other = createReadyFixture(false);
        Activation otherActivation = insertCompleteActivation(
                other,
                "TRANSFER-" + other.token(),
                false
        );

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,loan_account_id,"
                        + "movement_type,amount,occurred_at) "
                        + "values (?,?,null,?,'DISBURSED_TO_USED',1000,current_timestamp)",
                UUID.randomUUID(),
                fixture.limitId(),
                activation.loanAccountId()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,loan_account_id,"
                        + "movement_type,amount,occurred_at) "
                        + "values (?,?,?,null,'DISBURSED_TO_USED',1000,current_timestamp)",
                UUID.randomUUID(),
                fixture.limitId(),
                fixture.applicationId()
        ));

        assertConversionRejected(
                fixture,
                other.applicationId(),
                activation.loanAccountId()
        );
        assertConversionRejected(
                fixture,
                fixture.applicationId(),
                otherActivation.loanAccountId()
        );

        UUID reservedMovementId = jdbc.queryForObject(
                "select id from salary_advance_limit_movements "
                        + "where salary_advance_limit_id = ? and movement_type = 'RESERVED'",
                UUID.class,
                fixture.limitId()
        );
        DataAccessException convertedHistoryFailure = assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "update salary_advance_limit_movements "
                                + "set movement_type = 'DISBURSED_TO_USED', loan_account_id = ? "
                                + "where id = ?",
                        activation.loanAccountId(),
                        reservedMovementId
                )
        );
        assertTrue(allMessages(convertedHistoryFailure).contains(
                "must be inserted as new evidence"
        ));
    }

    private void assertConversionRejected(
            Fixture fixture,
            UUID applicationId,
            UUID accountId
    ) {
        assertThrows(
                DataAccessException.class,
                () -> transactions.executeWithoutResult(status -> {
                    jdbc.update(
                            "update salary_advance_limits "
                                    + "set used_amount = 1000, reserved_amount = 0, "
                                    + "available_amount = 1000 where id = ?",
                            fixture.limitId()
                    );
                    jdbc.update(
                            "insert into salary_advance_limit_movements "
                                    + "(id,salary_advance_limit_id,loan_application_id,"
                                    + "loan_account_id,movement_type,amount,occurred_at) "
                                    + "values (?,?,?,?,'DISBURSED_TO_USED',1000,current_timestamp)",
                            UUID.randomUUID(),
                            fixture.limitId(),
                            applicationId,
                            accountId
                    );
                })
        );
    }

    @Test
    void linksFinalScheduleFirstDateToManualDisbursementAtCommit() {
        Fixture matching = createReadyFixture(false);
        insertCompleteActivation(matching, "TRANSFER-" + matching.token(), false);

        Fixture mismatched = createReadyFixture(false);
        ScheduleSpec mismatch = scheduleWithDates(
                mismatched,
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 9, 28)
        );
        DataAccessException mismatchFailure = assertScheduleRejected(mismatched, mismatch);
        assertTrue(allMessages(mismatchFailure).contains(
                "Final repayment schedule header does not match the Loan Account"
        ));

        Fixture onValueDate = createReadyFixture(false);
        ScheduleSpec notAfterValueDate = scheduleWithDates(
                onValueDate,
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27)
        );
        DataAccessException dateFailure = assertScheduleRejected(onValueDate, notAfterValueDate);
        assertTrue(allMessages(dateFailure).contains(
                "Final repayment schedule header does not match the Loan Account"
        ));
    }

    @Test
    void rejectsInvalidScheduleCountSequenceSourcesMoneyBoundariesAndDatesAtCommit() {
        Fixture missingFixture = createReadyFixture(false);
        ScheduleSpec missingBase = validSchedule(missingFixture);
        DataAccessException missingFailure = assertScheduleRejected(
                missingFixture,
                new ScheduleSpec(
                        missingBase.scheduleType(),
                        missingBase.version(),
                        missingBase.approvedTermMonths(),
                        missingBase.approvedPrincipal(),
                        missingBase.totalInterest(),
                        missingBase.feeAmount(),
                        missingBase.totalRepaymentAmount(),
                        missingBase.firstDueDate(),
                        missingBase.lastDueDate(),
                        List.of(missingBase.items().getFirst())
                )
        );
        assertTrue(allMessages(missingFailure).contains(
                "Final repayment schedule does not reconcile to its source contract"
        ));

        Fixture skippedFixture = createReadyFixture(false);
        ScheduleSpec skippedBase = validSchedule(skippedFixture);
        ScheduleItemSpec skippedSecond = new ScheduleItemSpec(
                skippedBase.items().get(1).sourceContractItemId(),
                3,
                skippedBase.items().get(1).dueDate(),
                amount("500"),
                amount("50"),
                amount("0"),
                amount("550")
        );
        DataAccessException skippedFailure = assertScheduleRejected(
                skippedFixture,
                new ScheduleSpec(
                        skippedBase.scheduleType(),
                        skippedBase.version(),
                        skippedBase.approvedTermMonths(),
                        skippedBase.approvedPrincipal(),
                        skippedBase.totalInterest(),
                        skippedBase.feeAmount(),
                        skippedBase.totalRepaymentAmount(),
                        skippedBase.firstDueDate(),
                        skippedBase.lastDueDate(),
                        List.of(skippedBase.items().getFirst(), skippedSecond)
                )
        );
        assertTrue(allMessages(skippedFailure).contains(
                "Final repayment schedule does not reconcile to its source contract"
        ));

        Fixture duplicateFixture = createReadyFixture(false);
        ScheduleSpec duplicateBase = validSchedule(duplicateFixture);
        ScheduleItemSpec duplicateSecond = new ScheduleItemSpec(
                duplicateBase.items().get(1).sourceContractItemId(),
                1,
                duplicateBase.items().get(1).dueDate(),
                amount("500"),
                amount("50"),
                amount("0"),
                amount("550")
        );
        DataAccessException duplicateFailure = assertScheduleRejected(
                duplicateFixture,
                new ScheduleSpec(
                        duplicateBase.scheduleType(),
                        duplicateBase.version(),
                        duplicateBase.approvedTermMonths(),
                        duplicateBase.approvedPrincipal(),
                        duplicateBase.totalInterest(),
                        duplicateBase.feeAmount(),
                        duplicateBase.totalRepaymentAmount(),
                        duplicateBase.firstDueDate(),
                        duplicateBase.lastDueDate(),
                        List.of(duplicateBase.items().getFirst(), duplicateSecond)
                )
        );
        assertTrue(allMessages(duplicateFailure).contains(
                "uq_repayment_schedule_items_schedule_installment"
        ));

        Fixture sourceFixture = createReadyFixture(false);
        Fixture otherSource = createReadyFixture(false);
        ScheduleSpec sourceBase = validSchedule(sourceFixture);
        ScheduleItemSpec foreignSource = new ScheduleItemSpec(
                otherSource.firstContractItemId(),
                1,
                sourceBase.items().getFirst().dueDate(),
                amount("500"),
                amount("50"),
                amount("0"),
                amount("550")
        );
        DataAccessException sourceFailure = assertScheduleRejected(
                sourceFixture,
                new ScheduleSpec(
                        sourceBase.scheduleType(),
                        sourceBase.version(),
                        sourceBase.approvedTermMonths(),
                        sourceBase.approvedPrincipal(),
                        sourceBase.totalInterest(),
                        sourceBase.feeAmount(),
                        sourceBase.totalRepaymentAmount(),
                        sourceBase.firstDueDate(),
                        sourceBase.lastDueDate(),
                        List.of(foreignSource, sourceBase.items().get(1))
                )
        );
        assertTrue(allMessages(sourceFailure).contains(
                "Final repayment schedule does not reconcile to its source contract"
        ));

        Fixture headerFixture = createReadyFixture(false);
        ScheduleSpec headerBase = validSchedule(headerFixture);
        DataAccessException headerFailure = assertScheduleRejected(
                headerFixture,
                new ScheduleSpec(
                        headerBase.scheduleType(),
                        headerBase.version(),
                        headerBase.approvedTermMonths(),
                        amount("1001"),
                        headerBase.totalInterest(),
                        headerBase.feeAmount(),
                        amount("1101"),
                        headerBase.firstDueDate(),
                        headerBase.lastDueDate(),
                        headerBase.items()
                )
        );
        assertTrue(allMessages(headerFailure).contains("Final repayment schedule"));

        Fixture itemFixture = createReadyFixture(false);
        ScheduleSpec itemBase = validSchedule(itemFixture);
        ScheduleItemSpec badItem = new ScheduleItemSpec(
                itemBase.items().getFirst().sourceContractItemId(),
                1,
                itemBase.items().getFirst().dueDate(),
                amount("499"),
                amount("50"),
                amount("0"),
                amount("549")
        );
        DataAccessException itemFailure = assertScheduleRejected(
                itemFixture,
                new ScheduleSpec(
                        itemBase.scheduleType(),
                        itemBase.version(),
                        itemBase.approvedTermMonths(),
                        itemBase.approvedPrincipal(),
                        itemBase.totalInterest(),
                        itemBase.feeAmount(),
                        itemBase.totalRepaymentAmount(),
                        itemBase.firstDueDate(),
                        itemBase.lastDueDate(),
                        List.of(badItem, itemBase.items().get(1))
                )
        );
        assertTrue(allMessages(itemFailure).contains(
                "Final repayment schedule does not reconcile to its source contract"
        ));

        Fixture boundaryFixture = createReadyFixture(false);
        ScheduleSpec boundaryBase = validSchedule(boundaryFixture);
        DataAccessException boundaryFailure = assertScheduleRejected(
                boundaryFixture,
                new ScheduleSpec(
                        boundaryBase.scheduleType(),
                        boundaryBase.version(),
                        boundaryBase.approvedTermMonths(),
                        boundaryBase.approvedPrincipal(),
                        boundaryBase.totalInterest(),
                        boundaryBase.feeAmount(),
                        boundaryBase.totalRepaymentAmount(),
                        LocalDate.of(2026, 8, 28),
                        boundaryBase.lastDueDate(),
                        boundaryBase.items()
                )
        );
        assertTrue(allMessages(boundaryFailure).contains("Final repayment schedule"));

        Fixture datesFixture = createReadyFixture(false);
        ScheduleSpec datesBase = validSchedule(datesFixture);
        ScheduleItemSpec nonIncreasingSecond = new ScheduleItemSpec(
                datesBase.items().get(1).sourceContractItemId(),
                2,
                datesBase.items().getFirst().dueDate(),
                amount("500"),
                amount("50"),
                amount("0"),
                amount("550")
        );
        DataAccessException datesFailure = assertScheduleRejected(
                datesFixture,
                new ScheduleSpec(
                        datesBase.scheduleType(),
                        datesBase.version(),
                        datesBase.approvedTermMonths(),
                        datesBase.approvedPrincipal(),
                        datesBase.totalInterest(),
                        datesBase.feeAmount(),
                        datesBase.totalRepaymentAmount(),
                        datesBase.firstDueDate(),
                        datesBase.firstDueDate(),
                        List.of(datesBase.items().getFirst(), nonIncreasingSecond)
                )
        );
        assertTrue(allMessages(datesFailure).contains(
                "Final repayment schedule does not reconcile to its source contract"
        ));
    }

    @Test
    void rejectsInvalidApplicationContractCustomerAndAccountOwnershipTuples() {
        Fixture first = createReadyFixture(false);
        Fixture second = createReadyFixture(false);

        DataAccessException applicationContractFailure = assertThrows(
                DataAccessException.class,
                () -> insertLoanAccountTuple(
                        first.applicationId(),
                        second.contractId(),
                        first.customerId(),
                        UUID.randomUUID()
                )
        );
        assertTrue(allMessages(applicationContractFailure).contains(
                "fk_loan_accounts_contract_application_customer"
        ));

        DataAccessException contractCustomerFailure = assertThrows(
                DataAccessException.class,
                () -> insertLoanAccountTuple(
                        first.applicationId(),
                        first.contractId(),
                        second.customerId(),
                        UUID.randomUUID()
                )
        );
        assertTrue(allMessages(contractCustomerFailure).contains(
                "fk_loan_accounts_application_customer"
        ));

        DataAccessException manualAccountFailure = assertThrows(
                DataAccessException.class,
                () -> transactions.executeWithoutResult(status -> {
                    UUID firstAccount = UUID.randomUUID();
                    UUID secondAccount = UUID.randomUUID();
                    insertLoanAccount(first, firstAccount);
                    insertLoanAccount(second, secondAccount);
                    insertManualDisbursement(
                            first,
                            secondAccount,
                            UUID.randomUUID(),
                            "TRANSFER-" + first.token(),
                            "1000"
                    );
                })
        );
        assertTrue(allMessages(manualAccountFailure).contains(
                "fk_manual_disbursements_account_application_contract"
        ));

        DataAccessException scheduleAccountFailure = assertThrows(
                DataAccessException.class,
                () -> transactions.executeWithoutResult(status -> {
                    UUID firstAccount = UUID.randomUUID();
                    UUID secondAccount = UUID.randomUUID();
                    insertLoanAccount(first, firstAccount);
                    insertLoanAccount(second, secondAccount);
                    insertSchedule(first, secondAccount, UUID.randomUUID(), false, false);
                })
        );
        assertTrue(allMessages(scheduleAccountFailure).contains(
                "fk_repayment_schedules_account_application_contract"
        ));
    }

    @Test
    void enforcesEveryActivationEvidenceUniquenessBoundary() {
        for (String constraint : List.of(
                "uq_loan_accounts_application",
                "uq_loan_accounts_contract",
                "uq_manual_disbursements_application",
                "uq_manual_disbursements_contract",
                "uq_manual_disbursements_account",
                "uq_manual_disbursements_request",
                "uq_manual_disbursements_transfer_reference",
                "uq_repayment_schedules_application",
                "uq_repayment_schedules_contract",
                "uq_repayment_schedules_account"
        )) {
            assertEquals(1, count(
                    "select count(*) from pg_constraint "
                            + "where connamespace = ?::regnamespace and conname = ?",
                    SCHEMA,
                    constraint
            ));
        }

        Fixture fixture = createReadyFixture(false);
        Activation activation = insertCompleteActivation(
                fixture,
                "TRANSFER-" + fixture.token(),
                false
        );

        DataAccessException accountFailure = assertThrows(
                DataAccessException.class,
                () -> insertLoanAccount(fixture, UUID.randomUUID())
        );
        assertTrue(allMessages(accountFailure).contains("uq_loan_accounts_application"));

        DataAccessException disbursementFailure = assertThrows(
                DataAccessException.class,
                () -> insertManualDisbursement(
                        fixture,
                        activation.loanAccountId(),
                        UUID.randomUUID(),
                        "SECOND-" + fixture.token(),
                        "1000"
                )
        );
        assertTrue(allMessages(disbursementFailure).contains(
                "uq_manual_disbursements_application"
        ));

        DataAccessException scheduleFailure = assertThrows(
                DataAccessException.class,
                () -> insertSchedule(
                        fixture,
                        activation.loanAccountId(),
                        UUID.randomUUID(),
                        false,
                        false
                )
        );
        assertTrue(allMessages(scheduleFailure).contains(
                "uq_repayment_schedules_application"
        ));

        Fixture requestFirst = createReadyFixture(false);
        Fixture requestSecond = createReadyFixture(false);
        UUID repeatedRequestId = UUID.randomUUID();
        DataAccessException requestFailure = assertThrows(
                DataAccessException.class,
                () -> transactions.executeWithoutResult(status -> {
                    insertCompleteActivation(
                            requestFirst,
                            "REQUEST-FIRST-" + requestFirst.token(),
                            false,
                            repeatedRequestId
                    );
                    insertCompleteActivation(
                            requestSecond,
                            "REQUEST-SECOND-" + requestSecond.token(),
                            false,
                            repeatedRequestId
                    );
                })
        );
        assertTrue(allMessages(requestFailure).contains(
                "uq_manual_disbursements_request"
        ));

        Fixture referenceFirst = createReadyFixture(false);
        Fixture referenceSecond = createReadyFixture(false);
        String repeatedReference = "DUPLICATE-" + referenceFirst.token();
        DataAccessException referenceFailure = assertThrows(
                DataAccessException.class,
                () -> transactions.executeWithoutResult(status -> {
                    insertCompleteActivation(referenceFirst, repeatedReference, false);
                    insertCompleteActivation(referenceSecond, repeatedReference, false);
                })
        );
        assertTrue(allMessages(referenceFailure).contains(
                "uq_manual_disbursements_transfer_reference"
        ));
    }

    private void assertExactActivation(
            Fixture fixture,
            Activation activation,
            String transferReference
    ) {
        Map<String, Object> account = jdbc.queryForMap(
                "select * from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        assertEquals(fixture.applicationId(), account.get("loan_application_id"));
        assertEquals(fixture.contractId(), account.get("loan_contract_id"));
        assertEquals(fixture.customerId(), account.get("customer_id"));
        assertEquals(
                "LA-" + activation.loanAccountId().toString().replace("-", "").toUpperCase(),
                account.get("account_number")
        );
        assertEquals("ACTIVE", account.get("status"));
        assertEquals(amount("1000"), account.get("approved_principal"));
        assertEquals(2, account.get("approved_term_months"));
        assertEquals(amount("100"), account.get("total_interest"));
        assertEquals(amount("0"), account.get("fee_amount"));
        assertEquals(amount("1100"), account.get("total_repayment_amount"));

        Map<String, Object> disbursement = jdbc.queryForMap(
                "select * from manual_disbursements where loan_account_id = ?",
                activation.loanAccountId()
        );
        assertEquals(fixture.applicationId(), disbursement.get("loan_application_id"));
        assertEquals(fixture.contractId(), disbursement.get("loan_contract_id"));
        assertEquals(activation.loanAccountId(), disbursement.get("loan_account_id"));
        assertEquals(activation.requestId(), disbursement.get("request_id"));
        assertEquals(1, disbursement.get("expected_contract_version"));
        assertEquals(transferReference, disbursement.get("external_transfer_reference"));
        assertEquals(amount("1000"), disbursement.get("disbursed_amount"));
        assertEquals(
                LocalDate.of(2026, 7, 27),
                ((java.sql.Date) disbursement.get("disbursement_value_date")).toLocalDate()
        );
        assertEquals(
                LocalDate.of(2026, 8, 27),
                ((java.sql.Date) disbursement.get("first_repayment_date")).toLocalDate()
        );
        assertEquals(ACCOUNTING_USER_ID, disbursement.get("confirmed_by_user_id"));

        Map<String, Object> schedule = jdbc.queryForMap(
                "select * from repayment_schedules where id = ?",
                activation.scheduleId()
        );
        assertEquals(fixture.applicationId(), schedule.get("loan_application_id"));
        assertEquals(fixture.contractId(), schedule.get("loan_contract_id"));
        assertEquals(activation.loanAccountId(), schedule.get("loan_account_id"));
        assertEquals("FINAL", schedule.get("schedule_type"));
        assertEquals(1, schedule.get("version"));
        assertEquals(2, schedule.get("approved_term_months"));
        assertEquals(amount("1000"), schedule.get("approved_principal"));
        assertEquals(amount("100"), schedule.get("total_interest"));
        assertEquals(amount("0"), schedule.get("fee_amount"));
        assertEquals(amount("1100"), schedule.get("total_repayment_amount"));
        assertEquals(
                LocalDate.of(2026, 8, 27),
                ((java.sql.Date) schedule.get("first_due_date")).toLocalDate()
        );
        assertEquals(
                LocalDate.of(2026, 9, 27),
                ((java.sql.Date) schedule.get("last_due_date")).toLocalDate()
        );

        List<Map<String, Object>> items = jdbc.queryForList(
                "select source_loan_contract_repayment_item_id,installment_number,due_date,"
                        + "principal_due,interest_due,fee_due,total_due "
                        + "from repayment_schedule_items where repayment_schedule_id = ? "
                        + "order by installment_number",
                activation.scheduleId()
        );
        assertEquals(2, items.size());
        assertExactItem(
                items.get(0),
                fixture.firstContractItemId(),
                1,
                LocalDate.of(2026, 8, 27)
        );
        assertExactItem(
                items.get(1),
                fixture.secondContractItemId(),
                2,
                LocalDate.of(2026, 9, 27)
        );
    }

    private void assertExactItem(
            Map<String, Object> item,
            UUID sourceId,
            int installmentNumber,
            LocalDate dueDate
    ) {
        assertEquals(sourceId, item.get("source_loan_contract_repayment_item_id"));
        assertEquals(installmentNumber, item.get("installment_number"));
        assertEquals(
                dueDate,
                ((java.sql.Date) item.get("due_date")).toLocalDate()
        );
        assertEquals(amount("500"), item.get("principal_due"));
        assertEquals(amount("50"), item.get("interest_due"));
        assertEquals(amount("0"), item.get("fee_due"));
        assertEquals(amount("550"), item.get("total_due"));
    }

    private DataAccessException assertScheduleRejected(
            Fixture fixture,
            ScheduleSpec schedule
    ) {
        return assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(status -> {
                    UUID accountId = UUID.randomUUID();
                    insertLoanAccount(fixture, accountId);
                    insertManualDisbursement(
                            fixture,
                            accountId,
                            UUID.randomUUID(),
                            "TRANSFER-" + fixture.token(),
                            "1000"
                    );
                    insertSchedule(fixture, accountId, UUID.randomUUID(), schedule);
                })
        );
    }

    private ScheduleSpec scheduleWithDates(
            Fixture fixture,
            LocalDate firstItemDate,
            LocalDate secondItemDate,
            LocalDate firstHeaderDate,
            LocalDate lastHeaderDate
    ) {
        ScheduleSpec base = validSchedule(fixture);
        return new ScheduleSpec(
                base.scheduleType(),
                base.version(),
                base.approvedTermMonths(),
                base.approvedPrincipal(),
                base.totalInterest(),
                base.feeAmount(),
                base.totalRepaymentAmount(),
                firstHeaderDate,
                lastHeaderDate,
                List.of(
                        new ScheduleItemSpec(
                                base.items().getFirst().sourceContractItemId(),
                                1,
                                firstItemDate,
                                amount("500"), amount("50"), amount("0"), amount("550")
                        ),
                        new ScheduleItemSpec(
                                base.items().get(1).sourceContractItemId(),
                                2,
                                secondItemDate,
                                amount("500"), amount("50"), amount("0"), amount("550")
                        )
                )
        );
    }

    private Fixture createReadyFixture(boolean withSuspendedReservation) {
        return transactions.execute(status -> {
            UUID customerId = UUID.randomUUID();
            UUID customerUserId = UUID.randomUUID();
            UUID bankAccountId = UUID.randomUUID();
            UUID applicationId = UUID.randomUUID();
            UUID approvedOfferId = UUID.randomUUID();
            UUID firstContractItemId = UUID.randomUUID();
            UUID secondContractItemId = UUID.randomUUID();
            UUID contractId = UUID.randomUUID();
            UUID limitId = withSuspendedReservation ? UUID.randomUUID() : null;
            String token = customerId.toString().replace("-", "").substring(0, 12).toUpperCase();

            jdbc.update(
                    "insert into customers "
                            + "(id,customer_number,status,verification_status,profile_completion_status) "
                            + "values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')",
                    customerId,
                    "CUS-V28-" + token
            );
            jdbc.update(
                    "insert into users "
                            + "(id,email,normalized_email,password_hash,user_type,status,display_name,customer_id) "
                            + "values (?,?,?,'not-used','CUSTOMER','ACTIVE','V28 Customer',?)",
                    customerUserId,
                    "v28-" + token + "@meridian.test",
                    "v28-" + token + "@meridian.test",
                    customerId
            );
            jdbc.update(
                    "insert into customer_bank_accounts "
                            + "(id,customer_id,bank_code,bank_name_snapshot,account_holder_name,"
                            + "account_number_ciphertext,account_number_fingerprint,account_number_last_four,"
                            + "status,primary_account) "
                            + "values (?,?,'VCB','Vietcombank','MERIDIAN CUSTOMER',?,?, '7890','ACTIVE',true)",
                    bankAccountId,
                    customerId,
                    "ciphertext-" + token,
                    "fingerprint-" + token
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
                            + "(id,customer_id,loan_product_id,application_number,product_code,product_type,"
                            + "status,requested_amount,requested_term_months,submitted_at) "
                            + "values (?,?,?,?,'SALARY_ADVANCE','SALARY_BASED','DISBURSEMENT_PENDING',"
                            + "1000,2,current_timestamp)",
                    applicationId,
                    customerId,
                    productId,
                    "SA-V28-" + token
            );
            jdbc.update(
                    "insert into approved_offers "
                            + "(id,loan_application_id,source_loan_product_policy_id,status,approved_principal,"
                            + "approved_term_months,interest_calculation_method,flat_monthly_interest_rate,"
                            + "total_interest,fee_amount,total_repayment_amount,repayment_method,"
                            + "generated_at,expires_at,accepted_at) "
                            + "values (?,?,?,'ACCEPTED',1000,2,'FLAT_ORIGINAL_PRINCIPAL',0.05,"
                            + "100,0,1100,'ON_SALARY_DATE',current_timestamp-interval '2 days',"
                            + "current_timestamp-interval '1 day',current_timestamp-interval '1 day')",
                    approvedOfferId,
                    applicationId,
                    policyId
            );
            UUID firstOfferItemId = UUID.randomUUID();
            UUID secondOfferItemId = UUID.randomUUID();
            jdbc.update(
                    "insert into approved_offer_repayment_items "
                            + "(id,approved_offer_id,installment_number,principal_due,interest_due,fee_due,total_due) "
                            + "values (?,?,1,500,50,0,550),(?,?,2,500,50,0,550)",
                    firstOfferItemId,
                    approvedOfferId,
                    secondOfferItemId,
                    approvedOfferId
            );
            jdbc.update(
                    "insert into loan_contracts "
                            + "(id,loan_application_id,approved_offer_id,contract_reference,contract_version,status,"
                            + "approved_principal,approved_term_months,interest_calculation_method,"
                            + "flat_monthly_interest_rate,total_interest,fee_amount,total_repayment_amount,"
                            + "repayment_method,customer_id,source_bank_account_id,bank_code,bank_name_snapshot,"
                            + "account_holder_name,account_number_last_four,primary_at_capture,active_at_capture,"
                            + "account_captured_at,protection_scheme,protection_key_id,protection_nonce,"
                            + "protected_account_number,protection_aad_version,preparation_request_id,"
                            + "prepared_by_user_id,prepared_at,acknowledgment_request_id,acknowledged_by_user_id,"
                            + "acknowledged_at,confirmation_request_id,confirmed_by_user_id,confirmed_at) "
                            + "values (?,?,?, ?,1,'READY_FOR_DISBURSEMENT',1000,2,"
                            + "'FLAT_ORIGINAL_PRINCIPAL',0.05,100,0,1100,'ON_SALARY_DATE',?,?,"
                            + "'VCB','Vietcombank','MERIDIAN CUSTOMER','7890',true,true,"
                            + "current_timestamp-interval '2 hours','AES-256-GCM','v1',"
                            + "decode('000000000000000000000000','hex'),decode('01','hex'),"
                            + "'DISBURSEMENT_ACCOUNT_V1',?,?,current_timestamp-interval '2 hours',"
                            + "?,?,current_timestamp-interval '90 minutes',?,?,current_timestamp-interval '1 hour')",
                    contractId,
                    applicationId,
                    approvedOfferId,
                    "MCT-V28-" + token,
                    customerId,
                    bankAccountId,
                    UUID.randomUUID(),
                    ACCOUNTING_USER_ID,
                    UUID.randomUUID(),
                    customerUserId,
                    UUID.randomUUID(),
                    ACCOUNTING_USER_ID
            );
            jdbc.update(
                    "insert into loan_contract_repayment_items "
                            + "(id,loan_contract_id,source_approved_offer_repayment_item_id,"
                            + "installment_number,principal_due,interest_due,fee_due,total_due) "
                            + "values (?,?,?,1,500,50,0,550),(?,?,?,2,500,50,0,550)",
                    firstContractItemId,
                    contractId,
                    firstOfferItemId,
                    secondContractItemId,
                    contractId,
                    secondOfferItemId
            );

            if (withSuspendedReservation) {
                jdbc.update(
                        "insert into salary_advance_limits "
                                + "(id,customer_id,customer_partner_employee_link_id,total_limit,used_amount,"
                                + "reserved_amount,available_amount,status,last_refreshed_at) "
                                + "values (?,?,?,2000,0,1000,1000,'SUSPENDED',current_timestamp)",
                        limitId,
                        customerId,
                        UUID.randomUUID()
                );
                jdbc.update(
                        "insert into salary_advance_limit_movements "
                                + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                                + "values (?,?,?,'RESERVED',1000,current_timestamp)",
                        UUID.randomUUID(),
                        limitId,
                        applicationId
                );
            }

            return new Fixture(
                    customerId,
                    applicationId,
                    contractId,
                    firstContractItemId,
                    secondContractItemId,
                    limitId,
                    token
            );
        });
    }

    private Activation insertCompleteActivation(
            Fixture fixture,
            String transferReference,
            boolean sourceMismatch
    ) {
        return insertCompleteActivation(
                fixture,
                transferReference,
                sourceMismatch,
                UUID.randomUUID()
        );
    }

    private Activation insertCompleteActivation(
            Fixture fixture,
            String transferReference,
            boolean sourceMismatch,
            UUID requestId
    ) {
        return transactions.execute(status -> {
            UUID accountId = UUID.randomUUID();
            UUID scheduleId = UUID.randomUUID();
            insertLoanAccount(fixture, accountId);
            insertManualDisbursement(
                    fixture,
                    accountId,
                    requestId,
                    transferReference,
                    "1000"
            );
            insertSchedule(fixture, accountId, scheduleId, sourceMismatch, false);
            return new Activation(accountId, scheduleId, requestId);
        });
    }

    private void insertLoanAccountTuple(
            UUID applicationId,
            UUID contractId,
            UUID customerId,
            UUID accountId
    ) {
        jdbc.update(
                "insert into loan_accounts "
                        + "(id,loan_application_id,loan_contract_id,customer_id,account_number,status,"
                        + "approved_principal,approved_term_months,total_interest,fee_amount,"
                        + "total_repayment_amount,activated_at) "
                        + "values (?,?,?,?,'LA-' || upper(replace(?::text,'-','')),'ACTIVE',"
                        + "1000,2,100,0,1100,current_timestamp)",
                accountId,
                applicationId,
                contractId,
                customerId,
                accountId
        );
    }

    private void insertLoanAccount(Fixture fixture, UUID accountId) {
        insertLoanAccount(fixture, accountId, "1000");
    }

    private void insertLoanAccount(Fixture fixture, UUID accountId, String principal) {
        jdbc.update(
                "insert into loan_accounts "
                        + "(id,loan_application_id,loan_contract_id,customer_id,account_number,status,"
                        + "approved_principal,approved_term_months,total_interest,fee_amount,"
                        + "total_repayment_amount,activated_at) "
                        + "values (?,?,?,?,'LA-' || upper(replace(?::text,'-','')),'ACTIVE',"
                        + principal + ",2,100,0," + ("1000".equals(principal) ? "1100" : "1100.50")
                        + ",current_timestamp)",
                accountId,
                fixture.applicationId(),
                fixture.contractId(),
                fixture.customerId(),
                accountId
        );
    }

    private void insertManualDisbursement(
            Fixture fixture,
            UUID accountId,
            UUID requestId,
            String transferReference,
            String amount
    ) {
        insertManualDisbursementWithDates(
                fixture,
                accountId,
                requestId,
                transferReference,
                amount,
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27)
        );
    }

    private void insertManualDisbursementWithDates(
            Fixture fixture,
            UUID accountId,
            UUID requestId,
            String transferReference,
            LocalDate valueDate,
            LocalDate firstRepaymentDate
    ) {
        insertManualDisbursementWithDates(
                fixture,
                accountId,
                requestId,
                transferReference,
                "1000",
                valueDate,
                firstRepaymentDate
        );
    }

    private void insertManualDisbursementWithDates(
            Fixture fixture,
            UUID accountId,
            UUID requestId,
            String transferReference,
            String amount,
            LocalDate valueDate,
            LocalDate firstRepaymentDate
    ) {
        jdbc.update(
                "insert into manual_disbursements "
                        + "(id,loan_application_id,loan_contract_id,loan_account_id,request_id,"
                        + "expected_contract_version,external_transfer_reference,disbursed_amount,"
                        + "disbursement_value_date,first_repayment_date,confirmed_by_user_id,confirmed_at) "
                        + "values (?,?,?,?,?,1,?," + amount + ",?,?,?,current_timestamp)",
                UUID.randomUUID(),
                fixture.applicationId(),
                fixture.contractId(),
                accountId,
                requestId,
                transferReference,
                valueDate,
                firstRepaymentDate,
                ACCOUNTING_USER_ID
        );
    }

    private void insertSchedule(
            Fixture fixture,
            UUID accountId,
            UUID scheduleId,
            boolean sourceMismatch,
            boolean invalidType
    ) {
        ScheduleSpec valid = validSchedule(fixture);
        List<ScheduleItemSpec> items = sourceMismatch
                ? List.of(
                        new ScheduleItemSpec(
                                fixture.firstContractItemId(),
                                1,
                                LocalDate.of(2026, 8, 27),
                                amount("499"),
                                amount("50"),
                                amount("0"),
                                amount("549")
                        ),
                        new ScheduleItemSpec(
                                fixture.secondContractItemId(),
                                2,
                                LocalDate.of(2026, 9, 27),
                                amount("501"),
                                amount("50"),
                                amount("0"),
                                amount("551")
                        )
                )
                : valid.items();
        insertSchedule(
                fixture,
                accountId,
                scheduleId,
                new ScheduleSpec(
                        invalidType ? "PROVISIONAL" : "FINAL",
                        invalidType ? 2 : 1,
                        valid.approvedTermMonths(),
                        valid.approvedPrincipal(),
                        valid.totalInterest(),
                        valid.feeAmount(),
                        valid.totalRepaymentAmount(),
                        valid.firstDueDate(),
                        valid.lastDueDate(),
                        items
                )
        );
    }

    private void insertSchedule(
            Fixture fixture,
            UUID accountId,
            UUID scheduleId,
            ScheduleSpec spec
    ) {
        jdbc.update(
                "insert into repayment_schedules "
                        + "(id,loan_application_id,loan_contract_id,loan_account_id,schedule_type,version,"
                        + "approved_term_months,approved_principal,total_interest,fee_amount,"
                        + "total_repayment_amount,first_due_date,last_due_date,generated_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,current_timestamp)",
                scheduleId,
                fixture.applicationId(),
                fixture.contractId(),
                accountId,
                spec.scheduleType(),
                spec.version(),
                spec.approvedTermMonths(),
                spec.approvedPrincipal(),
                spec.totalInterest(),
                spec.feeAmount(),
                spec.totalRepaymentAmount(),
                spec.firstDueDate(),
                spec.lastDueDate()
        );
        for (ScheduleItemSpec item : spec.items()) {
            jdbc.update(
                    "insert into repayment_schedule_items "
                            + "(id,repayment_schedule_id,source_loan_contract_repayment_item_id,"
                            + "installment_number,due_date,principal_due,interest_due,fee_due,total_due) "
                            + "values (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    scheduleId,
                    item.sourceContractItemId(),
                    item.installmentNumber(),
                    item.dueDate(),
                    item.principalDue(),
                    item.interestDue(),
                    item.feeDue(),
                    item.totalDue()
            );
        }
    }

    private ScheduleSpec validSchedule(Fixture fixture) {
        return new ScheduleSpec(
                "FINAL",
                1,
                2,
                amount("1000"),
                amount("100"),
                amount("0"),
                amount("1100"),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 9, 27),
                List.of(
                        new ScheduleItemSpec(
                                fixture.firstContractItemId(),
                                1,
                                LocalDate.of(2026, 8, 27),
                                amount("500"),
                                amount("50"),
                                amount("0"),
                                amount("550")
                        ),
                        new ScheduleItemSpec(
                                fixture.secondContractItemId(),
                                2,
                                LocalDate.of(2026, 9, 27),
                                amount("500"),
                                amount("50"),
                                amount("0"),
                                amount("550")
                        )
                )
        );
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
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

    private void assertV28PreflightRejects(
            String suffix,
            Consumer<String> v27Setup,
            String expectedMessage
    ) {
        String schema = schemaName(suffix);
        try {
            migrateTo(schema, "27");
            v27Setup.accept(schema);

            FlywayException failure = assertThrows(
                    FlywayException.class,
                    () -> migrateLatest(schema)
            );
            assertTrue(allMessages(failure).contains(expectedMessage));
        } finally {
            dropSchema(schema);
        }
    }

    private UUID insertPreflightLimit(
            String schema,
            String used,
            String reserved,
            String available
    ) {
        UUID customerId = jdbc.queryForObject(
                "select id from " + schema + ".customers order by customer_number limit 1",
                UUID.class
        );
        UUID limitId = UUID.randomUUID();
        jdbc.update(
                "insert into " + schema + ".salary_advance_limits "
                        + "(id,customer_id,customer_partner_employee_link_id,total_limit,"
                        + "used_amount,reserved_amount,available_amount,status,last_refreshed_at) "
                        + "values (?,?,?,2000," + used + "," + reserved + "," + available
                        + ",'ACTIVE',current_timestamp)",
                limitId,
                customerId,
                UUID.randomUUID()
        );
        return limitId;
    }

    private void migrateLatest(String schema) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void dropSchema(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private static String allMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }

    private static String schemaName(String suffix) {
        return "md_v28_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private record ScheduleSpec(
            String scheduleType,
            int version,
            int approvedTermMonths,
            BigDecimal approvedPrincipal,
            BigDecimal totalInterest,
            BigDecimal feeAmount,
            BigDecimal totalRepaymentAmount,
            LocalDate firstDueDate,
            LocalDate lastDueDate,
            List<ScheduleItemSpec> items
    ) {
    }

    private record ScheduleItemSpec(
            UUID sourceContractItemId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal feeDue,
            BigDecimal totalDue
    ) {
    }

    private record Fixture(
            UUID customerId,
            UUID applicationId,
            UUID contractId,
            UUID firstContractItemId,
            UUID secondContractItemId,
            UUID limitId,
            String token
    ) {
    }

    private record Activation(
            UUID loanAccountId,
            UUID scheduleId,
            UUID requestId
    ) {
    }
}
