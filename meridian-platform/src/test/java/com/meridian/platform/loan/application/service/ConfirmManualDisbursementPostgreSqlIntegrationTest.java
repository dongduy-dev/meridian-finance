package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.FIRST_REPAYMENT_DATE;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.NOW;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.VALUE_DATE;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.assertMoney;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.moneyValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(ConfirmManualDisbursementPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class ConfirmManualDisbursementPostgreSqlIntegrationTest {

    private static final String SCHEMA = "md_i3_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired ConfirmContractReadinessUseCase readiness;
    @Autowired ManualDisbursementRepository manualDisbursementRepository;
    @Autowired LoanApplicationRepository loanApplicationRepository;
    @Autowired SalaryAdvanceLimitRepository salaryAdvanceLimits;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider currentUserProvider;

    private TransactionTemplate transactions;
    private ManualDisbursementActivationPostgreSqlTestSupport support;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        support = new ManualDisbursementActivationPostgreSqlTestSupport(
                jdbc, transactionManager);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting.officer@meridian.test",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse")
        ));
    }

    @Test
    void completeSalaryAdvanceActivationCommitsExactEvidenceHistoryAndAudit() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var command = support.command(
                fixture, UUID.randomUUID(), " transfer-" + fixture.token().toLowerCase() + " ");

        ConfirmManualDisbursementUseCase.Result result = disbursements.confirm(command);

        assertFalse(result.idempotentReplay());
        assertEquals(fixture.applicationId(), result.loanApplicationId());
        assertEquals(LoanApplicationStatus.DISBURSED, result.applicationStatus());
        assertEquals("ACTIVE", support.value(
                "select status from loan_accounts where id = ?", result.loanAccountId()));
        assertEquals(fixture.applicationId(), support.uuid(
                "select loan_application_id from loan_accounts where id = ?", result.loanAccountId()));
        assertEquals(fixture.contractId(), support.uuid(
                "select loan_contract_id from loan_accounts where id = ?", result.loanAccountId()));
        assertEquals(fixture.customerId(), support.uuid(
                "select customer_id from loan_accounts where id = ?", result.loanAccountId()));
        assertEquals(result.loanAccountNumber(), support.value(
                "select account_number from loan_accounts where id = ?", result.loanAccountId()));
        assertMoney("1000", support.money(
                "select approved_principal from loan_accounts where id = ?", result.loanAccountId()));
        assertEquals(2, support.integer(
                "select approved_term_months from loan_accounts where id = ?", result.loanAccountId()));
        assertMoney("100", support.money(
                "select total_interest from loan_accounts where id = ?", result.loanAccountId()));
        assertMoney("0", support.money(
                "select fee_amount from loan_accounts where id = ?", result.loanAccountId()));
        assertMoney("1100", support.money(
                "select total_repayment_amount from loan_accounts where id = ?", result.loanAccountId()));
        assertEquals(NOW, support.dateTime(
                "select activated_at from loan_accounts where id = ?", result.loanAccountId()));

        assertEquals(command.requestId(), support.uuid(
                "select request_id from manual_disbursements where id = ?", result.manualDisbursementId()));
        assertEquals("TRANSFER-" + fixture.token(), support.value(
                "select external_transfer_reference from manual_disbursements where id = ?",
                result.manualDisbursementId()));
        assertEquals(1, support.integer(
                "select expected_contract_version from manual_disbursements where id = ?",
                result.manualDisbursementId()));
        assertMoney("1000", support.money(
                "select disbursed_amount from manual_disbursements where id = ?",
                result.manualDisbursementId()));
        assertEquals(VALUE_DATE, support.date(
                "select disbursement_value_date from manual_disbursements where id = ?",
                result.manualDisbursementId()));
        assertEquals(FIRST_REPAYMENT_DATE, support.date(
                "select first_repayment_date from manual_disbursements where id = ?",
                result.manualDisbursementId()));
        assertEquals(ACCOUNTING_USER_ID, support.uuid(
                "select confirmed_by_user_id from manual_disbursements where id = ?",
                result.manualDisbursementId()));

        assertEquals("FINAL", support.value(
                "select schedule_type from repayment_schedules where id = ?", result.repaymentScheduleId()));
        assertEquals(1, support.integer(
                "select version from repayment_schedules where id = ?", result.repaymentScheduleId()));
        assertEquals(FIRST_REPAYMENT_DATE, support.date(
                "select first_due_date from repayment_schedules where id = ?", result.repaymentScheduleId()));
        assertEquals(LocalDate.of(2026, 9, 28), support.date(
                "select last_due_date from repayment_schedules where id = ?", result.repaymentScheduleId()));
        assertEquals(2, result.scheduleItems().size());
        for (int index = 0; index < result.scheduleItems().size(); index++) {
            var item = result.scheduleItems().get(index);
            assertEquals(index + 1, item.installmentNumber());
            assertEquals(fixture.contractItemIds().get(index), item.sourceLoanContractRepaymentItemId());
            assertMoney("500", item.principalDue());
            assertMoney("50", item.interestDue());
            assertMoney("0", item.feeDue());
            assertMoney("550", item.totalDue());
            assertEquals(item.dueDate(), support.date(
                    "select due_date from repayment_schedule_items where id = ?", item.id()));
        }

        assertEquals("DISBURSED", support.value(
                "select status from loan_applications where id = ?", fixture.applicationId()));
        assertEquals(1, support.count(
                "select count(*) from loan_application_status_transitions where loan_application_id = ? "
                        + "and action = 'CONFIRM_MANUAL_DISBURSEMENT'", fixture.applicationId()));
        assertEquals("DISBURSEMENT_PENDING", support.value(
                "select from_status from loan_application_status_transitions "
                        + "where loan_application_id = ? and action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                fixture.applicationId()));
        assertEquals("DISBURSED", support.value(
                "select to_status from loan_application_status_transitions "
                        + "where loan_application_id = ? and action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                fixture.applicationId()));

        assertEquals(1, support.count(
                "select count(*) from audit_events where entity_id = ? "
                        + "and action = 'MANUAL_DISBURSEMENT_CONFIRMED'", fixture.applicationId()));
        String payload = support.value(
                "select payload::text from audit_events where entity_id = ? "
                        + "and action = 'MANUAL_DISBURSEMENT_CONFIRMED'", fixture.applicationId());
        assertTrue(payload.contains(result.loanAccountId().toString()));
        assertTrue(payload.contains(result.manualDisbursementId().toString()));
        assertTrue(payload.contains(result.repaymentScheduleId().toString()));
        assertTrue(payload.contains("SALARY_ADVANCE"));
        assertFalse(payload.contains("TRANSFER-" + fixture.token()));
        assertFalse(payload.contains("7890"));
        assertEquals("LOAN_APPLICATION", support.value(
                "select entity_type from audit_events where entity_id = ? "
                        + "and action = 'MANUAL_DISBURSEMENT_CONFIRMED'", fixture.applicationId()));

        assertMoney("1000", support.money(
                "select used_amount from salary_advance_limits where id = ?", fixture.limitId()));
        assertMoney("0", support.money(
                "select reserved_amount from salary_advance_limits where id = ?", fixture.limitId()));
        assertMoney("4000", support.money(
                "select available_amount from salary_advance_limits where id = ?", fixture.limitId()));
        assertEquals(1, support.count(
                "select count(*) from salary_advance_limit_movements where loan_application_id = ? "
                        + "and movement_type = 'DISBURSED_TO_USED'", fixture.applicationId()));
        assertEquals(result.loanAccountId(), support.uuid(
                "select loan_account_id from salary_advance_limit_movements where loan_application_id = ? "
                        + "and movement_type = 'DISBURSED_TO_USED'", fixture.applicationId()));
        assertMoney("1000", support.money(
                "select amount from salary_advance_limit_movements where loan_application_id = ? "
                        + "and movement_type = 'DISBURSED_TO_USED'", fixture.applicationId()));
    }

    @Test
    void identicalReplayReturnsSameResultWithoutSecondEffect() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var command = support.command(fixture, UUID.randomUUID(), "REPLAY-" + fixture.token());
        var first = disbursements.confirm(command);
        var before = support.counts(fixture.applicationId());

        var replay = disbursements.confirm(new ConfirmManualDisbursementUseCase.Command(
                command.requestId(), fixture.applicationId(), 1,
                " replay-" + fixture.token().toLowerCase() + " ",
                VALUE_DATE, FIRST_REPAYMENT_DATE));

        assertTrue(replay.idempotentReplay());
        assertEquals(first.loanAccountId(), replay.loanAccountId());
        assertEquals(first.manualDisbursementId(), replay.manualDisbursementId());
        assertEquals(first.repaymentScheduleId(), replay.repaymentScheduleId());
        assertEquals(first.scheduleItems(), replay.scheduleItems());
        assertEquals(before, support.counts(fixture.applicationId()));
    }

    @Test
    void reusedRequestCompletedApplicationDuplicateReferenceAndStaleVersionAreDeterministic() {
        var firstFixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        String sharedReference = "SHARED-" + firstFixture.token();
        var firstCommand = support.command(firstFixture, UUID.randomUUID(), sharedReference);
        disbursements.confirm(firstCommand);

        var reused = assertThrows(BusinessStateConflictException.class, () ->
                disbursements.confirm(new ConfirmManualDisbursementUseCase.Command(
                        firstCommand.requestId(), firstFixture.applicationId(), 1,
                        sharedReference, VALUE_DATE, FIRST_REPAYMENT_DATE.minusDays(1))));
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.getErrorCode());
        assertFalse(reused.getMessage().contains(sharedReference));

        var completed = assertThrows(BusinessStateConflictException.class, () ->
                disbursements.confirm(support.command(
                        firstFixture, UUID.randomUUID(), "OTHER-" + firstFixture.token())));
        assertEquals("DISBURSEMENT_ALREADY_COMPLETED", completed.getErrorCode());

        var secondFixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var duplicate = assertThrows(BusinessStateConflictException.class, () ->
                disbursements.confirm(support.command(
                        secondFixture, UUID.randomUUID(), sharedReference)));
        assertEquals("DUPLICATE_TRANSFER_REFERENCE", duplicate.getErrorCode());
        assertFalse(duplicate.getMessage().contains(sharedReference));
        support.assertNoActivation(secondFixture);

        var staleFixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var stale = assertThrows(BusinessStateConflictException.class, () ->
                disbursements.confirm(new ConfirmManualDisbursementUseCase.Command(
                        UUID.randomUUID(), staleFixture.applicationId(), 2,
                        "STALE-" + staleFixture.token(), VALUE_DATE, FIRST_REPAYMENT_DATE)));
        assertEquals("CONTRACT_VERSION_STALE", stale.getErrorCode());
        support.assertNoActivation(staleFixture);
    }

    @Test
    void unsupportedProductAndReleasedReservationRollbackEveryGenericWrite() {
        var unsupported = support.createFixture(true, ProductCode.UNSECURED_CONSUMER_LOAN);
        var unsupportedFailure = assertThrows(BusinessStateConflictException.class, () ->
                disbursements.confirm(support.command(
                        unsupported, UUID.randomUUID(), "UCL-" + unsupported.token())));
        assertEquals("PRODUCT_ACTIVATION_NOT_SUPPORTED", unsupportedFailure.getErrorCode());
        support.assertNoActivation(unsupported);

        var released = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        transactions.executeWithoutResult(status -> {
            jdbc.update("insert into salary_advance_limit_movements "
                            + "(id,salary_advance_limit_id,loan_application_id,movement_type,amount,occurred_at) "
                            + "values (?,?,?,'RESERVATION_RELEASED',1000,?)",
                    UUID.randomUUID(), released.limitId(), released.applicationId(), NOW.minusMinutes(1));
            jdbc.update("update salary_advance_limits set reserved_amount = 0, available_amount = 5000 "
                    + "where id = ?", released.limitId());
        });
        var releasedFailure = assertThrows(BusinessStateConflictException.class, () ->
                disbursements.confirm(support.command(
                        released, UUID.randomUUID(), "RELEASED-" + released.token())));
        assertEquals("SALARY_ADVANCE_RESERVATION_RELEASED", releasedFailure.getErrorCode());
        support.assertNoGenericActivation(released);
    }

    @Test
    void immediatePersistenceHistoryAndAuditFailuresRollbackEverything() {
        assertRollbackWithRejectingTrigger("loan_accounts", "before insert",
                "raise exception 'test account failure'", "ACCOUNT");
        assertRollbackWithRejectingTrigger("repayment_schedules", "before insert",
                "raise exception 'test schedule failure'", "SCHEDULE");
        assertRollbackWithRejectingTrigger("loan_applications", "before update",
                "if new.status = 'DISBURSED' then raise exception 'test application failure'; end if",
                "APPLICATION");
        assertRollbackWithRejectingTrigger("loan_application_status_transitions", "before insert",
                "if new.action = 'CONFIRM_MANUAL_DISBURSEMENT' then raise exception 'test history failure'; end if",
                "HISTORY");
        assertRollbackWithRejectingTrigger("audit_events", "before insert",
                "if new.action = 'MANUAL_DISBURSEMENT_CONFIRMED' then raise exception 'test audit failure'; end if",
                "AUDIT");
    }

    @Test
    void deferredScheduleFailureAtCommitRollsBackAllEffects() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        String suffix = fixture.token().toLowerCase();
        String function = "mutate_schedule_" + suffix;
        String trigger = "trg_mutate_schedule_" + suffix;
        jdbc.execute("create function " + function + "() returns trigger as $$ begin "
                + "new.first_due_date := new.first_due_date + 1; return new; end; $$ language plpgsql");
        jdbc.execute("create trigger " + trigger
                + " before insert on repayment_schedules for each row execute function "
                + function + "()");
        try {
            assertThrows(RuntimeException.class, () -> disbursements.confirm(support.command(
                    fixture, UUID.randomUUID(), "DEFERRED-" + fixture.token())));
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on repayment_schedules");
            jdbc.execute("drop function if exists " + function + "()");
        }
        support.assertNoActivation(fixture);
    }
    @Test
    void concurrentSameRequestProducesOneActivationAndOneReplay() throws Exception {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var command = support.command(
                fixture, UUID.randomUUID(), "RACE-SAME-" + fixture.token());
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> holdRequestLock(
                    command.requestId(), lockHeld, releaseLock)));
            assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            Future<ConfirmManualDisbursementUseCase.Result> first =
                    executor.submit(() -> disbursements.confirm(command));
            Future<ConfirmManualDisbursementUseCase.Result> second =
                    executor.submit(() -> disbursements.confirm(command));
            futures.add(first);
            futures.add(second);
            awaitAdvisoryWaiters(2);
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            releaseLock.countDown();

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertEquals(firstResult.loanAccountId(), secondResult.loanAccountId());
            assertNotEquals(firstResult.idempotentReplay(), secondResult.idempotentReplay());
            assertEquals(new ManualDisbursementActivationPostgreSqlTestSupport.Counts(
                    1, 1, 1, 2, 1, 1, 1), support.counts(fixture.applicationId()));
        } finally {
            releaseLock.countDown();
            cleanupExecutor(executor, futures);
        }
    }

    @Test
    void concurrentDifferentRequestsProduceOneSuccessAndOneCompletedConflict() throws Exception {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var firstCommand = support.command(
                fixture, UUID.randomUUID(), "RACE-A-" + fixture.token());
        var secondCommand = support.command(
                fixture, UUID.randomUUID(), "RACE-B-" + fixture.token());
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> holdWorkflowLock(
                    fixture.applicationId(), lockHeld, releaseLock)));
            assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            Future<Object> first = executor.submit(() -> capture(() ->
                    disbursements.confirm(firstCommand)));
            Future<Object> second = executor.submit(() -> capture(() ->
                    disbursements.confirm(secondCommand)));
            futures.add(first);
            futures.add(second);
            awaitAdvisoryWaiters(2);
            releaseLock.countDown();

            List<Object> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertEquals(1, results.stream()
                    .filter(ConfirmManualDisbursementUseCase.Result.class::isInstance)
                    .count());
            var losingFailure = assertInstanceOf(
                    BusinessStateConflictException.class,
                    results.stream().filter(BusinessStateConflictException.class::isInstance)
                            .findFirst().orElseThrow()
            );
            assertEquals("DISBURSEMENT_ALREADY_COMPLETED", losingFailure.getErrorCode());
            assertEquals(new ManualDisbursementActivationPostgreSqlTestSupport.Counts(
                    1, 1, 1, 2, 1, 1, 1), support.counts(fixture.applicationId()));
        } finally {
            releaseLock.countDown();
            cleanupExecutor(executor, futures);
        }
    }

    @Test
    void readinessConfirmationCommitsBeforeWaitingDisbursement() throws Exception {
        var fixture = support.createFixture(false, ProductCode.SALARY_ADVANCE);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> holdWorkflowLock(
                    fixture.applicationId(), lockHeld, releaseLock)));
            assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            Future<?> readinessFuture = executor.submit(() -> readiness.confirm(
                    new ConfirmContractReadinessUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), fixture.contractId(), 1
                    )
            ));
            futures.add(readinessFuture);
            awaitAdvisoryWaiters(1);
            Future<ConfirmManualDisbursementUseCase.Result> disbursementFuture =
                    executor.submit(() -> disbursements.confirm(support.command(
                            fixture, UUID.randomUUID(), "READY-RACE-" + fixture.token())));
            futures.add(disbursementFuture);
            awaitAdvisoryWaiters(2);
            releaseLock.countDown();

            readinessFuture.get(10, TimeUnit.SECONDS);
            var result = disbursementFuture.get(10, TimeUnit.SECONDS);
            assertEquals(LoanApplicationStatus.DISBURSED, result.applicationStatus());
            assertEquals(1, support.count(
                    "select count(*) from loan_application_status_transitions "
                            + "where loan_application_id = ? "
                            + "and action = 'CONFIRM_DISBURSEMENT_READINESS'",
                    fixture.applicationId()));
            assertEquals(1, support.count(
                    "select count(*) from loan_application_status_transitions "
                            + "where loan_application_id = ? "
                            + "and action = 'CONFIRM_MANUAL_DISBURSEMENT'",
                    fixture.applicationId()));
        } finally {
            releaseLock.countDown();
            cleanupExecutor(executor, futures);
        }
    }

    @Test
    void limitRefreshLockSerializesBeforeExposureConversion() throws Exception {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> transactions.executeWithoutResult(status -> {
                salaryAdvanceLimits.acquireCustomerLinkLock(
                        fixture.customerId(), fixture.linkId());
                SalaryAdvanceLimit locked = salaryAdvanceLimits
                        .findByIdForUpdate(fixture.limitId()).orElseThrow();
                lockHeld.countDown();
                awaitLatch(releaseLock);
                salaryAdvanceLimits.save(new SalaryAdvanceLimit(
                        locked.id(), locked.customerId(), locked.customerPartnerEmployeeLinkId(),
                        moneyValue("6000"), locked.usedAmount(), locked.reservedAmount(),
                        moneyValue("5000"), SalaryAdvanceLimitStatus.ACTIVE, NOW
                ));
            })));
            assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            Future<ConfirmManualDisbursementUseCase.Result> activation = executor.submit(
                    () -> disbursements.confirm(support.command(
                            fixture, UUID.randomUUID(), "LIMIT-RACE-" + fixture.token())));
            futures.add(activation);
            awaitAdvisoryWaiters(1);
            assertFalse(activation.isDone());
            releaseLock.countDown();

            activation.get(10, TimeUnit.SECONDS);
            assertMoney("1000", support.money(
                    "select used_amount from salary_advance_limits where id = ?", fixture.limitId()));
            assertMoney("0", support.money(
                    "select reserved_amount from salary_advance_limits where id = ?", fixture.limitId()));
            assertMoney("5000", support.money(
                    "select available_amount from salary_advance_limits where id = ?", fixture.limitId()));
        } finally {
            releaseLock.countDown();
            cleanupExecutor(executor, futures);
        }
    }

    private void assertRollbackWithRejectingTrigger(
            String table,
            String timing,
            String body,
            String referencePrefix
    ) {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        String suffix = fixture.token().toLowerCase();
        String function = "reject_" + table + "_" + suffix;
        String trigger = "trg_reject_" + table + "_" + suffix;
        jdbc.execute("create function " + function + "() returns trigger as $$ begin "
                + body + "; return new; end; $$ language plpgsql");
        jdbc.execute("create trigger " + trigger + " " + timing + " on " + table
                + " for each row execute function " + function + "()");
        try {
            assertThrows(RuntimeException.class, () -> disbursements.confirm(support.command(
                    fixture, UUID.randomUUID(), referencePrefix + "-" + fixture.token())));
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on " + table);
            jdbc.execute("drop function if exists " + function + "()");
        }
        support.assertNoActivation(fixture);
    }

    private void holdRequestLock(UUID requestId, CountDownLatch held, CountDownLatch release) {
        transactions.executeWithoutResult(status -> {
            manualDisbursementRepository.acquireConfirmationRequestLock(requestId);
            held.countDown();
            awaitLatch(release);
        });
    }

    private void holdWorkflowLock(UUID applicationId, CountDownLatch held, CountDownLatch release) {
        transactions.executeWithoutResult(status -> {
            loanApplicationRepository.acquireWorkflowLock(applicationId);
            held.countDown();
            awaitLatch(release);
        });
    }

    private void awaitAdvisoryWaiters(int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiters = jdbc.queryForObject(
                    "select count(*) from pg_locks where locktype = 'advisory' and not granted",
                    Integer.class
            );
            if (waiters != null && waiters >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Expected bounded advisory-lock overlap was not observed.");
    }

    private static Object capture(ThrowingSupplier operation) {
        try {
            return operation.get();
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private static void cleanupExecutor(
            ExecutorService executor,
            List<Future<?>> futures
    ) throws Exception {
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Bounded test coordination timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test coordination was interrupted.", exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock incrementThreeClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-28T10:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
