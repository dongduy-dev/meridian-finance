package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcome;
import com.meridian.platform.loan.application.port.out.RepaymentOperationOutcomeRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.VALUE_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(RecordRepaymentRollbackPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class RecordRepaymentRollbackPostgreSqlIntegrationTest {
    private static final String SCHEMA = "repayment_rollback_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean BusinessAuditPublisher auditPublisher;
    @MockitoSpyBean RepaymentTransactionRepository repaymentTransactions;
    @MockitoSpyBean RepaymentInstallmentProgressRepository installmentProgress;
    @MockitoSpyBean RepaymentInstallmentStatusTransitionRepository installmentHistory;
    @MockitoSpyBean LoanAccountRepository loanAccounts;
    @MockitoSpyBean LoanAccountStatusTransitionRepository accountHistory;
    @MockitoSpyBean SalaryAdvanceLimitRepository salaryAdvanceLimits;
    @MockitoSpyBean SalaryAdvanceLimitMovementRepository limitMovements;
    @MockitoSpyBean RepaymentOperationOutcomeRepository operationOutcomes;
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
        reset(auditPublisher);
        support = new ManualDisbursementActivationPostgreSqlTestSupport(
                jdbc, transactionManager
        );
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "repayment.operator@meridian.test", "STAFF",
                null, Set.of("ACCOUNTING_OFFICER"), Set.of("repayment:update")
        ));
    }

    @Test
    void auditFailureRollsBackEveryRepaymentEffect() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "ROLLBACK-" + fixture.token()
        ));
        BigDecimal accountPaidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        doThrow(new IllegalStateException("injected audit failure"))
                .when(auditPublisher).publish(any());

        assertThrows(RuntimeException.class, () -> repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "FAIL-" + fixture.token(), money("100"), VALUE_DATE
                )
        ));

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, accountPaidBefore.compareTo(amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        )));
        assertEquals(0, usedBefore.compareTo(amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? "
                        + "and movement_type = 'REPAID_RELEASED'",
                fixture.applicationId()
        ));
    }

    @Test
    void loanAccountStatusAuditFailureRollsBackExactPayoff() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "STATUS-AUDIT-" + fixture.token()
        ));
        BigDecimal outstanding = amount(
                "select total_outstanding from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        doAnswer(invocation -> {
            BusinessAuditEvent event = invocation.getArgument(0);
            assertTrue(event.entries().stream().anyMatch(entry ->
                    entry.action() == BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED));
            throw new IllegalStateException("injected status audit failure");
        }).when(auditPublisher).publish(any());

        assertThrows(IllegalStateException.class, () -> repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "STATUS-FAIL-" + fixture.token(), outstanding, VALUE_DATE
                )
        ));

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "select status from loan_accounts where id = ?",
                String.class, activation.loanAccountId()
        ));
        assertEquals(0, usedBefore.compareTo(amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? "
                        + "and movement_type = 'REPAID_RELEASED'",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
    }
    @Test
    void missingOutcomeFailsTransactionOriginatingReconciliationAndRollsBack() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "NO-OUTCOME-" + fixture.token()
        ));
        BigDecimal accountPaidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal progressPaidBefore = amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        BigDecimal availableBefore = amount(
                "select available_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        int installmentHistoryBefore = installmentHistoryCount(
                activation.loanAccountId()
        );
        int accountHistoryBefore = accountHistoryCount(activation.loanAccountId());
        doAnswer(invocation -> {
            persistAudit(invocation.getArgument(0));
            return null;
        }).when(auditPublisher).publish(any());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(target(operationOutcomes)).save(any());

        assertThrows(RuntimeException.class, () -> repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "OMITTED-OUTCOME-" + fixture.token(), money("100"), VALUE_DATE
                )
        ));

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_allocations allocation "
                        + "join repayment_transactions transaction_row "
                        + "on transaction_row.id = allocation.repayment_transaction_id "
                        + "where transaction_row.loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, accountPaidBefore.compareTo(amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        )));
        assertEquals(0, progressPaidBefore.compareTo(amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        )));
        assertEquals(installmentHistoryBefore,
                installmentHistoryCount(activation.loanAccountId()));
        assertEquals(accountHistoryBefore, accountHistoryCount(activation.loanAccountId()));
        assertEquals(0, usedBefore.compareTo(amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, availableBefore.compareTo(amount(
                "select available_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? "
                        + "and movement_type = 'REPAID_RELEASED'",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from audit_events audit "
                        + "where audit.operation_id in ("
                        + "select transaction_row.id from repayment_transactions transaction_row "
                        + "where transaction_row.loan_application_id = ?)",
                fixture.applicationId()
        ));
    }
    @Test
    void missingAuditEvidenceFailsDeferredReconciliationAndRollsBack() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "DEFERRED-" + fixture.token()
        ));
        BigDecimal paidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );

        assertThrows(RuntimeException.class, () -> repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "NO-AUDIT-" + fixture.token(), money("100"), VALUE_DATE
                )
        ));

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, paidBefore.compareTo(amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        )));
        assertEquals(0, usedBefore.compareTo(amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? "
                        + "and movement_type = 'REPAID_RELEASED'",
                fixture.applicationId()
        ));
    }
    @ParameterizedTest
    @EnumSource(OutcomeTransitionCorruption.class)
    void itemSpecificOutcomeTransitionContradictionsFailAtCommitAndRollBack(
            OutcomeTransitionCorruption corruption
    ) {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "ITEM-EVIDENCE-" + fixture.token()
        ));
        BigDecimal paidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        int installmentHistoryBefore = installmentHistoryCount(
                activation.loanAccountId()
        );
        doAnswer(invocation -> {
            persistAudit(invocation.getArgument(0));
            return null;
        }).when(auditPublisher).publish(any());
        doAnswer(invocation -> {
            RepaymentOperationOutcome outcome = invocation.getArgument(0);
            persistOutcome(outcome, corruption.apply(
                    objectMapper.writeValueAsString(outcome)
            ));
            return outcome;
        }).when(target(operationOutcomes)).save(any());

        assertThrows(RuntimeException.class, () -> repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "BAD-ITEM-" + corruption.name() + "-" + fixture.token(),
                        money("100"), VALUE_DATE
                )
        ));

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, paidBefore.compareTo(amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        )));
        assertEquals(installmentHistoryBefore,
                installmentHistoryCount(activation.loanAccountId()));
        assertEquals(0, usedBefore.compareTo(amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? "
                        + "and movement_type = 'REPAID_RELEASED'",
                fixture.applicationId()
        ));
    }
    @Test
    void allocationPersistenceFailureRollsBackInsertedTransaction() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "ALLOCATION-" + fixture.token()
        ));
        BigDecimal paidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        jdbc.execute("create function " + SCHEMA
                + ".fail_repayment_allocation_insert() returns trigger "
                + "language plpgsql as $$ begin raise exception "
                + "'injected allocation persistence failure'; end $$");
        jdbc.execute("create trigger trg_fail_repayment_allocation_insert "
                + "before insert on repayment_allocations for each row execute function "
                + SCHEMA + ".fail_repayment_allocation_insert()");
        try {
            assertThrows(RuntimeException.class, () -> repayments.record(
                    new RecordRepaymentUseCase.Command(
                            UUID.randomUUID(), fixture.applicationId(),
                            "ALLOC-FAIL-" + fixture.token(), money("100"), VALUE_DATE
                    )
            ));
        } finally {
            jdbc.execute("drop trigger if exists trg_fail_repayment_allocation_insert "
                    + "on repayment_allocations");
            jdbc.execute("drop function if exists " + SCHEMA
                    + ".fail_repayment_allocation_insert()");
        }

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_allocations allocation "
                        + "join repayment_transactions transaction_row "
                        + "on transaction_row.id = allocation.repayment_transaction_id "
                        + "where transaction_row.loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, paidBefore.compareTo(amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        )));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? "
                        + "and movement_type = 'REPAID_RELEASED'",
                fixture.applicationId()
        ));
    }
    @ParameterizedTest
    @EnumSource(FailureStage.class)
    void persistenceFailureAtEveryBoundaryRollsBackTheWholeRepayment(
            FailureStage failureStage
    ) {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), "STAGE-" + fixture.token()
        ));
        BigDecimal outstanding = amount(
                "select total_outstanding from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal accountPaidBefore = amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        );
        BigDecimal progressPaidBefore = amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        BigDecimal availableBefore = amount(
                "select available_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        );
        int installmentHistoryBefore = count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress "
                        + "on progress.repayment_schedule_item_id = "
                        + "history.repayment_schedule_item_id "
                        + "where progress.loan_account_id = ?",
                activation.loanAccountId()
        );
        int accountHistoryBefore = count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        );
        IllegalStateException injected = new IllegalStateException(
                "injected " + failureStage.name().toLowerCase() + " failure"
        );
        inject(failureStage, injected);

        assertThrows(RuntimeException.class, () -> repayments.record(
                new RecordRepaymentUseCase.Command(
                        UUID.randomUUID(), fixture.applicationId(),
                        "BOUNDARY-" + fixture.token(), outstanding, VALUE_DATE
                )
        ));

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_allocations allocation "
                        + "join repayment_transactions transaction_row "
                        + "on transaction_row.id = allocation.repayment_transaction_id "
                        + "where transaction_row.loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id = ?",
                fixture.applicationId()
        ));
        assertEquals(0, accountPaidBefore.compareTo(amount(
                "select total_paid from loan_accounts where id = ?",
                activation.loanAccountId()
        )));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "select status from loan_accounts where id = ?",
                String.class, activation.loanAccountId()
        ));
        assertEquals(0, progressPaidBefore.compareTo(amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        )));
        assertEquals(installmentHistoryBefore, count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_installment_progress progress "
                        + "on progress.repayment_schedule_item_id = "
                        + "history.repayment_schedule_item_id "
                        + "where progress.loan_account_id = ?",
                activation.loanAccountId()
        ));
        assertEquals(accountHistoryBefore, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        ));
        assertEquals(0, usedBefore.compareTo(amount(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, availableBefore.compareTo(amount(
                "select available_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        )));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id = ? "
                        + "and movement_type = 'REPAID_RELEASED'",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from audit_events where operation_id in ("
                        + "select id from repayment_transactions "
                        + "where loan_application_id = ?)",
                fixture.applicationId()
        ));
    }

    private void persistOutcome(RepaymentOperationOutcome outcome, String json) {
        jdbc.update(
                "insert into repayment_operation_outcomes "
                        + "(repayment_transaction_id,loan_application_id,loan_account_id,"
                        + "repayment_schedule_id,received_amount,payment_value_date,recorded_at,"
                        + "principal_released,account_status,account_status_changed,outcome_json) "
                        + "values (?,?,?,?,?,?,?,?,?,?,cast(? as jsonb))",
                outcome.repaymentTransactionId(), outcome.loanApplicationId(),
                outcome.loanAccountId(), outcome.repaymentScheduleId(),
                outcome.receivedAmount(), outcome.paymentValueDate(), outcome.recordedAt(),
                outcome.principalReleased(), outcome.accountStatus().name(),
                outcome.accountStatusChanged(), json
        );
    }
    private void persistAudit(BusinessAuditEvent event) {
        int sequence = 1;
        for (var entry : event.entries()) {
            jdbc.update(
                    "insert into audit_events "
                            + "(id,operation_id,sequence_number,actor_type,actor_user_id,"
                            + "entity_type,entity_id,action,payload,occurred_at) "
                            + "values (?,?,?,?,?,?,?,?,cast('{}' as jsonb),?)",
                    UUID.randomUUID(), event.operationContext().operationId(), sequence++,
                    event.operationContext().actorType().name(),
                    event.operationContext().actorUserId(), entry.entityType().name(),
                    entry.entityId(), entry.action().name(),
                    event.operationContext().occurredAt()
            );
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
    private void inject(FailureStage stage, IllegalStateException failure) {
        switch (stage) {
            case TRANSACTION -> doThrow(failure)
                    .when(target(repaymentTransactions)).save(any());
            case PROGRESS -> doThrow(failure)
                    .when(target(installmentProgress)).saveAll(any());
            case INSTALLMENT_HISTORY -> doThrow(failure)
                    .when(target(installmentHistory)).save(any());
            case ACCOUNT -> doThrow(failure)
                    .when(target(loanAccounts)).updateServicingState(any());
            case ACCOUNT_HISTORY -> doThrow(failure)
                    .when(target(accountHistory)).save(any());
            case LIMIT -> doThrow(failure)
                    .when(target(salaryAdvanceLimits)).save(any());
            case RELEASE_MOVEMENT -> doThrow(failure)
                    .when(target(limitMovements)).save(any());
            case OUTCOME -> doThrow(failure)
                    .when(target(operationOutcomes)).save(any());
        }
    }
    private static <T> T target(T proxiedSpy) {
        return AopTestUtils.getUltimateTargetObject(proxiedSpy);
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

    private enum OutcomeTransitionCorruption {
        SNAPSHOT_OMITS_EXISTING_TRANSITION {
            @Override
            String apply(String json) {
                return json.replaceFirst(
                        "\"previousStatus\":\"NOT_DUE\","
                                + "\"statusChanged\":true",
                        "\"previousStatus\":\"PARTIALLY_PAID\","
                                + "\"statusChanged\":false"
                );
            }
        },
        SNAPSHOT_CLAIMS_MISSING_TRANSITION {
            @Override
            String apply(String json) {
                return json.replaceFirst(
                        "\"previousStatus\":\"NOT_DUE\","
                                + "\"statusChanged\":false",
                        "\"previousStatus\":\"DUE\","
                                + "\"statusChanged\":true"
                );
            }
        },
        SWAPPED_TRANSITION_ITEMS {
            @Override
            String apply(String json) {
                return SNAPSHOT_CLAIMS_MISSING_TRANSITION.apply(
                        SNAPSHOT_OMITS_EXISTING_TRANSITION.apply(json)
                );
            }
        };

        abstract String apply(String json);
    }
    private enum FailureStage {
        TRANSACTION,
        PROGRESS,
        INSTALLMENT_HISTORY,
        ACCOUNT,
        ACCOUNT_HISTORY,
        LIMIT,
        RELEASE_MOVEMENT,
        OUTCOME
    }
    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock repaymentRollbackClock() {
            return Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
