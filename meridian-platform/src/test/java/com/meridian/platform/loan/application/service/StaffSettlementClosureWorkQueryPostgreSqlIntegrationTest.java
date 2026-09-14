package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffApprovedSettlementEvidenceUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffClosureWorkUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffSettlementWorkUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.VALUE_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class StaffSettlementClosureWorkQueryPostgreSqlIntegrationTest {
    private static final String SCHEMA = "staff_terminal_work_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID APPROVER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000303"
    );

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired ApproveLoanSettlementUseCase settlements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired EvaluateLoanAccountOverdueUseCase overdueEvaluator;
    @Autowired CloseLoanAccountUseCase closures;
    @Autowired QueryStaffSettlementWorkUseCase settlementWork;
    @Autowired QueryStaffClosureWorkUseCase closureWork;
    @Autowired QueryStaffApprovedSettlementEvidenceUseCase settlementEvidence;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;
    @MockitoBean CurrentUserProvider currentUsers;

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
    }

    @Test
    void discoversAndReconcilesSettlementAndBothClosureProvenancesWithoutMutation() {
        Activated salary = activate(ProductCode.SALARY_ADVANCE, "CP9-SA");
        clock.set(Instant.parse("2026-07-28T10:00:01Z"));
        Activated ucl = activate(ProductCode.UNSECURED_CONSUMER_LOAN, "CP9-UCL");
        clock.set(Instant.parse("2026-09-01T10:00:00Z"));
        overdueEvaluator.evaluate(new EvaluateLoanAccountOverdueUseCase.Command(
                ucl.applicationId(), ucl.accountId(), LocalDate.of(2026, 9, 1),
                LocalDateTime.of(2026, 9, 1, 10, 0)
        ));

        approver();
        int transitionsBefore = transitionCount();
        var initial = settlementWork.queryWork(null, 0, 25);
        assertEquals(2, initial.totalElements());
        assertEquals(ucl.applicationId(), initial.items().getFirst().loanApplicationId());
        assertEquals(Set.of("ACTIVE", "OVERDUE"), initial.items().stream()
                .map(item -> item.accountStatus()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, settlementWork.queryWork(
                ProductCode.SALARY_ADVANCE, 0, 25
        ).totalElements());
        assertEquals(transitionsBefore, transitionCount());

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                update repayment_installment_progress
                set servicing_evaluation_date = servicing_evaluation_date + 1
                where loan_account_id=?
                """, salary.accountId()));

        var settlement = settlements.approve(new ApproveLoanSettlementUseCase.Command(
                UUID.randomUUID(), ucl.applicationId(), outstanding(ucl.accountId()),
                VALUE_DATE, "CP9-SETTLEMENT-" + ucl.token()
        ));
        assertEquals(1, settlementWork.queryWork(null, 0, 25).totalElements());
        var immutable = settlementEvidence.query(ucl.applicationId());
        assertEquals(settlement.settlementAmount(), immutable.settlementAmount());
        assertEquals(settlement.paymentValueDate(), immutable.paymentValueDate());

        accounting();
        repayments.record(new RecordRepaymentUseCase.Command(
                UUID.randomUUID(), salary.applicationId(),
                "CP9-PAYOFF-" + salary.token(), outstanding(salary.accountId()), VALUE_DATE
        ));
        var closureCandidates = closureWork.queryWork(null, 0, 25);
        assertEquals(2, closureCandidates.totalElements());
        assertTrue(closureCandidates.items().stream().anyMatch(item ->
                item.loanApplicationId().equals(ucl.applicationId())
                        && item.payoffProvenance().equals("APPROVED_SETTLEMENT")));
        assertTrue(closureCandidates.items().stream().anyMatch(item ->
                item.loanApplicationId().equals(salary.applicationId())
                        && item.payoffProvenance().equals("CONTRACTUAL_PAYOFF")));
        assertEquals(1, closureWork.queryWork(
                ProductCode.UNSECURED_CONSUMER_LOAN, 0, 25
        ).totalElements());
        int financialRows = repaymentCount();

        closures.close(new CloseLoanAccountUseCase.Command(
                UUID.randomUUID(), ucl.applicationId()
        ));
        assertEquals(1, closureWork.queryWork(null, 0, 25).totalElements());
        assertEquals(financialRows, repaymentCount());

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                update repayment_installment_progress
                set status='NOT_DUE'
                where loan_account_id=?
                """, salary.accountId()));

        approver();
        var afterClosure = settlementEvidence.query(ucl.applicationId());
        assertEquals(immutable, afterClosure);
    }

    private Activated activate(ProductCode product, String prefix) {
        accounting();
        var fixture = support.createFixture(true, product);
        var result = disbursements.confirm(support.command(
                fixture, UUID.randomUUID(), prefix + "-" + fixture.token()
        ));
        return new Activated(fixture.applicationId(), result.loanAccountId(), fixture.token());
    }

    private void approver() {
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                APPROVER_ID, "approver@meridian.test", "STAFF", null,
                Set.of("APPROVER"), Set.of("loan:settlement:approve", "loan:read")
        ));
    }

    private void accounting() {
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse", "repayment:update", "loan:account:close", "loan:read")
        ));
    }

    private BigDecimal outstanding(UUID accountId) {
        return jdbc.queryForObject(
                "select total_outstanding from loan_accounts where id=?",
                BigDecimal.class, accountId
        );
    }

    private int transitionCount() {
        return jdbc.queryForObject("select count(*) from loan_account_status_transitions",
                Integer.class);
    }

    private int repaymentCount() {
        return jdbc.queryForObject("select count(*) from repayment_transactions", Integer.class);
    }

    private record Activated(UUID applicationId, UUID accountId, String token) {
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock staffTerminalWorkClock() {
            return new MutableClock(Instant.parse("2026-07-28T10:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        MutableClock(Instant initial) { current = new AtomicReference<>(initial); }
        void set(Instant value) { current.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException();
            return this;
        }
        @Override public Instant instant() { return current.get(); }
    }
}
