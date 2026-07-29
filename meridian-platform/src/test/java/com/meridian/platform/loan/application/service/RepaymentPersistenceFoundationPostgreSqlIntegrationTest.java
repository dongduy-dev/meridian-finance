package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionSaveOutcome;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentServicingAction;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatusTransition;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.model.ActorType;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.NOW;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.VALUE_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(RepaymentPersistenceFoundationPostgreSqlIntegrationTest
        .FixedClockConfiguration.class)
class RepaymentPersistenceFoundationPostgreSqlIntegrationTest {

    private static final String SCHEMA = "repayment_foundation_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired RepaymentTransactionRepository repaymentTransactions;
    @Autowired RepaymentInstallmentProgressRepository progressRepository;
    @Autowired LoanAccountStatusTransitionRepository accountHistoryRepository;
    @Autowired RepaymentInstallmentStatusTransitionRepository installmentHistoryRepository;
    @Autowired LoanAccountRepository loanAccounts;
    @Autowired RepaymentScheduleRepository schedules;
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
                jdbc,
                transactionManager
        );
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting.officer@meridian.test",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse", "repayment:update")
        ));
    }

    @Test
    void roundTripsAggregateProgressAndHistoriesWithTypedConflicts() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "FOUNDATION-" + fixture.token()
        ));

        UUID transactionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String reference = "PAYMENT-" + fixture.token();
        LocalDateTime recordedAt = NOW.plusMinutes(5);

        transactions.executeWithoutResult(ignored -> {
            LoanAccount account = loanAccounts
                    .findById(activation.loanAccountId())
                    .orElseThrow();
            RepaymentSchedule schedule = schedules
                    .findByLoanAccountId(account.id())
                    .orElseThrow();
            List<RepaymentInstallmentProgress> progress =
                    progressRepository.findByRepaymentScheduleId(schedule.id());
            RepaymentInstallmentProgress first = progress.getFirst();
            RepaymentAllocation allocation = new RepaymentAllocation(
                    UUID.randomUUID(),
                    transactionId,
                    1,
                    first.repaymentScheduleItemId(),
                    RepaymentAllocationComponent.INTEREST,
                    money("50")
            );
            RepaymentTransaction transaction = RepaymentTransaction.recorded(
                    transactionId,
                    fixture.applicationId(),
                    account.id(),
                    schedule.id(),
                    requestId,
                    reference,
                    money("50"),
                    VALUE_DATE,
                    VALUE_DATE,
                    VALUE_DATE,
                    ACCOUNTING_USER_ID,
                    recordedAt,
                    List.of(allocation)
            );
            assertInstanceOf(
                    RepaymentTransactionSaveOutcome.Inserted.class,
                    repaymentTransactions.save(transaction)
            );

            RepaymentInstallmentProgress updatedFirst =
                    new RepaymentInstallmentProgress(
                            first.repaymentScheduleItemId(),
                            first.repaymentScheduleId(),
                            first.loanAccountId(),
                            first.installmentNumber(),
                            money("0"),
                            money("50"),
                            money("0"),
                            money("50"),
                            first.principalOutstanding(),
                            money("0"),
                            first.feeOutstanding(),
                            money("500"),
                            RepaymentInstallmentStatus.PARTIALLY_PAID,
                            VALUE_DATE,
                            recordedAt,
                            first.servicingEvaluationDate(),
                            recordedAt
                    );
            progressRepository.saveAll(List.of(updatedFirst, progress.get(1)));
            installmentHistoryRepository.save(
                    new RepaymentInstallmentStatusTransition(
                            UUID.randomUUID(),
                            first.repaymentScheduleItemId(),
                            2,
                            transactionId,
                            first.status(),
                            updatedFirst.status(),
                            RepaymentInstallmentServicingAction.REPAYMENT_RECORDED,
                            ActorType.USER,
                            ACCOUNTING_USER_ID,
                            first.servicingEvaluationDate(),
                            recordedAt
                    )
            );
            RepaymentBalance updatedBalance = new RepaymentBalance(
                    money("0"),
                    money("50"),
                    money("0"),
                    money("50"),
                    account.approvedPrincipal(),
                    money("50"),
                    account.feeAmount(),
                    money("1050"),
                    VALUE_DATE,
                    recordedAt,
                    account.servicingEvaluationDate()
            );
            loanAccounts.updateServicingState(account.withServicingState(
                    updatedBalance,
                    account.status(),
                    recordedAt
            ));
        });

        transactions.executeWithoutResult(ignored -> {
            RepaymentTransaction reloaded = repaymentTransactions
                    .findById(transactionId)
                    .orElseThrow();
            assertTrue(reloaded.externalPaymentReference().equals(
                    RepaymentTransaction.canonicalReference(
                            " payment-" + fixture.token().toLowerCase() + " "
                    )
            ));
            assertEquals(1, reloaded.allocations().size());
            assertFalse(reloaded.toString().contains(
                    reloaded.externalPaymentReference()
            ));
            assertEquals(
                    RepaymentInstallmentStatus.PARTIALLY_PAID,
                    progressRepository
                            .findByLoanAccountIdForUpdate(activation.loanAccountId())
                            .getFirst()
                            .status()
            );
            assertEquals(
                    1,
                    accountHistoryRepository
                            .findByLoanAccountId(activation.loanAccountId())
                            .size()
            );
            assertEquals(
                    2,
                    installmentHistoryRepository
                            .findByRepaymentScheduleItemId(
                                    reloaded.allocations().getFirst()
                                            .repaymentScheduleItemId()
                            )
                            .size()
            );
            assertInstanceOf(
                    RepaymentTransactionSaveOutcome.ExistingRequest.class,
                    repaymentTransactions.save(reloaded)
            );

            UUID duplicateTransactionId = UUID.randomUUID();
            RepaymentTransaction duplicateReference = new RepaymentTransaction(
                    duplicateTransactionId,
                    reloaded.loanApplicationId(),
                    reloaded.loanAccountId(),
                    reloaded.repaymentScheduleId(),
                    UUID.randomUUID(),
                    reference,
                    reloaded.receivedAmount(),
                    reloaded.paymentValueDate(),
                    reloaded.recordedByUserId(),
                    reloaded.recordedAt(),
                    List.of(new RepaymentAllocation(
                            UUID.randomUUID(),
                            duplicateTransactionId,
                            1,
                            reloaded.allocations().getFirst()
                                    .repaymentScheduleItemId(),
                            RepaymentAllocationComponent.INTEREST,
                            money("50")
                    ))
            );
            RepaymentTransactionSaveOutcome.Conflict conflict = assertInstanceOf(
                    RepaymentTransactionSaveOutcome.Conflict.class,
                    repaymentTransactions.save(duplicateReference)
            );
            assertEquals(
                    RepaymentTransactionSaveOutcome.ConflictKind
                            .EXTERNAL_PAYMENT_REFERENCE,
                    conflict.kind()
            );
        });
    }

    @Test
    void databaseRejectsEvidenceMutationAndDeferredReconciliationGaps() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "GUARDS-" + fixture.token()
        ));
        UUID transactionId = UUID.randomUUID();

        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored -> {
                    RepaymentSchedule schedule = schedules
                            .findByLoanAccountId(activation.loanAccountId())
                            .orElseThrow();
                    RepaymentInstallmentProgress first = progressRepository
                            .findByRepaymentScheduleId(schedule.id())
                            .getFirst();
                    repaymentTransactions.save(RepaymentTransaction.recorded(
                            transactionId,
                            fixture.applicationId(),
                            activation.loanAccountId(),
                            schedule.id(),
                            UUID.randomUUID(),
                            "UNRECONCILED-" + fixture.token(),
                            money("50"),
                            VALUE_DATE,
                            VALUE_DATE,
                            VALUE_DATE,
                            ACCOUNTING_USER_ID,
                            NOW.plusMinutes(10),
                            List.of(new RepaymentAllocation(
                                    UUID.randomUUID(),
                                    transactionId,
                                    1,
                                    first.repaymentScheduleItemId(),
                                    RepaymentAllocationComponent.INTEREST,
                                    money("50")
                            ))
                    ));
                }));

        assertEquals(0, support.count(
                "select count(*) from repayment_transactions where id = ?",
                transactionId
        ));
        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored -> jdbc.update(
                        "update repayment_installment_progress "
                                + "set principal_paid = 1 where loan_account_id = ?",
                        activation.loanAccountId()
                )));
        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored -> jdbc.update(
                        "update loan_accounts set approved_principal = 999 "
                                + "where id = ?",
                        activation.loanAccountId()
                )));
        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored -> jdbc.update(
                        "update repayment_schedule_items set due_date = due_date + 1 "
                                + "where repayment_schedule_id = ?",
                        activation.repaymentScheduleId()
                )));
    }

    @Test
    void advancesEvaluationDatesWithoutFakeSameStateHistory() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "EVALUATION-" + fixture.token()
        ));
        LocalDate evaluationDate = VALUE_DATE.plusDays(10);
        LocalDateTime changedAt = NOW.plusDays(10);

        transactions.executeWithoutResult(ignored -> {
            jdbc.update(
                    "update repayment_installment_progress "
                            + "set servicing_evaluation_date = ?, updated_at = ? "
                            + "where loan_account_id = ?",
                    evaluationDate,
                    changedAt,
                    activation.loanAccountId()
            );
            jdbc.update(
                    "update loan_accounts "
                            + "set servicing_evaluation_date = ?, updated_at = ? "
                            + "where id = ?",
                    evaluationDate,
                    changedAt,
                    activation.loanAccountId()
            );
        });

        assertEquals("ACTIVE", jdbc.queryForObject(
                "select status from loan_accounts where id = ?",
                String.class,
                activation.loanAccountId()
        ));
        assertEquals(evaluationDate, jdbc.queryForObject(
                "select servicing_evaluation_date from loan_accounts where id = ?",
                LocalDate.class,
                activation.loanAccountId()
        ));
        assertEquals(1, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        ));
        assertEquals(2, count(
                "select count(*) from repayment_installment_progress "
                        + "where loan_account_id = ? and status = 'NOT_DUE' "
                        + "and servicing_evaluation_date = ?",
                activation.loanAccountId(),
                evaluationDate
        ));
        assertEquals(2, count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_schedule_items item "
                        + "on item.id = history.repayment_schedule_item_id "
                        + "where item.repayment_schedule_id = ?",
                activation.repaymentScheduleId()
        ));
    }

    @Test
    void actualStatusChangeWithoutTransitionHistoryFailsAtCommit() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "MISSING-HISTORY-" + fixture.token()
        ));
        LocalDate evaluationDate = ManualDisbursementActivationPostgreSqlTestSupport
                .FIRST_REPAYMENT_DATE;
        LocalDateTime changedAt = NOW.plusMonths(1);

        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored ->
                        advanceToFirstDueDate(
                                activation.loanAccountId(),
                                evaluationDate,
                                changedAt,
                                false
                        )
                ));

        assertEquals("NOT_DUE", jdbc.queryForObject(
                "select status from repayment_installment_progress "
                        + "where repayment_schedule_item_id = ?",
                String.class,
                activation.scheduleItems().getFirst().id()
        ));
        assertEquals(VALUE_DATE, jdbc.queryForObject(
                "select servicing_evaluation_date from loan_accounts where id = ?",
                LocalDate.class,
                activation.loanAccountId()
        ));
    }

    @Test
    void actualStatusChangeWithTransitionHistoryCommits() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "VALID-HISTORY-" + fixture.token()
        ));
        LocalDate evaluationDate = ManualDisbursementActivationPostgreSqlTestSupport
                .FIRST_REPAYMENT_DATE;
        LocalDateTime changedAt = NOW.plusMonths(1);

        transactions.executeWithoutResult(ignored -> advanceToFirstDueDate(
                activation.loanAccountId(),
                evaluationDate,
                changedAt,
                true
        ));

        assertEquals("DUE", jdbc.queryForObject(
                "select status from repayment_installment_progress "
                        + "where repayment_schedule_item_id = ?",
                String.class,
                activation.scheduleItems().getFirst().id()
        ));
        assertEquals(2, count(
                "select count(*) from repayment_installment_status_transitions "
                        + "where repayment_schedule_item_id = ?",
                activation.scheduleItems().getFirst().id()
        ));
        assertEquals(1, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        ));
    }

    @Test
    void sameLimitPrincipalReleaseCommitsWithEarlyPartialRepayment() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "SAME-LIMIT-" + fixture.token()
        ));
        UUID transactionId = UUID.randomUUID();

        transactions.executeWithoutResult(ignored -> persistEarlyPartialRepayment(
                fixture,
                activation,
                fixture.limitId(),
                transactionId
        ));

        assertEquals(1, count(
                "select count(*) from repayment_transactions where id = ?",
                transactionId
        ));
        assertEquals(1, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id = ? "
                        + "and salary_advance_limit_id = ? "
                        + "and movement_type = 'REPAID_RELEASED' and amount = 100",
                transactionId,
                fixture.limitId()
        ));
        assertMoney("900", value(
                "select used_amount from salary_advance_limits where id = ?",
                fixture.limitId()
        ));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "select status from loan_accounts where id = ?",
                String.class,
                activation.loanAccountId()
        ));
        assertEquals(1, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id = ?",
                activation.loanAccountId()
        ));
        assertEquals(1, count(
                "select count(*) from repayment_installment_status_transitions "
                        + "where repayment_schedule_item_id = ?",
                activation.scheduleItems().get(1).id()
        ));
        assertEquals(0, count(
                "select count(*) from ("
                        + "select salary_advance_limit_id "
                        + "from salary_advance_limit_movements group by salary_advance_limit_id "
                        + "having coalesce(sum(amount) filter ("
                        + "where movement_type = 'REPAID_RELEASED'),0) "
                        + "> coalesce(sum(amount) filter ("
                        + "where movement_type = 'DISBURSED_TO_USED'),0)) excess"
        ));

        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored -> jdbc.update(
                        "insert into salary_advance_limit_movements "
                                + "(id,salary_advance_limit_id,loan_application_id,"
                                + "loan_account_id,repayment_transaction_id,movement_type,"
                                + "amount,occurred_at) values (?,?,?,?,?,'REPAID_RELEASED',100,?)",
                        UUID.randomUUID(),
                        fixture.limitId(),
                        fixture.applicationId(),
                        activation.loanAccountId(),
                        transactionId,
                        NOW.plusMinutes(10)
                )));
        assertEquals(1, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id = ?",
                transactionId
        ));
    }

    @Test
    void crossCustomerLimitReleaseFailsAndRollsBackWholeOperation() {
        var first = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var firstActivation = disbursements.confirm(support.command(
                first,
                UUID.randomUUID(),
                "CROSS-A-" + first.token()
        ));
        var second = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        disbursements.confirm(support.command(
                second,
                UUID.randomUUID(),
                "CROSS-B-" + second.token()
        ));
        UUID transactionId = UUID.randomUUID();

        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored -> persistEarlyPartialRepayment(
                        first,
                        firstActivation,
                        second.limitId(),
                        transactionId
                )));

        assertRepaymentOperationRolledBack(
                first,
                firstActivation.loanAccountId(),
                second,
                transactionId
        );
    }

    @Test
    void correctApplicationAndAccountCannotOverrideAuthoritativeLimit() {
        var owning = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var owningActivation = disbursements.confirm(support.command(
                owning,
                UUID.randomUUID(),
                "OWNER-" + owning.token()
        ));
        var unrelated = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        disbursements.confirm(support.command(
                unrelated,
                UUID.randomUUID(),
                "UNRELATED-" + unrelated.token()
        ));
        UUID transactionId = UUID.randomUUID();

        assertThrows(DataAccessException.class, () ->
                transactions.executeWithoutResult(ignored -> persistEarlyPartialRepayment(
                        owning,
                        owningActivation,
                        unrelated.limitId(),
                        transactionId
                )));

        assertEquals(0, count(
                "select count(*) from repayment_transactions where id = ?",
                transactionId
        ));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id = ?",
                transactionId
        ));
        assertMoney("1000", value(
                "select used_amount from salary_advance_limits where id = ?",
                owning.limitId()
        ));
        assertMoney("1000", value(
                "select used_amount from salary_advance_limits where id = ?",
                unrelated.limitId()
        ));
    }

    private void advanceToFirstDueDate(
            UUID loanAccountId,
            LocalDate evaluationDate,
            LocalDateTime changedAt,
            boolean includeTransition
    ) {
        UUID firstItemId = jdbc.queryForObject(
                "select progress.repayment_schedule_item_id "
                        + "from repayment_installment_progress progress "
                        + "where progress.loan_account_id = ? "
                        + "order by progress.installment_number limit 1",
                UUID.class,
                loanAccountId
        );
        jdbc.update(
                "update repayment_installment_progress "
                        + "set servicing_evaluation_date = ?, "
                        + "status = case when repayment_schedule_item_id = ? "
                        + "then 'DUE' else 'NOT_DUE' end, updated_at = ? "
                        + "where loan_account_id = ?",
                evaluationDate,
                firstItemId,
                changedAt,
                loanAccountId
        );
        jdbc.update(
                "update loan_accounts "
                        + "set servicing_evaluation_date = ?, status = 'ACTIVE', updated_at = ? "
                        + "where id = ?",
                evaluationDate,
                changedAt,
                loanAccountId
        );
        if (includeTransition) {
            jdbc.update(
                    "insert into repayment_installment_status_transitions "
                            + "(id,repayment_schedule_item_id,sequence_number,operation_id,"
                            + "from_status,to_status,action,actor_type,actor_user_id,"
                            + "servicing_evaluation_date,occurred_at) "
                            + "values (?,?,2,?,'NOT_DUE','DUE','OVERDUE_EVALUATED',"
                            + "'SYSTEM',null,?,?)",
                    UUID.randomUUID(),
                    firstItemId,
                    UUID.randomUUID(),
                    evaluationDate,
                    changedAt
            );
        }
    }

    private void persistEarlyPartialRepayment(
            ManualDisbursementActivationPostgreSqlTestSupport.Fixture fixture,
            ConfirmManualDisbursementUseCase.Result activation,
            UUID releaseLimitId,
            UUID transactionId
    ) {
        UUID firstItemId = activation.scheduleItems().getFirst().id();
        UUID secondItemId = activation.scheduleItems().get(1).id();
        LocalDate evaluationDate = VALUE_DATE.plusDays(10);
        LocalDateTime recordedAt = NOW.plusDays(10);

        jdbc.update(
                "insert into repayment_transactions "
                        + "(id,loan_application_id,loan_account_id,repayment_schedule_id,"
                        + "request_id,external_payment_reference,received_amount,"
                        + "payment_value_date,recorded_by_user_id,recorded_at) "
                        + "values (?,?,?,?,?,?,150,?,?,?)",
                transactionId,
                fixture.applicationId(),
                activation.loanAccountId(),
                activation.repaymentScheduleId(),
                UUID.randomUUID(),
                "FOUNDATION-" + fixture.token(),
                VALUE_DATE,
                ACCOUNTING_USER_ID,
                recordedAt
        );
        jdbc.update(
                "insert into repayment_allocations "
                        + "(id,repayment_transaction_id,allocation_sequence,"
                        + "repayment_schedule_item_id,component,amount) "
                        + "values (?,?,1,?,'INTEREST',50),(?,?,2,?,'PRINCIPAL',100)",
                UUID.randomUUID(), transactionId, firstItemId,
                UUID.randomUUID(), transactionId, firstItemId
        );
        jdbc.update(
                "update repayment_installment_progress set "
                        + "principal_paid = 100, interest_paid = 50, total_paid = 150, "
                        + "principal_outstanding = 400, interest_outstanding = 0, "
                        + "total_outstanding = 400, status = 'PARTIALLY_PAID', "
                        + "last_payment_value_date = ?, last_payment_recorded_at = ?, "
                        + "servicing_evaluation_date = ?, updated_at = ? "
                        + "where repayment_schedule_item_id = ?",
                VALUE_DATE, recordedAt, evaluationDate, recordedAt, firstItemId
        );
        jdbc.update(
                "update repayment_installment_progress "
                        + "set servicing_evaluation_date = ?, updated_at = ? "
                        + "where repayment_schedule_item_id = ?",
                evaluationDate, recordedAt, secondItemId
        );
        jdbc.update(
                "update loan_accounts set "
                        + "principal_paid = 100, interest_paid = 50, total_paid = 150, "
                        + "principal_outstanding = 900, interest_outstanding = 50, "
                        + "total_outstanding = 950, status = 'ACTIVE', "
                        + "last_payment_value_date = ?, last_payment_recorded_at = ?, "
                        + "servicing_evaluation_date = ?, updated_at = ? where id = ?",
                VALUE_DATE,
                recordedAt,
                evaluationDate,
                recordedAt,
                activation.loanAccountId()
        );
        jdbc.update(
                "insert into repayment_installment_status_transitions "
                        + "(id,repayment_schedule_item_id,sequence_number,operation_id,"
                        + "from_status,to_status,action,actor_type,actor_user_id,"
                        + "servicing_evaluation_date,occurred_at) "
                        + "values (?,?,2,?,'NOT_DUE','PARTIALLY_PAID','REPAYMENT_RECORDED',"
                        + "'USER',?,?,?)",
                UUID.randomUUID(),
                firstItemId,
                transactionId,
                ACCOUNTING_USER_ID,
                evaluationDate,
                recordedAt
        );
        jdbc.update(
                "insert into salary_advance_limit_movements "
                        + "(id,salary_advance_limit_id,loan_application_id,loan_account_id,"
                        + "repayment_transaction_id,movement_type,amount,occurred_at) "
                        + "values (?,?,?,?,?,'REPAID_RELEASED',100,?)",
                UUID.randomUUID(),
                releaseLimitId,
                fixture.applicationId(),
                activation.loanAccountId(),
                transactionId,
                recordedAt
        );
        jdbc.update(
                "update salary_advance_limits "
                        + "set used_amount = used_amount - 100, "
                        + "available_amount = available_amount + 100, last_refreshed_at = ? "
                        + "where id = ?",
                recordedAt,
                releaseLimitId
        );
    }

    private void assertRepaymentOperationRolledBack(
            ManualDisbursementActivationPostgreSqlTestSupport.Fixture first,
            UUID firstLoanAccountId,
            ManualDisbursementActivationPostgreSqlTestSupport.Fixture second,
            UUID transactionId
    ) {
        assertEquals(0, count(
                "select count(*) from repayment_transactions where id = ?",
                transactionId
        ));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where repayment_transaction_id = ?",
                transactionId
        ));
        assertMoney("1000", value(
                "select used_amount from salary_advance_limits where id = ?",
                first.limitId()
        ));
        assertMoney("1000", value(
                "select used_amount from salary_advance_limits where id = ?",
                second.limitId()
        ));
        assertMoney("0", value(
                "select total_paid from loan_accounts where id = ?",
                firstLoanAccountId
        ));
        assertEquals(2, count(
                "select count(*) from repayment_installment_progress "
                        + "where loan_account_id = ? and total_paid = 0",
                firstLoanAccountId
        ));
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private BigDecimal value(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, BigDecimal.class, arguments);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock repaymentFoundationClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-28T10:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
