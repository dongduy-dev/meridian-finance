package com.meridian.platform.loan.application.service;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcomeRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest(classes = {
        MeridianPlatformApplication.class,
        CloseLoanAccountPostgreSqlIntegrationTest.ClosureTestConfiguration.class
}, properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class CloseLoanAccountPostgreSqlIntegrationTest {

    private static final String SCHEMA = "account_closure_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDate OPERATION_DATE = LocalDate.of(2026, 9, 2);
    private static final LocalDateTime EVALUATED_AT =
            LocalDateTime.of(2026, 9, 2, 0, 5);

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired ApproveLoanSettlementUseCase settlements;
    @Autowired CloseLoanAccountUseCase closures;
    @Autowired EvaluateLoanAccountOverdueUseCase overdueEvaluator;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ThreadLocalCurrentUser currentUser;
    @Autowired MutableClock testClock;
    @MockitoSpyBean RepaymentInstallmentProgressRepository progressRepository;
    @MockitoSpyBean SalaryAdvanceLimitMovementRepository movements;
    @MockitoSpyBean RepaymentOperationOutcomeRepository outcomes;

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
        reset(target(progressRepository), target(movements), target(outcomes));
        testClock.set(Instant.parse("2026-09-02T10:00:00Z"));
        support = new ManualDisbursementActivationPostgreSqlTestSupport(
                jdbc,
                transactionManager
        );
        currentUser.accounting();
    }

    @Test
    void closesContractualPayoffWithoutFinancialMutationAndReplaysExactly() {
        Activated activated = activate("PAYOFF");
        UUID sharedOperationId = UUID.randomUUID();
        RecordRepaymentUseCase.Result payoff = repayments.record(
                new RecordRepaymentUseCase.Command(
                        sharedOperationId,
                        activated.applicationId(),
                        "CLOSURE-PAYOFF-" + activated.token(),
                        outstanding(activated.accountId()),
                        OPERATION_DATE
                )
        );
        assertEquals(LoanAccountStatus.SETTLED, payoff.accountBalance().status());
        FinancialFingerprint before = fingerprint(activated);
        CloseLoanAccountUseCase.Command command = new CloseLoanAccountUseCase.Command(
                sharedOperationId,
                activated.applicationId()
        );

        CloseLoanAccountUseCase.Result first = closures.close(command);
        CloseLoanAccountUseCase.Result replay = closures.close(command);

        assertFalse(first.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(LoanAccountStatus.CLOSED, first.resultingStatus());
        assertEquals(first.closedAt(), replay.closedAt());
        assertEquals(before, fingerprint(activated));
        assertClosureEvidence(activated, 1);
        assertEquals(0, count(
                "select count(*) from approved_loan_settlements "
                        + "where loan_account_id=?",
                activated.accountId()
        ));
        BusinessStateConflictException repaymentRejected = assertThrows(
                BusinessStateConflictException.class,
                () -> repayments.record(new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(),
                        activated.applicationId(),
                        "AFTER-CLOSURE-REPAYMENT-" + activated.token(),
                        BigDecimal.ONE,
                        OPERATION_DATE
                ))
        );
        assertEquals("REPAYMENT_NOT_ALLOWED", repaymentRejected.getErrorCode());
    }

    @Test
    void closesApprovedSettlementAndPreservesPaymentEvidence() {
        Activated activated = activate("SETTLEMENT");
        ApproveLoanSettlementUseCase.Command settlementCommand =
                settlementCommand(activated);
        currentUser.approver();
        ApproveLoanSettlementUseCase.Result settlement = settlements.approve(
                settlementCommand
        );
        assertEquals(LoanAccountStatus.SETTLED, settlement.accountBalance().status());
        FinancialFingerprint before = fingerprint(activated);
        currentUser.accounting();

        CloseLoanAccountUseCase.Result result = closures.close(
                new CloseLoanAccountUseCase.Command(
                        settlementCommand.requestId(),
                        activated.applicationId()
                )
        );

        assertEquals(LoanAccountStatus.CLOSED, result.resultingStatus());
        assertEquals(before, fingerprint(activated));
        assertClosureEvidence(activated, 1);
        assertEquals(1, count(
                "select count(*) from approved_loan_settlements "
                        + "where repayment_transaction_id=?",
                settlement.repaymentTransactionId()
        ));
        currentUser.approver();
        ApproveLoanSettlementUseCase.Result replayAfterClosure =
                settlements.approve(settlementCommand);
        assertTrue(replayAfterClosure.idempotentReplay());
        assertEquals(settlement.repaymentTransactionId(),
                replayAfterClosure.repaymentTransactionId());
        BusinessStateConflictException settlementRejected = assertThrows(
                BusinessStateConflictException.class,
                () -> settlements.approve(new ApproveLoanSettlementUseCase.Command(
                        UUID.randomUUID(),
                        activated.applicationId(),
                        settlement.settlementAmount(),
                        OPERATION_DATE,
                        "AFTER-CLOSURE-SETTLEMENT-" + activated.token()
                ))
        );
        assertEquals("SETTLEMENT_NOT_ALLOWED", settlementRejected.getErrorCode());
    }

    @Test
    void rejectsOpenStatesWrongRoleAndCustomer() {
        Activated active = activate("ACTIVE");
        assertClosureNotAllowed(active);

        overdueEvaluator.evaluate(new EvaluateLoanAccountOverdueUseCase.Command(
                active.applicationId(),
                active.accountId(),
                OPERATION_DATE,
                EVALUATED_AT
        ));
        assertEquals("OVERDUE", status(active.accountId()));
        assertClosureNotAllowed(active);

        currentUser.approverWithClosurePermission();
        AuthorizationException wrongRole = assertThrows(
                AuthorizationException.class,
                () -> closures.close(closureCommand(active))
        );
        assertEquals("ACCOUNTING_OFFICER_ROLE_REQUIRED", wrongRole.getErrorCode());

        currentUser.customer(UUID.randomUUID(), UUID.randomUUID());
        AuthorizationException customer = assertThrows(
                AuthorizationException.class,
                () -> closures.close(closureCommand(active))
        );
        assertEquals("ACCOUNTING_OFFICER_ROLE_REQUIRED", customer.getErrorCode());
    }

    @Test
    void preservesTheExistingMissingApplicationContract() {
        EntityNotFoundException missing = assertThrows(
                EntityNotFoundException.class,
                () -> closures.close(new CloseLoanAccountUseCase.Command(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                ))
        );

        assertEquals("LOAN_APPLICATION_NOT_FOUND", missing.getErrorCode());
    }

    @Test
    void rejectsIncompleteProgressExposureAndPayoffProvenanceSafely() {
        Activated progressConflict = activate("PROGRESS");
        repayFully(progressConflict);
        doReturn(List.of()).when(target(progressRepository))
                .findByRepaymentScheduleId(any());
        assertStateConflict(progressConflict);
        reset(target(progressRepository));

        Activated exposureConflict = activate("EXPOSURE");
        repayFully(exposureConflict);
        doReturn(List.of()).when(target(movements))
                .findByLoanApplicationIdAndMovementTypeForUpdate(any(), any());
        assertStateConflict(exposureConflict);
        reset(target(movements));

        Activated provenanceConflict = activate("PROVENANCE");
        repayFully(provenanceConflict);
        doReturn(Optional.empty()).when(target(outcomes))
                .findByRepaymentTransactionId(any());
        assertStateConflict(provenanceConflict);
        reset(target(outcomes));
    }

    @Test
    void enforcesClosureLogicalIdentityAndRejectsNewRequestAfterClosure() {
        Activated activated = activate("IDEMPOTENCY");
        repayFully(activated);
        UUID requestId = UUID.randomUUID();
        CloseLoanAccountUseCase.Command command = new CloseLoanAccountUseCase.Command(
                requestId,
                activated.applicationId()
        );
        closures.close(command);

        currentUser.otherAccounting();
        BusinessStateConflictException actorConflict = assertThrows(
                BusinessStateConflictException.class,
                () -> closures.close(command)
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", actorConflict.getErrorCode());

        currentUser.accounting();
        BusinessStateConflictException applicationConflict = assertThrows(
                BusinessStateConflictException.class,
                () -> closures.close(new CloseLoanAccountUseCase.Command(
                        requestId,
                        UUID.randomUUID()
                ))
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", applicationConflict.getErrorCode());

        BusinessStateConflictException newRequest = assertThrows(
                BusinessStateConflictException.class,
                () -> closures.close(closureCommand(activated))
        );
        assertEquals("LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED", newRequest.getErrorCode());
        assertClosureEvidence(activated, 1);
    }

    @Test
    void serializesIdenticalAndCompetingClosureRequests() throws Exception {
        Activated identical = activate("SAME-RACE");
        repayFully(identical);
        CloseLoanAccountUseCase.Command command = closureCommand(identical);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CloseLoanAccountUseCase.Result> first = pool.submit(() -> {
                currentUser.accounting();
                start.await();
                return closures.close(command);
            });
            Future<CloseLoanAccountUseCase.Result> second = pool.submit(() -> {
                currentUser.accounting();
                start.await();
                return closures.close(command);
            });
            start.countDown();
            List<CloseLoanAccountUseCase.Result> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
            assertEquals(1, results.stream()
                    .filter(CloseLoanAccountUseCase.Result::idempotentReplay)
                    .count());
            assertClosureEvidence(identical, 1);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        Activated competing = activate("DIFFERENT-RACE");
        repayFully(competing);
        ExecutorService competitors = Executors.newFixedThreadPool(2);
        CountDownLatch compete = new CountDownLatch(1);
        try {
            Future<Object> first = competitors.submit(() -> {
                currentUser.accounting();
                return closeOrConflict(compete, closureCommand(competing));
            });
            Future<Object> second = competitors.submit(() -> {
                currentUser.accounting();
                return closeOrConflict(compete, closureCommand(competing));
            });
            compete.countDown();
            List<Object> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
            assertEquals(1, results.stream()
                    .filter(CloseLoanAccountUseCase.Result.class::isInstance)
                    .count());
            BusinessStateConflictException rejected = assertInstanceOf(
                    BusinessStateConflictException.class,
                    results.stream()
                            .filter(BusinessStateConflictException.class::isInstance)
                            .findFirst().orElseThrow()
            );
            assertEquals("LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED",
                    rejected.getErrorCode());
            assertClosureEvidence(competing, 1);
        } finally {
            competitors.shutdownNow();
            assertTrue(competitors.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void serializesClosureAgainstSettlementAndOrdinaryPayoff() throws Exception {
        Activated settlementRace = activate("SETTLEMENT-RACE");
        Object[] settlementOutcomes = raceSettlementAndClosure(settlementRace);
        assertInstanceOf(ApproveLoanSettlementUseCase.Result.class,
                settlementOutcomes[0]);
        assertClosureRaceOutcome(settlementRace, settlementOutcomes[1]);

        Activated repaymentRace = activate("REPAYMENT-RACE");
        Object[] repaymentOutcomes = raceRepaymentAndClosure(repaymentRace);
        assertInstanceOf(RecordRepaymentUseCase.Result.class, repaymentOutcomes[0]);
        assertClosureRaceOutcome(repaymentRace, repaymentOutcomes[1]);
    }

    @Test
    void overdueEvaluationCannotReopenClosedAccount() throws Exception {
        Activated activated = activate("OVERDUE-RACE");
        repayFully(activated);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CloseLoanAccountUseCase.Result> closure = pool.submit(() -> {
                currentUser.accounting();
                start.await();
                return closures.close(closureCommand(activated));
            });
            Future<EvaluateLoanAccountOverdueUseCase.Result> evaluation =
                    pool.submit(() -> {
                        start.await();
                        return overdueEvaluator.evaluate(
                                new EvaluateLoanAccountOverdueUseCase.Command(
                                        activated.applicationId(),
                                        activated.accountId(),
                                        OPERATION_DATE,
                                        EVALUATED_AT
                                )
                        );
                    });
            start.countDown();
            closure.get(15, TimeUnit.SECONDS);
            EvaluateLoanAccountOverdueUseCase.Result evaluated =
                    evaluation.get(15, TimeUnit.SECONDS);
            assertTrue(evaluated.noOp());
            assertTrue(evaluated.resultingStatus() == LoanAccountStatus.SETTLED
                    || evaluated.resultingStatus() == LoanAccountStatus.CLOSED);
            assertEquals("CLOSED", status(activated.accountId()));
            assertEquals(0, count(
                    "select count(*) from loan_account_status_transitions "
                            + "where loan_account_id=? and sequence_number>("
                            + "select sequence_number from loan_account_status_transitions "
                            + "where loan_account_id=? "
                            + "and action='ADMINISTRATIVE_CLOSURE')",
                    activated.accountId(),
                    activated.accountId()
            ));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Object[] raceSettlementAndClosure(Activated activated) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> settlement = pool.submit(() -> {
                currentUser.approver();
                start.await();
                return settlements.approve(settlementCommand(activated));
            });
            Future<Object> closure = pool.submit(() -> {
                currentUser.accounting();
                return closeOrConflict(start, closureCommand(activated));
            });
            start.countDown();
            return new Object[]{
                    settlement.get(15, TimeUnit.SECONDS),
                    closure.get(15, TimeUnit.SECONDS)
            };
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Object[] raceRepaymentAndClosure(Activated activated) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> repayment = pool.submit(() -> {
                currentUser.accounting();
                start.await();
                return repayments.record(new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(),
                        activated.applicationId(),
                        "CLOSURE-RACE-PAYOFF-" + activated.token(),
                        outstanding(activated.accountId()),
                        OPERATION_DATE
                ));
            });
            Future<Object> closure = pool.submit(() -> {
                currentUser.accounting();
                return closeOrConflict(start, closureCommand(activated));
            });
            start.countDown();
            return new Object[]{
                    repayment.get(15, TimeUnit.SECONDS),
                    closure.get(15, TimeUnit.SECONDS)
            };
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void assertClosureRaceOutcome(Activated activated, Object outcome) {
        if (outcome instanceof CloseLoanAccountUseCase.Result result) {
            assertEquals(LoanAccountStatus.CLOSED, result.resultingStatus());
            assertEquals("CLOSED", status(activated.accountId()));
            assertClosureEvidence(activated, 1);
        } else {
            BusinessStateConflictException conflict = assertInstanceOf(
                    BusinessStateConflictException.class,
                    outcome
            );
            assertEquals("LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED", conflict.getErrorCode());
            assertEquals("SETTLED", status(activated.accountId()));
            assertClosureEvidence(activated, 0);
        }
    }

    private Object closeOrConflict(
            CountDownLatch start,
            CloseLoanAccountUseCase.Command command
    ) throws InterruptedException {
        start.await();
        try {
            return closures.close(command);
        } catch (BusinessStateConflictException exception) {
            return exception;
        }
    }

    private Activated activate(String prefix) {
        currentUser.accounting();
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        ConfirmManualDisbursementUseCase.Result activation = disbursements.confirm(
                support.command(
                        fixture,
                        UUID.randomUUID(),
                        prefix + "-" + fixture.token()
                )
        );
        return new Activated(
                fixture.applicationId(),
                activation.loanAccountId(),
                fixture.token()
        );
    }

    private RecordRepaymentUseCase.Result repayFully(Activated activated) {
        currentUser.accounting();
        return repayments.record(new RecordRepaymentUseCase.Command(
                UUID.randomUUID(),
                activated.applicationId(),
                "CLOSURE-PAYOFF-" + activated.token(),
                outstanding(activated.accountId()),
                OPERATION_DATE
        ));
    }

    private ApproveLoanSettlementUseCase.Command settlementCommand(
            Activated activated
    ) {
        return new ApproveLoanSettlementUseCase.Command(
                UUID.randomUUID(),
                activated.applicationId(),
                outstanding(activated.accountId()),
                OPERATION_DATE,
                "CLOSURE-SETTLEMENT-" + activated.token()
        );
    }

    private CloseLoanAccountUseCase.Command closureCommand(Activated activated) {
        return new CloseLoanAccountUseCase.Command(
                UUID.randomUUID(),
                activated.applicationId()
        );
    }

    private void assertClosureNotAllowed(Activated activated) {
        currentUser.accounting();
        BusinessStateConflictException conflict = assertThrows(
                BusinessStateConflictException.class,
                () -> closures.close(closureCommand(activated))
        );
        assertEquals("LOAN_ACCOUNT_CLOSURE_NOT_ALLOWED", conflict.getErrorCode());
        assertClosureEvidence(activated, 0);
    }

    private void assertStateConflict(Activated activated) {
        currentUser.accounting();
        BusinessStateConflictException conflict = assertThrows(
                BusinessStateConflictException.class,
                () -> closures.close(closureCommand(activated))
        );
        assertEquals("SYSTEM_STATE_CONFLICT", conflict.getErrorCode());
        assertClosureEvidence(activated, 0);
    }

    private void assertClosureEvidence(Activated activated, int expected) {
        assertEquals(expected, count(
                "select count(*) from loan_account_closures "
                        + "where loan_account_id=?",
                activated.accountId()
        ));
        assertEquals(expected, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id=? "
                        + "and action='ADMINISTRATIVE_CLOSURE'",
                activated.accountId()
        ));
        assertEquals(2 * expected, count(
                "select count(*) from audit_events audit "
                        + "where operation_id in (select id from loan_account_closures "
                        + "where loan_account_id=?)",
                activated.accountId()
        ));
    }

    private FinancialFingerprint fingerprint(Activated activated) {
        return new FinancialFingerprint(
                text("select concat_ws('|',principal_paid,interest_paid,fee_paid,"
                                + "total_paid,principal_outstanding,interest_outstanding,"
                                + "fee_outstanding,total_outstanding,last_payment_value_date,"
                                + "last_payment_recorded_at,servicing_evaluation_date) "
                                + "from loan_accounts where id=?",
                        activated.accountId()),
                text("select string_agg(concat_ws('|',item.installment_number,"
                                + "item.due_date,item.principal_due,item.interest_due,"
                                + "item.fee_due,item.total_due),',' "
                                + "order by item.installment_number) "
                                + "from repayment_schedule_items item "
                                + "join repayment_schedules schedule "
                                + "on schedule.id=item.repayment_schedule_id "
                                + "where schedule.loan_account_id=?",
                        activated.accountId()),
                text("select string_agg(concat_ws('|',progress.installment_number,"
                                + "progress.status,progress.principal_paid,"
                                + "progress.interest_paid,progress.fee_paid,"
                                + "progress.total_paid,progress.total_outstanding),',' "
                                + "order by progress.installment_number) "
                                + "from repayment_installment_progress progress "
                                + "where progress.loan_account_id=?",
                        activated.accountId()),
                count("select count(*) from repayment_transactions "
                                + "where loan_account_id=?",
                        activated.accountId()),
                count("select count(*) from repayment_allocations allocation "
                                + "join repayment_transactions transaction_row "
                                + "on transaction_row.id=allocation.repayment_transaction_id "
                                + "where transaction_row.loan_account_id=?",
                        activated.accountId()),
                count("select count(*) from salary_advance_limit_movements "
                                + "where loan_account_id=?",
                        activated.accountId()),
                count("select count(*) from repayment_installment_status_transitions history "
                                + "join repayment_schedule_items item "
                                + "on item.id=history.repayment_schedule_item_id "
                                + "join repayment_schedules schedule "
                                + "on schedule.id=item.repayment_schedule_id "
                                + "where schedule.loan_account_id=?",
                        activated.accountId()),
                text("select status from loan_applications where id=?",
                        activated.applicationId()),
                count("select count(*) from loan_application_status_transitions "
                                + "where loan_application_id=?",
                        activated.applicationId())
        );
    }

    private BigDecimal outstanding(UUID accountId) {
        return jdbc.queryForObject(
                "select total_outstanding from loan_accounts where id=?",
                BigDecimal.class,
                accountId
        );
    }

    private String status(UUID accountId) {
        return text("select status from loan_accounts where id=?", accountId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private static <T> T target(T proxiedSpy) {
        return AopTestUtils.getUltimateTargetObject(proxiedSpy);
    }

    private record Activated(UUID applicationId, UUID accountId, String token) {
    }

    private record FinancialFingerprint(
            String balance,
            String schedule,
            String progress,
            int transactions,
            int allocations,
            int exposureMovements,
            int installmentHistory,
            String applicationStatus,
            int applicationHistory
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClosureTestConfiguration {
        @Bean
        @Primary
        ThreadLocalCurrentUser closureCurrentUser() {
            return new ThreadLocalCurrentUser();
        }

        @Bean
        @Primary
        MutableClock closureClock() {
            return new MutableClock(Instant.parse("2026-09-02T10:00:00Z"));
        }
    }

    static final class ThreadLocalCurrentUser implements CurrentUserProvider {
        private final ThreadLocal<AuthenticatedUser> users = new ThreadLocal<>();

        void accounting() {
            users.set(new AuthenticatedUser(
                    ACCOUNTING_USER_ID,
                    "accounting@meridian.test",
                    "STAFF",
                    null,
                    Set.of("ACCOUNTING_OFFICER"),
                    Set.of("loan:disburse", "repayment:update", "loan:account:close")
            ));
        }

        void otherAccounting() {
            users.set(new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000307"),
                    "other.accounting@meridian.test",
                    "STAFF",
                    null,
                    Set.of("ACCOUNTING_OFFICER"),
                    Set.of("loan:account:close")
            ));
        }

        void approver() {
            users.set(new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000303"),
                    "approver@meridian.test",
                    "STAFF",
                    null,
                    Set.of("APPROVER"),
                    Set.of("loan:settlement:approve")
            ));
        }

        void approverWithClosurePermission() {
            users.set(new AuthenticatedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000303"),
                    "approver@meridian.test",
                    "STAFF",
                    null,
                    Set.of("APPROVER"),
                    Set.of("loan:account:close")
            ));
        }

        void customer(UUID userId, UUID customerId) {
            users.set(new AuthenticatedUser(
                    userId,
                    "customer@meridian.test",
                    "CUSTOMER",
                    customerId,
                    Set.of("CUSTOMER"),
                    Set.of("loan:account:close")
            ));
        }

        @Override
        public AuthenticatedUser currentUser() {
            return users.get();
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void set(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Closure test clock uses UTC.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
