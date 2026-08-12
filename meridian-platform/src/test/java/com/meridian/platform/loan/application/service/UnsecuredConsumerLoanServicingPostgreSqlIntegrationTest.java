package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.OverdueEvaluationCandidateQuery;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.FIRST_REPAYMENT_DATE;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.VALUE_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(UnsecuredConsumerLoanServicingPostgreSqlIntegrationTest.ClockConfiguration.class)
class UnsecuredConsumerLoanServicingPostgreSqlIntegrationTest {

    private static final String SCHEMA = "ucl_servicing_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID APPROVER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000303"
    );

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired QueryRepaymentsUseCase repaymentQueries;
    @Autowired EvaluateLoanAccountOverdueUseCase overdueEvaluator;
    @Autowired OverdueEvaluationCandidateQuery overdueCandidates;
    @Autowired ApproveLoanSettlementUseCase settlements;
    @Autowired CloseLoanAccountUseCase closures;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoSpyBean BusinessAuditPublisher auditPublisher;

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
        support = new ManualDisbursementActivationPostgreSqlTestSupport(
                jdbc, transactionManager
        );
        accountingActor();
    }

    @Test
    void ordinaryRepaymentUsesMonthlyScheduleCuresOverdueAndClosesAtPayoff() {
        Activated activated = activateUcl();
        RecordRepaymentUseCase.Command earlyCommand = repaymentCommand(
                activated, UUID.randomUUID(), "UCL-EARLY-" + activated.token(),
                "100", VALUE_DATE
        );

        RecordRepaymentUseCase.Result early = repayments.record(earlyCommand);

        assertMoney("50", early.principalAllocated());
        assertMoney("0", early.principalReleased());
        assertEquals(LoanAccountStatus.ACTIVE, early.accountBalance().status());
        assertEquals("MONTHLY_INSTALLMENT", text(
                "select contract.repayment_method from loan_contracts contract "
                        + "join loan_accounts account on account.loan_contract_id = contract.id "
                        + "where account.id = ?",
                activated.accountId()
        ));
        assertEquals(0, salaryMovements(activated.applicationId()));
        assertTrue(repayments.record(earlyCommand).idempotentReplay());

        QueryRepaymentsUseCase.Item read = repaymentQueries.query(
                activated.applicationId(), 0, 20
        ).items().getFirst();
        assertMoney("50", read.principalAllocated());
        assertMoney("0", read.principalReleased());

        LocalDate overdueDate = FIRST_REPAYMENT_DATE.plusDays(1);
        assertTrue(overdueCandidates.findCandidates(overdueDate, 100).stream()
                .anyMatch(candidate -> candidate.loanAccountId().equals(activated.accountId())));
        EvaluateLoanAccountOverdueUseCase.Result overdue = overdueEvaluator.evaluate(
                new EvaluateLoanAccountOverdueUseCase.Command(
                        activated.applicationId(), activated.accountId(), overdueDate,
                        overdueDate.atStartOfDay()
                )
        );
        assertEquals(LoanAccountStatus.OVERDUE, overdue.resultingStatus());

        clock.set(overdueDate.atTime(10, 0).toInstant(ZoneOffset.UTC));
        RecordRepaymentUseCase.Result cure = repayments.record(repaymentCommand(
                activated, UUID.randomUUID(), "UCL-CURE-" + activated.token(),
                "450", overdueDate
        ));
        assertEquals(LoanAccountStatus.ACTIVE, cure.accountBalance().status());
        assertMoney("450", cure.principalAllocated());
        assertMoney("0", cure.principalReleased());

        RecordRepaymentUseCase.Result payoff = repayments.record(repaymentCommand(
                activated, UUID.randomUUID(), "UCL-PAYOFF-" + activated.token(),
                "550", overdueDate
        ));
        assertEquals(LoanAccountStatus.SETTLED, payoff.accountBalance().status());
        assertMoney("500", payoff.principalAllocated());
        assertMoney("0", payoff.principalReleased());
        assertMoney("0", payoff.accountBalance().totalOutstanding());
        assertEquals(2, count(
                "select count(distinct item.installment_number) "
                        + "from repayment_allocations allocation "
                        + "join repayment_transactions transaction_row "
                        + "on transaction_row.id = allocation.repayment_transaction_id "
                        + "join repayment_schedule_items item "
                        + "on item.id = allocation.repayment_schedule_item_id "
                        + "where transaction_row.loan_account_id = ?",
                activated.accountId()
        ));

        accountingActor();
        UUID closureRequestId = UUID.randomUUID();
        CloseLoanAccountUseCase.Result closed = closures.close(
                new CloseLoanAccountUseCase.Command(
                        closureRequestId, activated.applicationId()
                )
        );
        assertEquals(LoanAccountStatus.CLOSED, closed.resultingStatus());
        assertTrue(closures.close(new CloseLoanAccountUseCase.Command(
                closureRequestId, activated.applicationId()
        )).idempotentReplay());
        assertEquals("DISBURSED", text(
                "select status from loan_applications where id = ?",
                activated.applicationId()
        ));
        assertEquals(2, count(
                "select count(*) from repayment_installment_progress "
                        + "where loan_account_id = ? and status = 'PAID'",
                activated.accountId()
        ));
        assertMoney("0", amount(
                "select sum(principal_released) from repayment_operation_outcomes "
                        + "where loan_account_id = ?",
                activated.accountId()
        ));
        assertEquals(0, salaryMovements(activated.applicationId()));
    }

    @Test
    void exactAdministrativeSettlementReplaysAndSupportsClosure() {
        Activated activated = activateUcl();
        repayments.record(repaymentCommand(
                activated, UUID.randomUUID(), "UCL-PARTIAL-" + activated.token(),
                "100", VALUE_DATE
        ));
        approverActor();
        assertEquals("SETTLEMENT_AMOUNT_INVALID", assertThrows(
                BusinessRuleViolationException.class,
                () -> settlements.approve(settlementCommand(
                        activated, UUID.randomUUID(), "999",
                        "UCL-SETTLE-WRONG-" + activated.token()
                ))
        ).getErrorCode());

        UUID requestId = UUID.randomUUID();
        ApproveLoanSettlementUseCase.Command command = settlementCommand(
                activated, requestId, "1000",
                "UCL-SETTLE-" + activated.token()
        );
        ApproveLoanSettlementUseCase.Result settled = settlements.approve(command);

        assertEquals(LoanAccountStatus.SETTLED, settled.accountBalance().status());
        assertMoney("950", settled.principalAllocated());
        assertMoney("0", settled.principalReleased());
        assertTrue(settlements.approve(command).idempotentReplay());
        assertEquals(1, count(
                "select count(*) from approved_loan_settlements "
                        + "where loan_account_id = ?",
                activated.accountId()
        ));
        assertEquals(0, salaryMovements(activated.applicationId()));

        accountingActor();
        CloseLoanAccountUseCase.Result closed = closures.close(
                new CloseLoanAccountUseCase.Command(
                        UUID.randomUUID(), activated.applicationId()
                )
        );
        assertEquals(LoanAccountStatus.CLOSED, closed.resultingStatus());
    }

    @Test
    void concurrentSettlementHasOneWinnerAndAuditFailureRollsBackRepayment() throws Exception {
        Activated concurrent = activateUcl();
        approverActor();
        var first = settlementCommand(
                concurrent, UUID.randomUUID(), "1100",
                "UCL-CONCURRENT-A-" + concurrent.token()
        );
        var second = settlementCommand(
                concurrent, UUID.randomUUID(), "1100",
                "UCL-CONCURRENT-B-" + concurrent.token()
        );
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> firstResult = executor.submit(
                    () -> approveAfter(start, first)
            );
            Future<Object> secondResult = executor.submit(
                    () -> approveAfter(start, second)
            );
            start.countDown();
            List<Object> results = List.of(
                    firstResult.get(30, TimeUnit.SECONDS),
                    secondResult.get(30, TimeUnit.SECONDS)
            );
            assertEquals(1, results.stream()
                    .filter(ApproveLoanSettlementUseCase.Result.class::isInstance)
                    .count());
            assertEquals(1, results.stream()
                    .filter(BusinessStateConflictException.class::isInstance)
                    .count());
        }
        assertEquals(1, count(
                "select count(*) from approved_loan_settlements "
                        + "where loan_account_id = ?",
                concurrent.accountId()
        ));

        accountingActor();
        Activated rollback = activateUcl();
        int beforeTransactions = count(
                "select count(*) from repayment_transactions where loan_account_id = ?",
                rollback.accountId()
        );
        doThrow(new IllegalStateException("forced audit failure"))
                .when(auditPublisher).publish(any());
        assertThrows(IllegalStateException.class, () -> repayments.record(
                repaymentCommand(
                        rollback, UUID.randomUUID(), "UCL-ROLLBACK-" + rollback.token(),
                        "100", VALUE_DATE
                )
        ));
        assertEquals(beforeTransactions, count(
                "select count(*) from repayment_transactions where loan_account_id = ?",
                rollback.accountId()
        ));
        assertMoney("1100", amount(
                "select total_outstanding from loan_accounts where id = ?",
                rollback.accountId()
        ));
        assertEquals(0, salaryMovements(rollback.applicationId()));
    }

    @Test
    void repaymentValidationRejectsFractionalOverpaymentAndInvalidDates() {
        Activated activated = activateUcl();
        assertEquals("REPAYMENT_AMOUNT_INVALID", assertThrows(
                BusinessRuleViolationException.class,
                () -> repaymentCommand(
                        activated, UUID.randomUUID(), "UCL-FRACTIONAL",
                        "1.50", VALUE_DATE
                )
        ).getErrorCode());
        assertEquals("REPAYMENT_EXCEEDS_OUTSTANDING", assertThrows(
                BusinessRuleViolationException.class,
                () -> repayments.record(repaymentCommand(
                        activated, UUID.randomUUID(), "UCL-OVERPAYMENT",
                        "1101", VALUE_DATE
                ))
        ).getErrorCode());
        assertEquals("REPAYMENT_VALUE_DATE_INVALID", assertThrows(
                BusinessRuleViolationException.class,
                () -> repayments.record(repaymentCommand(
                        activated, UUID.randomUUID(), "UCL-BEFORE-DISBURSEMENT",
                        "100", VALUE_DATE.minusDays(1)
                ))
        ).getErrorCode());
        assertEquals("REPAYMENT_VALUE_DATE_INVALID", assertThrows(
                BusinessRuleViolationException.class,
                () -> repayments.record(repaymentCommand(
                        activated, UUID.randomUUID(), "UCL-FUTURE",
                        "100", VALUE_DATE.plusDays(1)
                ))
        ).getErrorCode());
    }

    @Test
    void v42RejectsNonZeroUclReleaseAndAttachedSalaryMovement() {
        Activated ucl = activateUcl();
        RecordRepaymentUseCase.Result repayment = repayments.record(repaymentCommand(
                ucl, UUID.randomUUID(), "UCL-V42-" + ucl.token(), "100", VALUE_DATE
        ));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(DataAccessException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.execute("alter table repayment_operation_outcomes disable trigger "
                    + "trg_repayment_operation_outcomes_immutable");
            jdbc.update(
                    "update repayment_operation_outcomes set principal_released = 1 "
                            + "where repayment_transaction_id = ?",
                    repayment.repaymentTransactionId()
            );
            jdbc.execute("select validate_repayment_operation_outcome_evidence('"
                    + repayment.repaymentTransactionId() + "')");
        }));

        accountingActor();
        var salaryFixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        disbursements.confirm(support.command(
                salaryFixture, UUID.randomUUID(), "SALARY-V42-" + salaryFixture.token()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,movement_type,"
                        + "amount,loan_account_id,repayment_transaction_id,occurred_at) "
                        + "values (?,?,?,'REPAID_RELEASED',?,?,?,current_timestamp)",
                UUID.randomUUID(), salaryFixture.limitId(), ucl.applicationId(),
                repayment.principalAllocated(), ucl.accountId(),
                repayment.repaymentTransactionId()
        ));
        assertEquals(0, salaryMovements(ucl.applicationId()));
        assertMoney("0", amount(
                "select principal_released from repayment_operation_outcomes "
                        + "where repayment_transaction_id = ?",
                repayment.repaymentTransactionId()
        ));
    }

    private Activated activateUcl() {
        accountingActor();
        var fixture = support.createFixture(
                true, ProductCode.UNSECURED_CONSUMER_LOAN
        );
        ConfirmManualDisbursementUseCase.Result result = disbursements.confirm(
                support.command(
                        fixture, UUID.randomUUID(), "UCL-ACTIVATE-" + fixture.token()
                )
        );
        return new Activated(
                fixture.applicationId(), result.loanAccountId(), fixture.token()
        );
    }

    private RecordRepaymentUseCase.Command repaymentCommand(
            Activated activated,
            UUID requestId,
            String reference,
            String amount,
            LocalDate valueDate
    ) {
        return new RecordRepaymentUseCase.Command(
                requestId, activated.applicationId(), reference, money(amount), valueDate
        );
    }

    private ApproveLoanSettlementUseCase.Command settlementCommand(
            Activated activated,
            UUID requestId,
            String amount,
            String reference
    ) {
        return new ApproveLoanSettlementUseCase.Command(
                requestId, activated.applicationId(), money(amount), VALUE_DATE, reference
        );
    }

    private Object approveAfter(
            CountDownLatch start,
            ApproveLoanSettlementUseCase.Command command
    ) {
        try {
            start.await(10, TimeUnit.SECONDS);
            return settlements.approve(command);
        } catch (BusinessStateConflictException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        }
    }

    private void accountingActor() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse", "repayment:update", "loan:read",
                        "loan:account:close")
        ));
    }

    private void approverActor() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                APPROVER_USER_ID, "approver@meridian.test", "STAFF", null,
                Set.of("APPROVER"), Set.of("loan:settlement:approve", "loan:read")
        ));
    }

    private int salaryMovements(UUID applicationId) {
        return count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ?",
                applicationId
        );
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private BigDecimal amount(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, BigDecimal.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private record Activated(UUID applicationId, UUID accountId, String token) {
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock uclServicingClock() {
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
                throw new IllegalArgumentException("UCL servicing test clock uses UTC.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
