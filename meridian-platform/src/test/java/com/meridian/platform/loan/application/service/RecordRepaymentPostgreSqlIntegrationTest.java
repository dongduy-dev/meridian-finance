package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
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
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.VALUE_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(RecordRepaymentPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class RecordRepaymentPostgreSqlIntegrationTest {
    private static final String SCHEMA = "repayment_posting_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider currentUserProvider;

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
        support = new ManualDisbursementActivationPostgreSqlTestSupport(
                jdbc, transactionManager
        );
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "repayment.operator@meridian.test", "STAFF",
                null, Set.of("ACCOUNTING_OFFICER"), Set.of("repayment:update")
        ));
    }

    @Test
    void postsRepaymentReleasesPrincipalAndReplaysOriginalOutcomeAfterLaterPayment() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "ACTIVATE-" + fixture.token()
        ));
        BigDecimal fee = amount(
                "select fee_outstanding from repayment_installment_progress "
                        + "where loan_account_id = ? order by installment_number limit 1",
                activation.loanAccountId()
        );
        BigDecimal interest = amount(
                "select interest_outstanding from repayment_installment_progress "
                        + "where loan_account_id = ? order by installment_number limit 1",
                activation.loanAccountId()
        );
        BigDecimal payment = fee.add(interest).add(money("100"));
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        BigDecimal availableBefore = amount(
                "select available_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        BigDecimal reservedBefore = amount(
                "select reserved_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        UUID requestId = UUID.randomUUID();
        RecordRepaymentUseCase.Command command = new RecordRepaymentUseCase.Command(
                requestId, fixture.applicationId(),
                " repay-" + fixture.token().toLowerCase() + " ", payment, VALUE_DATE
        );

        RecordRepaymentUseCase.Result first = repayments.record(command);

        assertFalse(first.idempotentReplay());
        assertEquals(money("100"), first.principalAllocatedAndReleased());
        assertEquals(RepaymentAllocationComponent.PRINCIPAL,
                first.allocations().getLast().component());
        assertEquals(0, usedBefore.subtract(money("100")).compareTo(amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, availableBefore.add(money("100")).compareTo(amount(
                "select available_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, reservedBefore.compareTo(amount(
                "select reserved_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(1, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id = ? and movement_type = 'REPAID_RELEASED'",
                first.repaymentTransactionId()
        ));

        RecordRepaymentUseCase.Result immediateReplay = repayments.record(command);
        assertTrue(immediateReplay.idempotentReplay());
        assertEquals(first.accountBalance(), immediateReplay.accountBalance());

        repayments.record(new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(),
                "LATER-" + fixture.token(), money("1"), VALUE_DATE
        ));
        RecordRepaymentUseCase.Result replayAfterLaterPayment = repayments.record(command);
        assertTrue(replayAfterLaterPayment.idempotentReplay());
        assertEquals(first.accountBalance(), replayAfterLaterPayment.accountBalance());
        assertEquals(first.installmentProgress(), replayAfterLaterPayment.installmentProgress());
        assertEquals(1, count(
                "select count(*) from audit_events where operation_id = ? "
                        + "and action = 'REPAYMENT_RECORDED'",
                first.repaymentTransactionId()
        ));
    }

    @Test
    void rejectsOverpaymentAndCanonicalDuplicateReferenceWithoutPartialWrites() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "GUARD-" + fixture.token()
        ));
        BigDecimal outstanding = amount(
                "select total_outstanding from loan_accounts where id = ?",
                activation.loanAccountId()
        );

        BusinessRuleViolationException overpayment = assertThrows(
                BusinessRuleViolationException.class,
                () -> repayments.record(new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "OVER-" + fixture.token(), outstanding.add(money("1")), VALUE_DATE
                ))
        );
        assertEquals("REPAYMENT_EXCEEDS_OUTSTANDING", overpayment.getErrorCode());

        repayments.record(new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(),
                "DUP-" + fixture.token(), money("1"), VALUE_DATE
        ));
        BusinessStateConflictException duplicate = assertThrows(
                BusinessStateConflictException.class,
                () -> repayments.record(new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        " dup-" + fixture.token().toLowerCase() + " ",
                        money("1"), VALUE_DATE
                ))
        );
        assertEquals("DUPLICATE_PAYMENT_REFERENCE", duplicate.getErrorCode());
        assertEquals(1, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
    }

    @Test
    void serializesConcurrentExactRequestToOneDurableOperation() throws Exception {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "RACE-" + fixture.token()
        ));
        RecordRepaymentUseCase.Command command = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(),
                "SAME-" + fixture.token(), money("1"), VALUE_DATE
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RecordRepaymentUseCase.Result> first = executor.submit(() -> {
                start.await();
                return repayments.record(command);
            });
            Future<RecordRepaymentUseCase.Result> second = executor.submit(() -> {
                start.await();
                return repayments.record(command);
            });
            start.countDown();

            RecordRepaymentUseCase.Result firstResult = first.get(30, TimeUnit.SECONDS);
            RecordRepaymentUseCase.Result secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(firstResult.repaymentTransactionId(),
                    secondResult.repaymentTransactionId());
            assertTrue(firstResult.idempotentReplay()
                    ^ secondResult.idempotentReplay());
            assertEquals(1, count(
                    "select count(*) from repayment_transactions where request_id = ?",
                    command.requestId()
            ));
            assertEquals(1, count(
                    "select count(*) from repayment_operation_outcomes where repayment_transaction_id = ?",
                    firstResult.repaymentTransactionId()
            ));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    @Test
    void rejectsEveryReusedRequestIdentityMismatchWithoutReplayWrites() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "IDENTITY-" + fixture.token()
        ));
        String secretReference = "SECRET-" + fixture.token();
        RecordRepaymentUseCase.Command command = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(), secretReference,
                money("1"), VALUE_DATE
        );
        RecordRepaymentUseCase.Result first = repayments.record(command);

        assertIdempotencyConflict(new RecordRepaymentUseCase.Command(
                command.requestId(), command.loanApplicationId(), "OTHER-" + fixture.token(),
                command.amount(), command.paymentValueDate()
        ), secretReference);
        assertIdempotencyConflict(new RecordRepaymentUseCase.Command(
                command.requestId(), command.loanApplicationId(), secretReference,
                money("2"), command.paymentValueDate()
        ), secretReference);
        assertIdempotencyConflict(new RecordRepaymentUseCase.Command(
                command.requestId(), command.loanApplicationId(), secretReference,
                command.amount(), command.paymentValueDate().minusDays(1)
        ), secretReference);
        assertIdempotencyConflict(new RecordRepaymentUseCase.Command(
                command.requestId(), UUID.randomUUID(), secretReference,
                command.amount(), command.paymentValueDate()
        ), secretReference);

        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "other.operator@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("repayment:update")
        ));
        assertIdempotencyConflict(command, secretReference);

        assertFalse(first.toString().contains(secretReference));
        assertEquals(1, count(
                "select count(*) from repayment_transactions where request_id = ?",
                command.requestId()
        ));
        assertEquals(1, count(
                "select count(*) from audit_events where operation_id = ? "
                        + "and action = 'REPAYMENT_RECORDED'",
                first.repaymentTransactionId()
        ));
    }

    @Test
    void exactPayoffSettlesAndRejectsAnyAdditionalRepayment() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "PAYOFF-" + fixture.token()
        ));
        BigDecimal outstanding = amount(
                "select total_outstanding from loan_accounts where id = ?",
                activation.loanAccountId()
        );

        RecordRepaymentUseCase.Result payoff = repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "FULL-" + fixture.token(), outstanding, VALUE_DATE
                )
        );

        assertEquals(LoanAccountStatus.SETTLED, payoff.accountBalance().status());
        assertEquals(0, payoff.accountBalance().totalOutstanding().signum());
        assertEquals(1, count(
                "select count(*) from loan_account_status_transitions "
                        + "where operation_id = ? and to_status = 'SETTLED'",
                payoff.repaymentTransactionId()
        ));
        assertEquals(1, count(
                "select count(*) from audit_events where operation_id = ? "
                        + "and action = 'LOAN_ACCOUNT_STATUS_CHANGED'",
                payoff.repaymentTransactionId()
        ));

        BusinessStateConflictException rejected = assertThrows(
                BusinessStateConflictException.class,
                () -> repayments.record(new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "AFTER-" + fixture.token(), money("1"), VALUE_DATE
                ))
        );
        assertEquals("REPAYMENT_NOT_ALLOWED", rejected.getErrorCode());
        assertEquals(1, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
    }

    @Test
    void serializesDifferentRequestsAndCanonicalReferenceRaceOnOneAccount()
            throws Exception {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "ACCOUNT-RACE-" + fixture.token()
        ));
        BigDecimal paidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        RecordRepaymentUseCase.Command firstCommand = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(),
                "RACE-A-" + fixture.token(), money("1"), VALUE_DATE
        );
        RecordRepaymentUseCase.Command secondCommand = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), fixture.applicationId(),
                "RACE-B-" + fixture.token(), money("1"), VALUE_DATE
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RecordRepaymentUseCase.Result> first = executor.submit(() -> {
                start.await();
                return repayments.record(firstCommand);
            });
            Future<RecordRepaymentUseCase.Result> second = executor.submit(() -> {
                start.await();
                return repayments.record(secondCommand);
            });
            start.countDown();
            RecordRepaymentUseCase.Result firstResult = first.get(30, TimeUnit.SECONDS);
            RecordRepaymentUseCase.Result secondResult = second.get(30, TimeUnit.SECONDS);

            assertFalse(firstResult.idempotentReplay());
            assertFalse(secondResult.idempotentReplay());
            assertEquals(2, count(
                    "select count(*) from repayment_transactions "
                            + "where loan_application_id = ?",
                    fixture.applicationId()
            ));
            assertEquals(0, paidBefore.add(money("2")).compareTo(amount(
                    "select total_paid from loan_accounts where id = ?",
                    activation.loanAccountId()
            )));
            BigDecimal released = firstResult.principalAllocatedAndReleased()
                    .add(secondResult.principalAllocatedAndReleased());
            assertEquals(0, usedBefore.subtract(released).compareTo(amount(
                    "select used_amount from salary_advance_limits where id = ?",
                    fixture.limitId()
            )));

            String sharedReference = "SHARED-" + fixture.token();
            CountDownLatch duplicateStart = new CountDownLatch(1);
            Future<Object> duplicateFirst = executor.submit(() -> recordOrConflict(
                    duplicateStart,
                    new RecordRepaymentUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(), sharedReference,
                            money("1"), VALUE_DATE
                    )
            ));
            Future<Object> duplicateSecond = executor.submit(() -> recordOrConflict(
                    duplicateStart,
                    new RecordRepaymentUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(),
                            " " + sharedReference.toLowerCase() + " ",
                            money("1"), VALUE_DATE
                    )
            ));
            duplicateStart.countDown();
            Object left = duplicateFirst.get(30, TimeUnit.SECONDS);
            Object right = duplicateSecond.get(30, TimeUnit.SECONDS);

            assertTrue(left instanceof RecordRepaymentUseCase.Result
                    ^ right instanceof RecordRepaymentUseCase.Result);
            Object conflictValue = left instanceof BusinessStateConflictException
                    ? left : right;
            assertTrue(conflictValue instanceof BusinessStateConflictException);
            assertEquals("DUPLICATE_PAYMENT_REFERENCE",
                    ((BusinessStateConflictException) conflictValue).getErrorCode());
            assertEquals(3, count(
                    "select count(*) from repayment_transactions "
                            + "where loan_application_id = ?",
                    fixture.applicationId()
            ));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void replayRejectsItemSpecificOutcomeContradiction() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "REPLAY-ITEM-" + fixture.token()
        ));
        UUID requestId = UUID.randomUUID();
        String paymentReference = "REPLAY-ITEM-PAY-" + fixture.token();
        RecordRepaymentUseCase.Result recorded = repayments.record(
                new RecordRepaymentUseCase.Command(
                        requestId, fixture.applicationId(), paymentReference,
                        money("100"), VALUE_DATE
                )
        );
        String originalJson = jdbc.queryForObject(
                "select outcome_json::text from repayment_operation_outcomes "
                        + "where repayment_transaction_id = ?",
                String.class,
                recorded.repaymentTransactionId()
        );
        jdbc.execute("alter table repayment_operation_outcomes disable trigger "
                + "trg_repayment_operation_outcomes_immutable");
        try {
            jdbc.update(
                    "update repayment_operation_outcomes set outcome_json = "
                            + "jsonb_set(jsonb_set(jsonb_set(jsonb_set(outcome_json,"
                            + "'{installments,0,previousStatus}',to_jsonb('PARTIALLY_PAID'::text)),"
                            + "'{installments,0,statusChanged}','false'::jsonb),"
                            + "'{installments,1,previousStatus}',to_jsonb('DUE'::text)),"
                            + "'{installments,1,statusChanged}','true'::jsonb) "
                            + "where repayment_transaction_id = ?",
                    recorded.repaymentTransactionId()
            );

            BusinessStateConflictException rejected = assertThrows(
                    BusinessStateConflictException.class,
                    () -> repayments.record(new RecordRepaymentUseCase.Command(
                            requestId, fixture.applicationId(), paymentReference,
                            recorded.receivedAmount(), recorded.paymentValueDate()
                    ))
            );
            assertEquals("SYSTEM_STATE_CONFLICT", rejected.getErrorCode());
        } finally {
            jdbc.update(
                    "update repayment_operation_outcomes set outcome_json = cast(? as jsonb) "
                            + "where repayment_transaction_id = ?",
                    originalJson,
                    recorded.repaymentTransactionId()
            );
            jdbc.execute("alter table repayment_operation_outcomes enable trigger "
                    + "trg_repayment_operation_outcomes_immutable");
        }

        assertTrue(repayments.record(new RecordRepaymentUseCase.Command(
                requestId, fixture.applicationId(), paymentReference,
                recorded.receivedAmount(), recorded.paymentValueDate()
        )).idempotentReplay());
    }
    @Test
    void duplicateInstallmentTransitionForOneOperationAndItemIsRejected() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "DUP-HISTORY-" + fixture.token()
        ));
        UUID requestId = UUID.randomUUID();
        String paymentReference = "DUP-HISTORY-PAY-" + fixture.token();
        RecordRepaymentUseCase.Result result = repayments.record(
                new RecordRepaymentUseCase.Command(
                        requestId, fixture.applicationId(), paymentReference,
                        money("100"), VALUE_DATE
                )
        );
        assertEquals(1, count(
                "select count(*) from repayment_installment_status_transitions "
                        + "where operation_id = ?",
                result.repaymentTransactionId()
        ));

        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                transactionManager
        );
        assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status ->
                jdbc.update(
                        "insert into repayment_installment_status_transitions "
                                + "(id,repayment_schedule_item_id,sequence_number,operation_id,"
                                + "from_status,to_status,action,actor_type,actor_user_id,"
                                + "servicing_evaluation_date,occurred_at) "
                                + "select ?,repayment_schedule_item_id,sequence_number + 100,"
                                + "operation_id,from_status,to_status,action,actor_type,"
                                + "actor_user_id,servicing_evaluation_date,occurred_at "
                                + "from repayment_installment_status_transitions "
                                + "where operation_id = ?",
                        UUID.randomUUID(), result.repaymentTransactionId()
                )
        ));

        assertEquals(1, count(
                "select count(*) from repayment_installment_status_transitions "
                        + "where operation_id = ?",
                result.repaymentTransactionId()
        ));
        assertTrue(repayments.record(new RecordRepaymentUseCase.Command(
                requestId, fixture.applicationId(), paymentReference,
                result.receivedAmount(), result.paymentValueDate()
        )).idempotentReplay());
    }
    @Test
    void canonicalReferenceRaceAcrossIndependentAccountsHasOneCleanLoser()
            throws Exception {
        var firstFixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var secondFixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var firstActivation = disbursements.confirm(support.command(
                firstFixture, UUID.randomUUID(), "CROSS-A-" + firstFixture.token()
        ));
        var secondActivation = disbursements.confirm(support.command(
                secondFixture, UUID.randomUUID(), "CROSS-B-" + secondFixture.token()
        ));
        assertFalse(firstFixture.customerId().equals(secondFixture.customerId()));
        assertFalse(firstFixture.applicationId().equals(secondFixture.applicationId()));
        assertFalse(firstActivation.loanAccountId().equals(secondActivation.loanAccountId()));

        BigDecimal firstPaidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                firstActivation.loanAccountId()
        );
        BigDecimal secondPaidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                secondActivation.loanAccountId()
        );
        BigDecimal firstProgressPaidBefore = amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id = ?",
                firstActivation.loanAccountId()
        );
        BigDecimal secondProgressPaidBefore = amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id = ?",
                secondActivation.loanAccountId()
        );
        BigDecimal firstUsedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                firstFixture.limitId()
        );
        BigDecimal secondUsedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                secondFixture.limitId()
        );
        BigDecimal firstAvailableBefore = amount(
                "select available_amount from salary_advance_limits where id = ?",
                firstFixture.limitId()
        );
        BigDecimal secondAvailableBefore = amount(
                "select available_amount from salary_advance_limits where id = ?",
                secondFixture.limitId()
        );
        int firstInstallmentHistoryBefore = installmentHistoryCount(
                firstActivation.loanAccountId()
        );
        int secondInstallmentHistoryBefore = installmentHistoryCount(
                secondActivation.loanAccountId()
        );
        int firstAccountHistoryBefore = accountHistoryCount(firstActivation.loanAccountId());
        int secondAccountHistoryBefore = accountHistoryCount(secondActivation.loanAccountId());

        String sharedReference = "CROSS-ACCOUNT-" + firstFixture.token();
        RecordRepaymentUseCase.Command firstCommand = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), firstFixture.applicationId(), sharedReference,
                money("100"), VALUE_DATE
        );
        RecordRepaymentUseCase.Command secondCommand = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), secondFixture.applicationId(),
                " " + sharedReference.toLowerCase() + " ", money("100"), VALUE_DATE
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> recordOrConflict(
                    start, firstCommand
            ));
            Future<Object> second = executor.submit(() -> recordOrConflict(
                    start, secondCommand
            ));
            start.countDown();
            Object left = first.get(30, TimeUnit.SECONDS);
            Object right = second.get(30, TimeUnit.SECONDS);

            assertTrue(left instanceof RecordRepaymentUseCase.Result
                    ^ right instanceof RecordRepaymentUseCase.Result);
            RecordRepaymentUseCase.Result winner =
                    (RecordRepaymentUseCase.Result) (
                            left instanceof RecordRepaymentUseCase.Result ? left : right
                    );
            Object conflictValue = left instanceof BusinessStateConflictException
                    ? left : right;
            assertTrue(conflictValue instanceof BusinessStateConflictException);
            assertEquals("DUPLICATE_PAYMENT_REFERENCE",
                    ((BusinessStateConflictException) conflictValue).getErrorCode());
            assertFalse(winner.idempotentReplay());

            boolean firstWon = winner.loanApplicationId().equals(
                    firstFixture.applicationId()
            );
            var winnerFixture = firstWon ? firstFixture : secondFixture;
            var loserFixture = firstWon ? secondFixture : firstFixture;
            UUID winnerAccountId = firstWon
                    ? firstActivation.loanAccountId() : secondActivation.loanAccountId();
            UUID loserAccountId = firstWon
                    ? secondActivation.loanAccountId() : firstActivation.loanAccountId();
            BigDecimal winnerPaidBefore = firstWon ? firstPaidBefore : secondPaidBefore;
            BigDecimal loserPaidBefore = firstWon ? secondPaidBefore : firstPaidBefore;
            BigDecimal loserProgressPaidBefore = firstWon
                    ? secondProgressPaidBefore : firstProgressPaidBefore;
            BigDecimal winnerUsedBefore = firstWon ? firstUsedBefore : secondUsedBefore;
            BigDecimal loserUsedBefore = firstWon ? secondUsedBefore : firstUsedBefore;
            BigDecimal winnerAvailableBefore = firstWon
                    ? firstAvailableBefore : secondAvailableBefore;
            BigDecimal loserAvailableBefore = firstWon
                    ? secondAvailableBefore : firstAvailableBefore;
            int loserInstallmentHistoryBefore = firstWon
                    ? secondInstallmentHistoryBefore : firstInstallmentHistoryBefore;
            int loserAccountHistoryBefore = firstWon
                    ? secondAccountHistoryBefore : firstAccountHistoryBefore;

            assertEquals(1, count(
                    "select count(*) from repayment_transactions "
                            + "where loan_application_id = ?",
                    winnerFixture.applicationId()
            ));
            assertEquals(1, count(
                    "select count(*) from repayment_operation_outcomes "
                            + "where loan_application_id = ?",
                    winnerFixture.applicationId()
            ));
            assertEquals(1, count(
                    "select count(*) from audit_events audit "
                            + "join repayment_transactions transaction_row "
                            + "on transaction_row.id = audit.operation_id "
                            + "where transaction_row.loan_application_id = ? "
                            + "and audit.action = 'REPAYMENT_RECORDED'",
                    winnerFixture.applicationId()
            ));
            assertEquals(0, winnerPaidBefore.add(money("100")).compareTo(amount(
                    "select total_paid from loan_accounts where id = ?",
                    winnerAccountId
            )));
            assertEquals(0, winnerUsedBefore.subtract(
                    winner.principalAllocatedAndReleased()
            ).compareTo(amount(
                    "select used_amount from salary_advance_limits where id = ?",
                    winnerFixture.limitId()
            )));
            assertEquals(0, winnerAvailableBefore.add(
                    winner.principalAllocatedAndReleased()
            ).compareTo(amount(
                    "select available_amount from salary_advance_limits where id = ?",
                    winnerFixture.limitId()
            )));

            assertEquals(0, count(
                    "select count(*) from repayment_transactions "
                            + "where loan_application_id = ?",
                    loserFixture.applicationId()
            ));
            assertEquals(0, count(
                    "select count(*) from repayment_allocations allocation "
                            + "join repayment_transactions transaction_row "
                            + "on transaction_row.id = allocation.repayment_transaction_id "
                            + "where transaction_row.loan_application_id = ?",
                    loserFixture.applicationId()
            ));
            assertEquals(0, count(
                    "select count(*) from repayment_operation_outcomes "
                            + "where loan_application_id = ?",
                    loserFixture.applicationId()
            ));
            assertEquals(0, loserPaidBefore.compareTo(amount(
                    "select total_paid from loan_accounts where id = ?",
                    loserAccountId
            )));
            assertEquals(0, loserProgressPaidBefore.compareTo(amount(
                    "select sum(total_paid) from repayment_installment_progress "
                            + "where loan_account_id = ?",
                    loserAccountId
            )));
            assertEquals(loserInstallmentHistoryBefore,
                    installmentHistoryCount(loserAccountId));
            assertEquals(loserAccountHistoryBefore, accountHistoryCount(loserAccountId));
            assertEquals(0, count(
                    "select count(*) from salary_advance_limit_movements "
                            + "where loan_application_id = ? "
                            + "and movement_type = 'REPAID_RELEASED'",
                    loserFixture.applicationId()
            ));
            assertEquals(0, loserUsedBefore.compareTo(amount(
                    "select used_amount from salary_advance_limits where id = ?",
                    loserFixture.limitId()
            )));
            assertEquals(0, loserAvailableBefore.compareTo(amount(
                    "select available_amount from salary_advance_limits where id = ?",
                    loserFixture.limitId()
            )));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private int installmentHistoryCount(UUID loanAccountId) {
        return count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress "
                        + "on progress.repayment_schedule_item_id = "
                        + "history.repayment_schedule_item_id "
                        + "where progress.loan_account_id = ?",
                loanAccountId
        );
    }

    private int accountHistoryCount(UUID loanAccountId) {
        return count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id = ?",
                loanAccountId
        );
    }
    private Object recordOrConflict(
            CountDownLatch start,
            RecordRepaymentUseCase.Command command
    ) throws InterruptedException {
        start.await();
        try {
            return repayments.record(command);
        } catch (BusinessStateConflictException exception) {
            return exception;
        }
    }

    private void assertIdempotencyConflict(
            RecordRepaymentUseCase.Command command,
            String secretReference
    ) {
        BusinessStateConflictException conflict = assertThrows(
                BusinessStateConflictException.class,
                () -> repayments.record(command)
        );
        assertEquals("IDEMPOTENCY_KEY_REUSED", conflict.getErrorCode());
        assertFalse(conflict.getMessage().contains(secretReference));
    }
    private BigDecimal amount(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, BigDecimal.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock repaymentPostingClock() {
            return Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
