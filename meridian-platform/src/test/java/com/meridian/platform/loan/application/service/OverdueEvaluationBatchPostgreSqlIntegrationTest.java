package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.RunOverdueEvaluationBatchUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.OverdueEvaluationCandidateQuery;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(OverdueEvaluationBatchPostgreSqlIntegrationTest.ClockConfiguration.class)
class OverdueEvaluationBatchPostgreSqlIntegrationTest {

    private static final String SCHEMA = "overdue_batch_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDate TARGET = LocalDate.of(2026, 8, 29);

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired EvaluateLoanAccountOverdueUseCase evaluator;
    @Autowired RunOverdueEvaluationBatchUseCase batch;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired OverdueEvaluationCandidateQuery candidates;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoSpyBean BusinessAuditPublisher auditPublisher;
    @Autowired MutableClock clock;

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
        clock.set(Instant.parse("2026-07-28T10:00:00Z"));
        reset(auditPublisher);
        support = new ManualDisbursementActivationPostgreSqlTestSupport(jdbc, transactionManager);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "batch.operator@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse")
        ));
    }

    @Test
    void realBatchCommitsLaterCandidateAfterEarlierCommitTimeFailure() {
        proveCandidateMatrix();
        Activated failed = activate("FAILED");
        Activated successful = activate("SUCCESSFUL");
        evaluator.evaluate(command(successful, LocalDate.of(2026, 8, 27)));
        reset(auditPublisher);

        Evidence failedBefore = evidence(failed.accountId());
        Evidence successfulBefore = evidence(successful.accountId());
        AtomicBoolean injectedInsideTransaction = new AtomicBoolean();

        doAnswer(invocation -> {
            invocation.callRealMethod();
            BusinessAuditEvent event = invocation.getArgument(0);
            if (event.entries().stream().anyMatch(entry ->
                    entry.entityId().equals(failed.accountId()))) {
                injectedInsideTransaction.set(
                        TransactionSynchronizationManager.isActualTransactionActive());
                jdbc.update("update repayment_installment_progress set status='NOT_DUE' "
                                + "where loan_account_id=? and installment_number=1",
                        failed.accountId());
            }
            return null;
        }).when(target(auditPublisher)).publish(any());

        assertTrue(AopUtils.isAopProxy(evaluator));
        RunOverdueEvaluationBatchUseCase.Result result = batch.run(
                new RunOverdueEvaluationBatchUseCase.Command(
                        TARGET, LocalDateTime.of(2026, 8, 29, 0, 5), 10));

        assertEquals(2, result.candidates());
        assertEquals(1, result.evaluated());
        assertEquals(0, result.noOp());
        assertEquals(1, result.transitioned());
        assertEquals(1, result.failed());
        assertTrue(injectedInsideTransaction.get());

        assertEquals("OVERDUE", text("select status from loan_accounts where id=?",
                successful.accountId()));
        assertEquals(TARGET, date("select servicing_evaluation_date "
                + "from loan_accounts where id=?", successful.accountId()));
        assertEquals(List.of("OVERDUE", "NOT_DUE"), statuses(successful.accountId()));
        assertFinancialEvidenceUnchanged(successfulBefore, evidence(successful.accountId()));
        assertEquals(successfulBefore.installmentHistory() + 1,
                evidence(successful.accountId()).installmentHistory());
        assertEquals(successfulBefore.accountHistory() + 1,
                evidence(successful.accountId()).accountHistory());
        assertEquals(successfulBefore.audit() + 1,
                evidence(successful.accountId()).audit());

        Evidence failedAfter = evidence(failed.accountId());
        assertEquals(failedBefore, failedAfter);
        assertEquals("ACTIVE", text("select status from loan_accounts where id=?",
                failed.accountId()));
        assertEquals(LocalDate.of(2026, 7, 28), date(
                "select servicing_evaluation_date from loan_accounts where id=?",
                failed.accountId()));
        assertEquals(List.of("NOT_DUE", "NOT_DUE"), statuses(failed.accountId()));
    }

    private void proveCandidateMatrix() {
        Activated activeOne = activate("ACTIVE-ONE");
        Activated activeTwo = activate("ACTIVE-TWO");
        Activated overdue = activate("OVERDUE");
        Activated equalDate = activate("EQUAL");
        Activated futureDate = activate("FUTURE");
        Activated settled = activate("SETTLED");
        LocalDate target = LocalDate.of(2026, 8, 30);

        evaluator.evaluate(command(overdue, LocalDate.of(2026, 8, 29)));
        evaluator.evaluate(command(equalDate, target));
        evaluator.evaluate(command(futureDate, target.plusDays(1)));
        clock.set(Instant.parse("2026-08-29T10:00:00Z"));
        repayments.record(new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), settled.applicationId(),
                "CANDIDATE-SETTLED-" + UUID.randomUUID(),
                new BigDecimal("1100.00"), LocalDate.of(2026, 8, 29)));

        List<OverdueEvaluationCandidateQuery.Candidate> selected =
                candidates.findCandidates(target, 100);
        List<UUID> selectedKnown = selected.stream().map(
                        OverdueEvaluationCandidateQuery.Candidate::loanAccountId)
                .filter(id -> Set.of(activeOne.accountId(), activeTwo.accountId(),
                        overdue.accountId(), equalDate.accountId(), futureDate.accountId(),
                        settled.accountId()).contains(id))
                .toList();
        List<UUID> activeByUuid = java.util.stream.Stream.of(
                        activeOne.accountId(), activeTwo.accountId())
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        assertEquals(List.of(activeByUuid.get(0), activeByUuid.get(1), overdue.accountId()),
                selectedKnown);
        assertEquals(activeByUuid.getFirst(),
                candidates.findCandidates(target, 1).getFirst().loanAccountId());
        assertFalse(selectedKnown.contains(settled.accountId()));
        assertFalse(selectedKnown.contains(equalDate.accountId()));
        assertFalse(selectedKnown.contains(futureDate.accountId()));

        Activated closed = activate("CLOSED");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("update loan_accounts set status='CLOSED' where id=?",
                    closed.accountId());
            assertFalse(candidates.findCandidates(target, 100).stream().anyMatch(candidate ->
                    candidate.loanAccountId().equals(closed.accountId())));
            status.setRollbackOnly();
        });

        evaluator.evaluate(command(activeOne, TARGET));
        evaluator.evaluate(command(activeTwo, TARGET));
        evaluator.evaluate(command(closed, TARGET));
        clock.set(Instant.parse("2026-07-28T10:00:00Z"));
    }

    private Activated activate(String token) {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var result = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "BATCH-" + token + "-" + fixture.token()));
        reset(auditPublisher);
        return new Activated(fixture.applicationId(), result.loanAccountId());
    }

    private EvaluateLoanAccountOverdueUseCase.Command command(
            Activated activated, LocalDate date
    ) {
        return new EvaluateLoanAccountOverdueUseCase.Command(
                activated.applicationId(), activated.accountId(), date,
                LocalDateTime.of(date, java.time.LocalTime.of(0, 5)));
    }

    private Evidence evidence(UUID accountId) {
        UUID applicationId = jdbc.queryForObject(
                "select loan_application_id from loan_accounts where id=?",
                UUID.class, accountId);
        Map<String, Object> account = jdbc.queryForMap(
                "select status,servicing_evaluation_date,principal_paid,interest_paid,"
                        + "fee_paid,total_paid,principal_outstanding,interest_outstanding,"
                        + "fee_outstanding,total_outstanding,last_payment_value_date,"
                        + "last_payment_recorded_at,updated_at from loan_accounts where id=?",
                accountId);
        List<Map<String, Object>> progress = jdbc.queryForList(
                "select installment_number,status,servicing_evaluation_date,principal_paid,"
                        + "interest_paid,fee_paid,total_paid,principal_outstanding,"
                        + "interest_outstanding,fee_outstanding,total_outstanding,"
                        + "last_payment_value_date,last_payment_recorded_at,updated_at "
                        + "from repayment_installment_progress where loan_account_id=? "
                        + "order by installment_number", accountId);
        Map<String, Object> limit = jdbc.queryForMap(
                "select total_limit,available_amount,reserved_amount,used_amount,updated_at "
                        + "from salary_advance_limits where id=(select salary_advance_limit_id "
                        + "from salary_advance_limit_movements where loan_application_id=? "
                        + "order by created_at limit 1)", applicationId);
        List<Map<String, Object>> movements = jdbc.queryForList(
                "select movement_type,amount,loan_account_id,repayment_transaction_id,"
                        + "occurred_at,created_at from salary_advance_limit_movements "
                        + "where loan_application_id=? order by created_at,id", applicationId);
        return new Evidence(account, progress, limit, movements,
                count("select count(*) from repayment_transactions where loan_account_id=?",
                        accountId),
                count("select count(*) from repayment_allocations allocation join "
                                + "repayment_transactions transaction_row on "
                                + "transaction_row.id=allocation.repayment_transaction_id "
                                + "where transaction_row.loan_account_id=?", accountId),
                count("select count(*) from repayment_installment_status_transitions history "
                                + "join repayment_installment_progress progress on "
                                + "progress.repayment_schedule_item_id="
                                + "history.repayment_schedule_item_id "
                                + "where progress.loan_account_id=?", accountId),
                count("select count(*) from loan_account_status_transitions "
                        + "where loan_account_id=?", accountId),
                count("select count(*) from audit_events where entity_type='LOAN_ACCOUNT' "
                        + "and entity_id=?", accountId));
    }

    private void assertFinancialEvidenceUnchanged(Evidence before, Evidence after) {
        List<String> financialKeys = List.of(
                "principal_paid", "interest_paid", "fee_paid", "total_paid",
                "principal_outstanding", "interest_outstanding", "fee_outstanding",
                "total_outstanding", "last_payment_value_date", "last_payment_recorded_at");
        financialKeys.forEach(key -> assertEquals(
                before.account().get(key), after.account().get(key), key));
        assertEquals(before.progress().size(), after.progress().size());
        for (int index = 0; index < before.progress().size(); index++) {
            int current = index;
            financialKeys.forEach(key -> assertEquals(
                    before.progress().get(current).get(key),
                    after.progress().get(current).get(key),
                    "installment " + current + " " + key));
        }
        assertEquals(before.limit(), after.limit());
        assertEquals(before.movements(), after.movements());
        assertEquals(before.repayments(), after.repayments());
        assertEquals(before.allocations(), after.allocations());
    }

    private List<String> statuses(UUID accountId) {
        return jdbc.queryForList("select status from repayment_installment_progress "
                        + "where loan_account_id=? order by installment_number",
                String.class, accountId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private LocalDate date(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, LocalDate.class, arguments);
    }

    private static <T> T target(T proxiedSpy) {
        return AopTestUtils.getUltimateTargetObject(proxiedSpy);
    }

    private record Activated(UUID applicationId, UUID accountId) {
    }

    private record Evidence(
            Map<String, Object> account,
            List<Map<String, Object>> progress,
            Map<String, Object> limit,
            List<Map<String, Object>> movements,
            int repayments,
            int allocations,
            int installmentHistory,
            int accountHistory,
            int audit
    ) {
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock batchClock() {
            return new MutableClock(Instant.parse("2026-07-28T10:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            current.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
