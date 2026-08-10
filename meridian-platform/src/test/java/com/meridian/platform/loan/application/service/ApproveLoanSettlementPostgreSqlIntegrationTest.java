package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(ApproveLoanSettlementPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class ApproveLoanSettlementPostgreSqlIntegrationTest {

    private static final String SCHEMA = "settlement_posting_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID APPROVER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000303"
    );
    private static final LocalDate ACTIVE_DATE = LocalDate.of(2026, 7, 28);
    private static final LocalDate OVERDUE_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDateTime EVALUATED_AT =
            LocalDateTime.of(2026, 9, 1, 0, 5);

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired ApproveLoanSettlementUseCase settlements;
    @Autowired EvaluateLoanAccountOverdueUseCase overdueEvaluator;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock testClock;
    @MockitoBean CurrentUserProvider currentUserProvider;

    private ManualDisbursementActivationPostgreSqlTestSupport support;
    private LocalDate paymentDate;

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
        testClock.set(Instant.parse("2026-07-28T10:00:00Z"));
        paymentDate = ACTIVE_DATE;
        support = new ManualDisbursementActivationPostgreSqlTestSupport(
                jdbc,
                transactionManager
        );
        accountingActor();
    }

    @Test
    void settlesActiveAccountWithExactPaymentEvidenceAndExactReplay() {
        Activated activated = activate("ACTIVE");
        BigDecimal outstanding = outstanding(activated.accountId());
        BigDecimal principalOutstanding = amount(
                "select principal_outstanding from loan_accounts where id=?",
                activated.accountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id=?",
                activated.limitId()
        );
        String scheduleBefore = scheduleFingerprint(activated.accountId());
        assertEquals("ACTIVE", text(
                "select status from loan_accounts where id=?",
                activated.accountId()
        ));
        UUID requestId = UUID.randomUUID();
        ApproveLoanSettlementUseCase.Command command = command(
                requestId,
                activated.applicationId(),
                outstanding,
                " SETTLE-" + activated.token().toLowerCase() + " "
        );
        approverActor();

        ApproveLoanSettlementUseCase.Result first = settlements.approve(command);

        assertFalse(first.idempotentReplay());
        assertEquals(LoanAccountStatus.SETTLED, first.accountBalance().status());
        assertEquals(0, first.accountBalance().totalOutstanding().signum());
        assertEquals(0, principalOutstanding.compareTo(
                first.principalAllocatedAndReleased()
        ));
        assertEquals("APPROVED_SETTLEMENT", text(
                "select transaction_type from repayment_transactions where id=?",
                first.repaymentTransactionId()
        ));
        assertEquals(1, count(
                "select count(*) from approved_loan_settlements "
                        + "where repayment_transaction_id=?",
                first.repaymentTransactionId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_installment_progress "
                        + "where loan_account_id=? and (status<>'PAID' or total_outstanding<>0)",
                activated.accountId()
        ));
        assertEquals(0, usedBefore.subtract(principalOutstanding).compareTo(amount(
                "select used_amount from salary_advance_limits where id=?",
                activated.limitId()
        )));
        assertEquals(1, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id=? "
                        + "and movement_type='REPAID_RELEASED' and amount=?",
                first.repaymentTransactionId(),
                principalOutstanding
        ));
        assertEquals(scheduleBefore, scheduleFingerprint(activated.accountId()));
        assertDeterministicAllocation(first.repaymentTransactionId());

        ApproveLoanSettlementUseCase.Result replay = settlements.approve(command);

        assertTrue(replay.idempotentReplay());
        assertEquals(first.accountBalance(), replay.accountBalance());
        assertEquals(first.repaymentTransactionId(), replay.repaymentTransactionId());
        assertOperationCounts(first.repaymentTransactionId(), activated.accountId(), 1);
    }

    @Test
    void settlesOverdueAccountAndCannotBeReturnedToOpenServicing() {
        Activated activated = activate("OVERDUE");
        testClock.set(Instant.parse("2026-09-01T10:00:00Z"));
        paymentDate = OVERDUE_DATE;
        overdueEvaluator.evaluate(new EvaluateLoanAccountOverdueUseCase.Command(
                activated.applicationId(),
                activated.accountId(),
                paymentDate,
                EVALUATED_AT
        ));
        assertEquals("OVERDUE", text(
                "select status from loan_accounts where id=?",
                activated.accountId()
        ));
        approverActor();

        ApproveLoanSettlementUseCase.Result result = settlements.approve(command(
                UUID.randomUUID(),
                activated.applicationId(),
                outstanding(activated.accountId()),
                "OVERDUE-SETTLE-" + activated.token()
        ));

        assertEquals(LoanAccountStatus.SETTLED, result.accountBalance().status());
        EvaluateLoanAccountOverdueUseCase.Result evaluation = overdueEvaluator.evaluate(
                new EvaluateLoanAccountOverdueUseCase.Command(
                        activated.applicationId(),
                        activated.accountId(),
                        paymentDate,
                        EVALUATED_AT.plusMinutes(1)
                )
        );
        assertTrue(evaluation.noOp());
        assertEquals(LoanAccountStatus.SETTLED, evaluation.resultingStatus());
    }

    @Test
    void rejectsStaleDiscountedAndExcessExpectedAmountsWithoutWrites() {
        Activated activated = activate("AMOUNT");
        BigDecimal outstanding = outstanding(activated.accountId());
        approverActor();

        for (BigDecimal invalid : List.of(
                outstanding.subtract(money("1")),
                outstanding.add(money("1"))
        )) {
            BusinessRuleViolationException rejected = assertThrows(
                    BusinessRuleViolationException.class,
                    () -> settlements.approve(command(
                            UUID.randomUUID(),
                            activated.applicationId(),
                            invalid,
                            "INVALID-" + invalid.toPlainString() + "-" + activated.token()
                    ))
            );
            assertEquals("SETTLEMENT_AMOUNT_INVALID", rejected.getErrorCode());
        }
        assertEquals(0, count(
                "select count(*) from approved_loan_settlements "
                        + "where loan_application_id=?",
                activated.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id=?",
                activated.applicationId()
        ));
        assertEquals(0, amount(
                "select total_paid from loan_accounts where id=?",
                activated.accountId()
        ).signum());
    }

    @Test
    void rejectsInvalidValueDateRoleAndSettledState() {
        Activated activated = activate("GUARDS");
        BigDecimal outstanding = outstanding(activated.accountId());
        approverActor();
        BusinessRuleViolationException date = assertThrows(
                BusinessRuleViolationException.class,
                () -> settlements.approve(new ApproveLoanSettlementUseCase.Command(
                        UUID.randomUUID(),
                        activated.applicationId(),
                        outstanding,
                        LocalDate.of(2026, 7, 26),
                        "DATE-" + activated.token()
                ))
        );
        assertEquals("SETTLEMENT_VALUE_DATE_INVALID", date.getErrorCode());

        accountingActor();
        AuthorizationException role = assertThrows(
                AuthorizationException.class,
                () -> settlements.approve(command(
                        UUID.randomUUID(),
                        activated.applicationId(),
                        outstanding,
                        "ROLE-" + activated.token()
                ))
        );
        assertEquals("APPROVER_ROLE_REQUIRED", role.getErrorCode());

        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.fromString("00000000-0000-0000-0000-000000000306"),
                "customer@meridian.local", "CUSTOMER", UUID.randomUUID(),
                Set.of("CUSTOMER"), Set.of("loan:settlement:approve")
        ));
        AuthorizationException customer = assertThrows(
                AuthorizationException.class,
                () -> settlements.approve(command(
                        UUID.randomUUID(),
                        activated.applicationId(),
                        outstanding,
                        "CUSTOMER-" + activated.token()
                ))
        );
        assertEquals("APPROVER_ROLE_REQUIRED", customer.getErrorCode());

        approverActor();
        settlements.approve(command(
                UUID.randomUUID(),
                activated.applicationId(),
                outstanding,
                "FIRST-" + activated.token()
        ));
        BusinessStateConflictException state = assertThrows(
                BusinessStateConflictException.class,
                () -> settlements.approve(command(
                        UUID.randomUUID(),
                        activated.applicationId(),
                        outstanding,
                        "SECOND-" + activated.token()
                ))
        );
        assertEquals("SETTLEMENT_NOT_ALLOWED", state.getErrorCode());
    }

    @Test
    void detectsEveryLogicalReplayConflictAndDuplicatePaymentReference() {
        Activated first = activate("REPLAY");
        BigDecimal outstanding = outstanding(first.accountId());
        UUID requestId = UUID.randomUUID();
        ApproveLoanSettlementUseCase.Command original = command(
                requestId,
                first.applicationId(),
                outstanding,
                "REPLAY-" + first.token()
        );
        approverActor();
        settlements.approve(original);

        BusinessStateConflictException operationTypeConflict = assertThrows(
                BusinessStateConflictException.class,
                () -> repayments.record(new RecordRepaymentUseCase.Command(
                        requestId,
                        first.applicationId(),
                        original.externalPaymentReference(),
                        outstanding,
                        paymentDate
                ))
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", operationTypeConflict.getErrorCode());

        assertIdempotencyConflict(new ApproveLoanSettlementUseCase.Command(
                requestId, first.applicationId(), outstanding.add(money("1")),
                paymentDate, original.externalPaymentReference()
        ));
        assertIdempotencyConflict(new ApproveLoanSettlementUseCase.Command(
                requestId, first.applicationId(), outstanding,
                paymentDate.minusDays(1), original.externalPaymentReference()
        ));
        assertIdempotencyConflict(new ApproveLoanSettlementUseCase.Command(
                requestId, first.applicationId(), outstanding,
                paymentDate, "CHANGED-" + first.token()
        ));
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.fromString("00000000-0000-0000-0000-000000000305"),
                "other.approver@meridian.local", "STAFF", null,
                Set.of("APPROVER"), Set.of("loan:settlement:approve")
        ));
        assertIdempotencyConflict(original);

        accountingActor();
        Activated second = activateAsAccounting("DUPLICATE");
        approverActor();
        BusinessStateConflictException duplicate = assertThrows(
                BusinessStateConflictException.class,
                () -> settlements.approve(new ApproveLoanSettlementUseCase.Command(
                        UUID.randomUUID(), second.applicationId(),
                        outstanding(second.accountId()), paymentDate,
                        original.externalPaymentReference()
                ))
        );
        assertEquals("DUPLICATE_PAYMENT_REFERENCE", duplicate.getErrorCode());

        accountingActor();
        Activated ordinaryPayoff = activateAsAccounting("OPERATION-TYPE");
        BigDecimal payoffAmount = outstanding(ordinaryPayoff.accountId());
        UUID payoffRequestId = UUID.randomUUID();
        String payoffReference = "OPERATION-TYPE-" + ordinaryPayoff.token();
        approverActor();
        repayments.record(new RecordRepaymentUseCase.Command(
                payoffRequestId,
                ordinaryPayoff.applicationId(),
                payoffReference,
                payoffAmount,
                paymentDate
        ));
        assertIdempotencyConflict(new ApproveLoanSettlementUseCase.Command(
                payoffRequestId,
                ordinaryPayoff.applicationId(),
                payoffAmount,
                paymentDate,
                payoffReference
        ));
    }

    @Test
    void serializesIdenticalAndCompetingSettlementRequests() throws Exception {
        Activated identical = activate("SAME-RACE");
        ApproveLoanSettlementUseCase.Command command = command(
                UUID.randomUUID(),
                identical.applicationId(),
                outstanding(identical.accountId()),
                "SAME-RACE-" + identical.token()
        );
        approverActor();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ApproveLoanSettlementUseCase.Result> first = executor.submit(() -> {
                start.await();
                return settlements.approve(command);
            });
            Future<ApproveLoanSettlementUseCase.Result> second = executor.submit(() -> {
                start.await();
                return settlements.approve(command);
            });
            start.countDown();
            List<ApproveLoanSettlementUseCase.Result> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
            assertEquals(1, results.stream()
                    .filter(ApproveLoanSettlementUseCase.Result::idempotentReplay)
                    .count());
            assertEquals(results.getFirst().repaymentTransactionId(),
                    results.getLast().repaymentTransactionId());
            assertEquals(1, count(
                    "select count(*) from approved_loan_settlements "
                            + "where loan_application_id=?",
                    identical.applicationId()
            ));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        Activated competing = activate("DIFFERENT-RACE");
        BigDecimal expected = outstanding(competing.accountId());
        ApproveLoanSettlementUseCase.Command left = command(
                UUID.randomUUID(), competing.applicationId(), expected,
                "LEFT-" + competing.token()
        );
        ApproveLoanSettlementUseCase.Command right = command(
                UUID.randomUUID(), competing.applicationId(), expected,
                "RIGHT-" + competing.token()
        );
        approverActor();
        ExecutorService competitors = Executors.newFixedThreadPool(2);
        CountDownLatch compete = new CountDownLatch(1);
        try {
            Future<Object> first = competitors.submit(() -> settleOrConflict(compete, left));
            Future<Object> second = competitors.submit(() -> settleOrConflict(compete, right));
            compete.countDown();
            List<Object> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
            assertEquals(1, results.stream()
                    .filter(ApproveLoanSettlementUseCase.Result.class::isInstance)
                    .count());
            BusinessStateConflictException rejected = assertInstanceOf(
                    BusinessStateConflictException.class,
                    results.stream().filter(BusinessStateConflictException.class::isInstance)
                            .findFirst().orElseThrow()
            );
            assertEquals("SETTLEMENT_NOT_ALLOWED", rejected.getErrorCode());
            assertEquals(1, count(
                    "select count(*) from approved_loan_settlements "
                            + "where loan_application_id=?",
                    competing.applicationId()
            ));
        } finally {
            competitors.shutdownNow();
            assertTrue(competitors.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void serializesSettlementAgainstRepaymentAndOverdueEvaluation() throws Exception {
        Activated paymentRace = activate("PAYMENT-RACE");
        BigDecimal expected = outstanding(paymentRace.accountId());
        ApproveLoanSettlementUseCase.Command settlement = command(
                UUID.randomUUID(), paymentRace.applicationId(), expected,
                "SETTLEMENT-RACE-" + paymentRace.token()
        );
        RecordRepaymentUseCase.Command repayment = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), paymentRace.applicationId(),
                "REPAYMENT-RACE-" + paymentRace.token(), money("1"), paymentDate
        );
        approverActor();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> settlementResult = executor.submit(
                    () -> settleOrConflict(start, settlement)
            );
            Future<Object> repaymentResult = executor.submit(() -> {
                start.await();
                try {
                    return repayments.record(repayment);
                } catch (BusinessRuleViolationException
                         | BusinessStateConflictException exception) {
                    return exception;
                }
            });
            start.countDown();
            Object settlementOutcome = settlementResult.get(15, TimeUnit.SECONDS);
            Object repaymentOutcome = repaymentResult.get(15, TimeUnit.SECONDS);
            assertEquals(1, List.of(settlementOutcome, repaymentOutcome).stream()
                    .filter(item -> item instanceof ApproveLoanSettlementUseCase.Result
                            || item instanceof RecordRepaymentUseCase.Result)
                    .count());
            assertEquals(1, count(
                    "select count(*) from repayment_transactions "
                            + "where loan_application_id=?",
                    paymentRace.applicationId()
            ));
            if (settlementOutcome instanceof ApproveLoanSettlementUseCase.Result) {
                BusinessStateConflictException rejected = assertInstanceOf(
                        BusinessStateConflictException.class,
                        repaymentOutcome
                );
                assertEquals("REPAYMENT_NOT_ALLOWED", rejected.getErrorCode());
                assertEquals("SETTLED", text(
                        "select status from loan_accounts where id=?",
                        paymentRace.accountId()
                ));
            } else {
                BusinessRuleViolationException rejected = assertInstanceOf(
                        BusinessRuleViolationException.class,
                        settlementOutcome
                );
                assertEquals("SETTLEMENT_AMOUNT_INVALID", rejected.getErrorCode());
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        Activated evaluationRace = activate("EVALUATION-RACE");
        testClock.set(Instant.parse("2026-09-01T10:00:00Z"));
        paymentDate = OVERDUE_DATE;
        ApproveLoanSettlementUseCase.Command settlementCommand = command(
                UUID.randomUUID(), evaluationRace.applicationId(),
                outstanding(evaluationRace.accountId()),
                "EVALUATION-SETTLE-" + evaluationRace.token()
        );
        approverActor();
        ExecutorService evaluators = Executors.newFixedThreadPool(2);
        CountDownLatch evaluate = new CountDownLatch(1);
        try {
            Future<ApproveLoanSettlementUseCase.Result> settlementResult =
                    evaluators.submit(() -> {
                        evaluate.await();
                        return settlements.approve(settlementCommand);
                    });
            Future<EvaluateLoanAccountOverdueUseCase.Result> evaluationResult =
                    evaluators.submit(() -> {
                        evaluate.await();
                        return overdueEvaluator.evaluate(
                                new EvaluateLoanAccountOverdueUseCase.Command(
                                        evaluationRace.applicationId(),
                                        evaluationRace.accountId(),
                                        OVERDUE_DATE,
                                        EVALUATED_AT
                                )
                        );
                    });
            evaluate.countDown();
            settlementResult.get(15, TimeUnit.SECONDS);
            evaluationResult.get(15, TimeUnit.SECONDS);
            assertEquals("SETTLED", text(
                    "select status from loan_accounts where id=?",
                    evaluationRace.accountId()
            ));
            assertEquals(0, count(
                    "select count(*) from loan_account_status_transitions "
                            + "where loan_account_id=? and sequence_number>("
                            + "select sequence_number from loan_account_status_transitions "
                            + "where loan_account_id=? and action='APPROVED_SETTLEMENT') "
                            + "and to_status in ('ACTIVE','OVERDUE')",
                    evaluationRace.accountId(), evaluationRace.accountId()
            ));
        } finally {
            evaluators.shutdownNow();
            assertTrue(evaluators.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Activated activate(String prefix) {
        accountingActor();
        return activateAsAccounting(prefix);
    }

    private Activated activateAsAccounting(String prefix) {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                prefix + "-" + fixture.token()
        ));
        return new Activated(
                fixture.applicationId(),
                activation.loanAccountId(),
                fixture.limitId(),
                fixture.token()
        );
    }

    private ApproveLoanSettlementUseCase.Command command(
            UUID requestId,
            UUID applicationId,
            BigDecimal amount,
            String reference
    ) {
        return new ApproveLoanSettlementUseCase.Command(
                requestId,
                applicationId,
                amount,
                paymentDate,
                reference
        );
    }

    private void assertIdempotencyConflict(
            ApproveLoanSettlementUseCase.Command command
    ) {
        BusinessStateConflictException conflict = assertThrows(
                BusinessStateConflictException.class,
                () -> settlements.approve(command)
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", conflict.getErrorCode());
    }

    private Object settleOrConflict(
            CountDownLatch start,
            ApproveLoanSettlementUseCase.Command command
    ) throws InterruptedException {
        start.await();
        try {
            return settlements.approve(command);
        } catch (BusinessRuleViolationException
                 | BusinessStateConflictException exception) {
            return exception;
        }
    }

    private void assertDeterministicAllocation(UUID transactionId) {
        List<AllocationRow> rows = jdbc.query(
                "select schedule_item.installment_number,allocation.component "
                        + "from repayment_allocations allocation "
                        + "join repayment_schedule_items schedule_item "
                        + "on schedule_item.id=allocation.repayment_schedule_item_id "
                        + "where allocation.repayment_transaction_id=? "
                        + "order by allocation.allocation_sequence",
                (result, row) -> new AllocationRow(
                        result.getInt("installment_number"),
                        result.getString("component")
                ),
                transactionId
        );
        int priorInstallment = 0;
        int priorComponent = 0;
        for (AllocationRow row : rows) {
            int component = switch (row.component()) {
                case "FEE" -> 1;
                case "INTEREST" -> 2;
                case "PRINCIPAL" -> 3;
                default -> throw new AssertionError("Unexpected allocation component");
            };
            assertTrue(row.installment() >= priorInstallment);
            if (row.installment() == priorInstallment) {
                assertTrue(component > priorComponent);
            } else {
                priorComponent = 0;
            }
            priorInstallment = row.installment();
            priorComponent = component;
        }
    }

    private void assertOperationCounts(
            UUID transactionId,
            UUID accountId,
            int expected
    ) {
        assertEquals(expected, count(
                "select count(*) from approved_loan_settlements "
                        + "where repayment_transaction_id=?",
                transactionId
        ));
        assertEquals(expected, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where repayment_transaction_id=?",
                transactionId
        ));
        assertEquals(expected, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id=?",
                transactionId
        ));
        assertEquals(2 * expected, count(
                "select count(*) from audit_events where operation_id=?",
                transactionId
        ));
        assertEquals(expected, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id=? and operation_id=? "
                        + "and action='APPROVED_SETTLEMENT'",
                accountId, transactionId
        ));
    }

    private String scheduleFingerprint(UUID accountId) {
        return jdbc.queryForObject(
                "select string_agg(concat_ws('|',item.installment_number,item.due_date,"
                        + "item.principal_due,item.interest_due,item.fee_due,item.total_due),"
                        + "',' order by item.installment_number) "
                        + "from repayment_schedule_items item "
                        + "join repayment_schedules schedule "
                        + "on schedule.id=item.repayment_schedule_id "
                        + "where schedule.loan_account_id=?",
                String.class,
                accountId
        );
    }

    private BigDecimal outstanding(UUID accountId) {
        return amount(
                "select total_outstanding from loan_accounts where id=?",
                accountId
        );
    }

    private BigDecimal amount(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, BigDecimal.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private void accountingActor() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting@meridian.local",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse")
        ));
    }

    private void approverActor() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                APPROVER_USER_ID,
                "approver@meridian.local",
                "STAFF",
                null,
                Set.of("APPROVER"),
                Set.of("loan:settlement:approve")
        ));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private record Activated(
            UUID applicationId,
            UUID accountId,
            UUID limitId,
            String token
    ) {
    }

    private record AllocationRow(int installment, String component) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        MutableClock settlementClock() {
            return new MutableClock(Instant.parse("2026-07-28T10:00:00Z"));
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
                throw new IllegalArgumentException("Settlement test clock uses UTC.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
