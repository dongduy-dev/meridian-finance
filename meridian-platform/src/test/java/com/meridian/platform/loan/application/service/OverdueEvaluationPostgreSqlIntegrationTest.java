package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.OverdueEvaluationCandidateQuery;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.util.concurrent.atomic.AtomicReference;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(OverdueEvaluationPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class OverdueEvaluationPostgreSqlIntegrationTest {

    private static final String SCHEMA = "overdue_evaluation_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired EvaluateLoanAccountOverdueUseCase evaluator;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired OverdueEvaluationCandidateQuery candidates;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoSpyBean BusinessAuditPublisher auditPublisher;
    @MockitoSpyBean RepaymentInstallmentProgressRepository progressRepository;
    @MockitoSpyBean RepaymentInstallmentStatusTransitionRepository installmentHistory;
    @MockitoSpyBean LoanAccountRepository accountRepository;
    @MockitoSpyBean LoanAccountStatusTransitionRepository accountHistory;
    @MockitoSpyBean LoanApplicationRepository applications;
    @Autowired MutableTestClock testClock;

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
        testClock.set(Instant.parse("2026-07-28T10:00:00Z"));
        reset(auditPublisher, progressRepository, installmentHistory, accountRepository,
                accountHistory, applications);
        support = new ManualDisbursementActivationPostgreSqlTestSupport(jdbc, transactionManager);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "overdue.operator@meridian.test", "STAFF",
                null, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse")
        ));
    }

    @Test
    void advancesDatesAndRealStatusesAtomicallyWithoutChangingBalances() {
        var activated = activate();
        UUID accountId = activated.accountId();
        BigDecimal balanceBefore = money(
                "select total_outstanding from loan_accounts where id = ?", accountId);
        int accountHistoryBefore = count(
                "select count(*) from loan_account_status_transitions where loan_account_id = ?",
                accountId);
        int auditBefore = count(
                "select count(*) from audit_events where entity_type = 'LOAN_ACCOUNT' and entity_id = ?",
                accountId);

        EvaluateLoanAccountOverdueUseCase.Result due = evaluate(
                activated.applicationId(), accountId, LocalDate.of(2026, 8, 28));
        assertFalse(due.noOp());
        assertFalse(due.accountStatusChanged());
        assertEquals(List.of("DUE", "NOT_DUE"), installmentStatuses(accountId));
        assertEquals(accountHistoryBefore, count(
                "select count(*) from loan_account_status_transitions where loan_account_id = ?",
                accountId));
        assertEquals(auditBefore, count(
                "select count(*) from audit_events where entity_type = 'LOAN_ACCOUNT' and entity_id = ?",
                accountId));

        EvaluateLoanAccountOverdueUseCase.Result overdue = evaluate(
                activated.applicationId(), accountId, LocalDate.of(2026, 8, 29));
        assertTrue(overdue.accountStatusChanged());
        assertEquals("OVERDUE", text("select status from loan_accounts where id = ?", accountId));
        assertEquals(List.of("OVERDUE", "NOT_DUE"), installmentStatuses(accountId));
        assertEquals(0, balanceBefore.compareTo(money(
                "select total_outstanding from loan_accounts where id = ?", accountId)));
        assertEquals(accountHistoryBefore + 1, count(
                "select count(*) from loan_account_status_transitions where loan_account_id = ?",
                accountId));
        assertEquals(auditBefore + 1, count(
                "select count(*) from audit_events where entity_type = 'LOAN_ACCOUNT' and entity_id = ?",
                accountId));

        int installmentHistory = count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress on "
                        + "progress.repayment_schedule_item_id=history.repayment_schedule_item_id "
                        + "where progress.loan_account_id = ?", accountId);
        EvaluateLoanAccountOverdueUseCase.Result replay = evaluate(
                activated.applicationId(), accountId, LocalDate.of(2026, 8, 29));
        assertTrue(replay.noOp());
        assertEquals(installmentHistory, count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress on "
                        + "progress.repayment_schedule_item_id=history.repayment_schedule_item_id "
                        + "where progress.loan_account_id = ?", accountId));
    }

    @Test
    void candidateSelectionIsBoundedDeterministicAndExcludesAlreadyEvaluatedAccount() {
        var first = activate();
        var second = activate();
        LocalDate target = LocalDate.of(2026, 8, 29);
        List<OverdueEvaluationCandidateQuery.Candidate> selected = candidates.findCandidates(target, 1);
        assertEquals(1, selected.size());
        assertTrue(first.accountId() != null && second.accountId() != null);

        var chosen = selected.getFirst();
        evaluate(chosen.loanApplicationId(), chosen.loanAccountId(), target);
        List<OverdueEvaluationCandidateQuery.Candidate> remaining = candidates.findCandidates(target, 10);
        assertFalse(remaining.stream().anyMatch(candidate ->
                candidate.loanAccountId().equals(chosen.loanAccountId())));
    }

    @Test
    void secondEvaluatorWaitsForWorkflowLockThenReReadsAndNoOps() throws Exception {
        var activated = activate();
        LocalDate target = LocalDate.of(2026, 8, 29);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondHasLock = new CountDownLatch(1);
        installWorkflowBarrier(activated.applicationId(), "first-evaluator",
                firstHasLock, releaseFirst, "second-evaluator", secondHasLock);
        BigDecimal balanceBefore = money(
                "select total_outstanding from loan_accounts where id=?", activated.accountId());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<EvaluateLoanAccountOverdueUseCase.Result> first = pool.submit(() -> {
                Thread.currentThread().setName("first-evaluator");
                return evaluate(activated.applicationId(), activated.accountId(), target);
            });
            assertTrue(firstHasLock.await(5, TimeUnit.SECONDS));
            Future<EvaluateLoanAccountOverdueUseCase.Result> second = pool.submit(() -> {
                Thread.currentThread().setName("second-evaluator");
                return evaluate(activated.applicationId(), activated.accountId(), target);
            });
            assertFalse(secondHasLock.await(300, TimeUnit.MILLISECONDS));
            assertFalse(second.isDone());
            releaseFirst.countDown();

            assertFalse(first.get(15, TimeUnit.SECONDS).noOp());
            assertTrue(second.get(15, TimeUnit.SECONDS).noOp());
            assertTrue(secondHasLock.getCount() == 0);
            assertEquals(1, count(
                    "select count(*) from loan_account_status_transitions "
                            + "where loan_account_id = ? and action = 'OVERDUE_EVALUATED'",
                    activated.accountId()));
            assertEquals(1, count(
                    "select count(*) from repayment_installment_status_transitions history "
                            + "join repayment_installment_progress progress on "
                            + "progress.repayment_schedule_item_id="
                            + "history.repayment_schedule_item_id where "
                            + "progress.loan_account_id=? and history.action='OVERDUE_EVALUATED'",
                    activated.accountId()));
            assertEquals(1, count(
                    "select count(*) from audit_events where entity_type='LOAN_ACCOUNT' "
                            + "and entity_id=? and action='LOAN_ACCOUNT_STATUS_CHANGED'",
                    activated.accountId()));
            assertEquals(0, balanceBefore.compareTo(money(
                    "select total_outstanding from loan_accounts where id=?",
                    activated.accountId())));
            assertContiguousSequences(activated.accountId());
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void repaymentCommitsFirstAndEvaluatorUsesCommittedPartialPaymentState() throws Exception {
        var activated = activate();
        testClock.set(Instant.parse("2026-08-28T10:00:00Z"));
        CountDownLatch repaymentHasLock = new CountDownLatch(1);
        CountDownLatch releaseRepayment = new CountDownLatch(1);
        CountDownLatch evaluatorHasLock = new CountDownLatch(1);
        installWorkflowBarrier(activated.applicationId(), "repayment-first",
                repaymentHasLock, releaseRepayment, "repayment-first-evaluator",
                evaluatorHasLock);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RecordRepaymentUseCase.Result> repayment = pool.submit(() -> {
                Thread.currentThread().setName("repayment-first");
                return repayments.record(new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), activated.applicationId(),
                        "REPAYMENT-FIRST-" + UUID.randomUUID(),
                        new BigDecimal("100.00"), LocalDate.of(2026, 8, 28)));
            });
            assertTrue(repaymentHasLock.await(5, TimeUnit.SECONDS));
            Future<EvaluateLoanAccountOverdueUseCase.Result> evaluation = pool.submit(() -> {
                Thread.currentThread().setName("repayment-first-evaluator");
                return evaluate(activated.applicationId(), activated.accountId(),
                        LocalDate.of(2026, 8, 29));
            });
            assertFalse(evaluatorHasLock.await(300, TimeUnit.MILLISECONDS));
            assertFalse(evaluation.isDone());
            releaseRepayment.countDown();

            RecordRepaymentUseCase.Result repaymentResult =
                    repayment.get(15, TimeUnit.SECONDS);
            EvaluateLoanAccountOverdueUseCase.Result evaluationResult =
                    evaluation.get(15, TimeUnit.SECONDS);
            assertFalse(evaluationResult.noOp());
            assertEquals("OVERDUE", text(
                    "select status from loan_accounts where id=?", activated.accountId()));
            assertEquals(List.of("OVERDUE", "NOT_DUE"),
                    installmentStatuses(activated.accountId()));
            assertEquals(0, repaymentResult.accountBalance().totalOutstanding().compareTo(
                    money("select total_outstanding from loan_accounts where id=?",
                            activated.accountId())));
            assertEquals(1, count(
                    "select count(*) from repayment_transactions where loan_account_id = ?",
                    activated.accountId()));
            assertEquals(1, count("select count(*) from audit_events where entity_type="
                    + "'LOAN_ACCOUNT' and entity_id=? and action="
                    + "'LOAN_ACCOUNT_STATUS_CHANGED'", activated.accountId()));
            assertContiguousSequences(activated.accountId());
        } finally {
            releaseRepayment.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.00", "550.00", "1100.00"})
    void evaluationCommitsFirstAndRepaymentUsesEvaluatedState(String amount) throws Exception {
        var activated = activate();
        testClock.set(Instant.parse("2026-08-29T10:00:00Z"));
        CountDownLatch evaluatorHasLock = new CountDownLatch(1);
        CountDownLatch releaseEvaluator = new CountDownLatch(1);
        CountDownLatch repaymentHasLock = new CountDownLatch(1);
        installWorkflowBarrier(activated.applicationId(), "evaluation-first",
                evaluatorHasLock, releaseEvaluator, "evaluation-first-repayment",
                repaymentHasLock);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<EvaluateLoanAccountOverdueUseCase.Result> evaluation = pool.submit(() -> {
                Thread.currentThread().setName("evaluation-first");
                return evaluate(activated.applicationId(), activated.accountId(),
                        LocalDate.of(2026, 8, 29));
            });
            assertTrue(evaluatorHasLock.await(5, TimeUnit.SECONDS));
            RecordRepaymentUseCase.Command repaymentCommand =
                    new RecordRepaymentUseCase.Command(
                            UUID.randomUUID(), activated.applicationId(),
                            "EVALUATION-FIRST-" + amount + "-" + UUID.randomUUID(),
                            new BigDecimal(amount), LocalDate.of(2026, 8, 29));
            Future<RecordRepaymentUseCase.Result> repayment = pool.submit(() -> {
                Thread.currentThread().setName("evaluation-first-repayment");
                return repayments.record(repaymentCommand);
            });
            assertFalse(repaymentHasLock.await(300, TimeUnit.MILLISECONDS));
            assertFalse(repayment.isDone());
            releaseEvaluator.countDown();

            EvaluateLoanAccountOverdueUseCase.Result evaluationResult =
                    evaluation.get(15, TimeUnit.SECONDS);
            RecordRepaymentUseCase.Result repaymentResult =
                    repayment.get(15, TimeUnit.SECONDS);
            assertFalse(evaluationResult.noOp());
            String expectedStatus = amount.startsWith("100.") ? "OVERDUE"
                    : amount.startsWith("550.") ? "ACTIVE" : "SETTLED";
            BigDecimal expectedRelease = amount.startsWith("100.")
                    ? new BigDecimal("50.00")
                    : amount.startsWith("550.")
                    ? new BigDecimal("500.00") : new BigDecimal("1000.00");
            assertEquals(expectedStatus, text(
                    "select status from loan_accounts where id=?", activated.accountId()));
            assertEquals(0, expectedRelease.compareTo(
                    repaymentResult.principalAllocatedAndReleased()));
            assertEquals(0, new BigDecimal(amount).compareTo(money(
                    "select sum(amount) from repayment_allocations "
                            + "where repayment_transaction_id=?",
                    repaymentResult.repaymentTransactionId())));
            assertEquals(1, count("select count(*) from loan_account_status_transitions "
                    + "where loan_account_id=? and action='OVERDUE_EVALUATED'",
                    activated.accountId()));
            int expectedAccountAudits = amount.startsWith("100.") ? 1 : 2;
            assertEquals(expectedAccountAudits, count(
                    "select count(*) from audit_events where entity_type='LOAN_ACCOUNT' "
                            + "and entity_id=? and action='LOAN_ACCOUNT_STATUS_CHANGED'",
                    activated.accountId()));
            assertEquals(1, count("select count(*) from salary_advance_limit_movements "
                    + "where repayment_transaction_id=? and movement_type='REPAID_RELEASED'",
                    repaymentResult.repaymentTransactionId()));
            assertContiguousSequences(activated.accountId());
        } finally {
            releaseEvaluator.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
    @ParameterizedTest
    @EnumSource(PersistenceFailure.class)
    void persistenceFailureRollsBackEveryEvaluationEffect(PersistenceFailure failure) {
        var activated = activate();
        int installmentHistoryBefore = count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress on "
                        + "progress.repayment_schedule_item_id=history.repayment_schedule_item_id "
                        + "where progress.loan_account_id=?", activated.accountId());
        int accountHistoryBefore = count(
                "select count(*) from loan_account_status_transitions where loan_account_id=?",
                activated.accountId());
        RuntimeException injected = new IllegalStateException("injected persistence failure");
        switch (failure) {
            case PROGRESS_UPDATE -> doThrow(injected).when(target(progressRepository)).saveAll(any());
            case INSTALLMENT_HISTORY -> doThrow(injected).when(target(installmentHistory)).save(any());
            case ACCOUNT_UPDATE -> doThrow(injected).when(target(accountRepository)).updateServicingState(any());
            case ACCOUNT_HISTORY -> doThrow(injected).when(target(accountHistory)).save(any());
        }

        assertThrows(RuntimeException.class, () -> evaluate(
                activated.applicationId(), activated.accountId(), LocalDate.of(2026, 8, 29)));

        assertEquals("ACTIVE", text(
                "select status from loan_accounts where id=?", activated.accountId()));
        assertEquals(LocalDate.of(2026, 7, 28), jdbc.queryForObject(
                "select servicing_evaluation_date from loan_accounts where id=?",
                LocalDate.class, activated.accountId()));
        assertEquals(List.of("NOT_DUE", "NOT_DUE"), installmentStatuses(activated.accountId()));
        assertEquals(installmentHistoryBefore, count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress on "
                        + "progress.repayment_schedule_item_id=history.repayment_schedule_item_id "
                        + "where progress.loan_account_id=?", activated.accountId()));
        assertEquals(accountHistoryBefore, count(
                "select count(*) from loan_account_status_transitions where loan_account_id=?",
                activated.accountId()));
    }
    @Test
    void auditFailureRollsBackDatesStatusesAndHistories() {
        var activated = activate();
        UUID accountId = activated.accountId();
        int historiesBefore = count(
                "select count(*) from loan_account_status_transitions where loan_account_id = ?",
                accountId);
        doThrow(new IllegalStateException("injected overdue audit failure"))
                .when(auditPublisher).publish(any());

        assertThrows(RuntimeException.class, () -> evaluate(
                activated.applicationId(), accountId, LocalDate.of(2026, 8, 29)));

        assertEquals("ACTIVE", text("select status from loan_accounts where id = ?", accountId));
        assertEquals(LocalDate.of(2026, 7, 28), jdbc.queryForObject(
                "select servicing_evaluation_date from loan_accounts where id = ?",
                LocalDate.class, accountId));
        assertEquals(List.of("NOT_DUE", "NOT_DUE"), installmentStatuses(accountId));
        assertEquals(historiesBefore, count(
                "select count(*) from loan_account_status_transitions where loan_account_id = ?",
                accountId));
    }

    @Test
    void repaymentReplayReturnsV34OutcomeAfterLaterOverdueAdvancement() {
        var activated = activate();
        testClock.set(Instant.parse("2026-08-28T10:00:00Z"));
        RecordRepaymentUseCase.Command command = new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), activated.applicationId(),
                "REPLAY-AFTER-OVERDUE-" + UUID.randomUUID(),
                new BigDecimal("100.00"), LocalDate.of(2026, 8, 28));
        RecordRepaymentUseCase.Result original = repayments.record(command);

        evaluate(activated.applicationId(), activated.accountId(),
                LocalDate.of(2026, 8, 29));
        List<String> statusesBeforeReplay = installmentStatuses(activated.accountId());
        String accountStatusBeforeReplay = text(
                "select status from loan_accounts where id=?", activated.accountId());
        LocalDate evaluationDateBeforeReplay = jdbc.queryForObject(
                "select servicing_evaluation_date from loan_accounts where id=?",
                LocalDate.class, activated.accountId());
        int transactionsBefore = count(
                "select count(*) from repayment_transactions where loan_account_id=?",
                activated.accountId());
        int allocationsBefore = count(
                "select count(*) from repayment_allocations where repayment_transaction_id=?",
                original.repaymentTransactionId());
        int movementsBefore = count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id=?",
                original.repaymentTransactionId());
        int outcomesBefore = count(
                "select count(*) from repayment_operation_outcomes "
                        + "where repayment_transaction_id=?",
                original.repaymentTransactionId());
        int historiesBefore = count(
                "select count(*) from loan_account_status_transitions where loan_account_id=?",
                activated.accountId()) + count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress on "
                        + "progress.repayment_schedule_item_id="
                        + "history.repayment_schedule_item_id "
                        + "where progress.loan_account_id=?", activated.accountId());
        int auditsBefore = count(
                "select count(*) from audit_events where operation_id=?",
                original.repaymentTransactionId());

        RecordRepaymentUseCase.Result replay = repayments.record(command);

        assertTrue(replay.idempotentReplay());
        assertEquals(original.repaymentTransactionId(), replay.repaymentTransactionId());
        assertEquals(original.allocations(), replay.allocations());
        assertEquals(original.installmentProgress(), replay.installmentProgress());
        assertEquals(original.accountBalance(), replay.accountBalance());
        assertEquals(original.principalAllocatedAndReleased(),
                replay.principalAllocatedAndReleased());
        assertEquals(transactionsBefore, count(
                "select count(*) from repayment_transactions where loan_account_id=?",
                activated.accountId()));
        assertEquals(allocationsBefore, count(
                "select count(*) from repayment_allocations where repayment_transaction_id=?",
                original.repaymentTransactionId()));
        assertEquals(movementsBefore, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id=?",
                original.repaymentTransactionId()));
        assertEquals(outcomesBefore, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where repayment_transaction_id=?",
                original.repaymentTransactionId()));
        assertEquals(historiesBefore, count(
                "select count(*) from loan_account_status_transitions where loan_account_id=?",
                activated.accountId()) + count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress on "
                        + "progress.repayment_schedule_item_id="
                        + "history.repayment_schedule_item_id "
                        + "where progress.loan_account_id=?", activated.accountId()));
        assertEquals(auditsBefore, count(
                "select count(*) from audit_events where operation_id=?",
                original.repaymentTransactionId()));
        assertEquals(statusesBeforeReplay, installmentStatuses(activated.accountId()));
        assertEquals(accountStatusBeforeReplay, text(
                "select status from loan_accounts where id=?", activated.accountId()));
        assertEquals(evaluationDateBeforeReplay, jdbc.queryForObject(
                "select servicing_evaluation_date from loan_accounts where id=?",
                LocalDate.class, activated.accountId()));
    }

    private Activated activate() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "OVERDUE-" + fixture.token()));
        reset(auditPublisher, progressRepository, installmentHistory, accountRepository,
                accountHistory, applications);
        return new Activated(fixture.applicationId(), activation.loanAccountId());
    }

    private EvaluateLoanAccountOverdueUseCase.Result evaluate(
            UUID applicationId, UUID accountId, LocalDate date
    ) {
        return evaluator.evaluate(new EvaluateLoanAccountOverdueUseCase.Command(
                applicationId, accountId, date, LocalDateTime.of(date, java.time.LocalTime.of(0, 5))));
    }

    private List<String> installmentStatuses(UUID accountId) {
        return jdbc.queryForList(
                "select status from repayment_installment_progress "
                        + "where loan_account_id = ? order by installment_number",
                String.class, accountId);
    }

    private void installWorkflowBarrier(
            UUID applicationId,
            String holdingThread,
            CountDownLatch holdingThreadHasLock,
            CountDownLatch releaseHoldingThread,
            String waitingThread,
            CountDownLatch waitingThreadHasLock
    ) {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            UUID lockedApplicationId = invocation.getArgument(0);
            if (!applicationId.equals(lockedApplicationId)) {
                return null;
            }
            String threadName = Thread.currentThread().getName();
            if (holdingThread.equals(threadName)) {
                holdingThreadHasLock.countDown();
                if (!releaseHoldingThread.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out holding workflow lock.");
                }
            } else if (waitingThread.equals(threadName)) {
                waitingThreadHasLock.countDown();
            }
            return null;
        }).when(target(applications)).acquireWorkflowLock(any());
    }

    private void assertContiguousSequences(UUID accountId) {
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
                "select count(*)=max(sequence_number) and min(sequence_number)=1 "
                        + "from loan_account_status_transitions where loan_account_id=?",
                Boolean.class, accountId)));
        assertEquals(0, count(
                "select count(*) from (select history.repayment_schedule_item_id from "
                        + "repayment_installment_status_transitions history join "
                        + "repayment_installment_progress progress on "
                        + "progress.repayment_schedule_item_id="
                        + "history.repayment_schedule_item_id "
                        + "where progress.loan_account_id=? group by "
                        + "history.repayment_schedule_item_id having "
                        + "min(history.sequence_number)<>1 "
                        + "or count(*)<>max(history.sequence_number)) gap",
                accountId));
    }

    private static <T> T target(T proxiedSpy) {
        return AopTestUtils.getUltimateTargetObject(proxiedSpy);
    }
    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private BigDecimal money(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private enum PersistenceFailure {
        PROGRESS_UPDATE,
        INSTALLMENT_HISTORY,
        ACCOUNT_UPDATE,
        ACCOUNT_HISTORY
    }
    private record Activated(UUID applicationId, UUID accountId) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        MutableTestClock overdueTestClock() {
            return new MutableTestClock(Instant.parse("2026-07-28T10:00:00Z"));
        }
    }

    static class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableTestClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported in this test Clock.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
