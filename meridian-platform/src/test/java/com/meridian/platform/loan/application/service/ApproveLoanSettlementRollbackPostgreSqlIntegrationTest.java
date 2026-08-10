package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
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
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(ApproveLoanSettlementRollbackPostgreSqlIntegrationTest
        .FixedClockConfiguration.class)
class ApproveLoanSettlementRollbackPostgreSqlIntegrationTest {

    private static final String SCHEMA = "settlement_rollback_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final UUID APPROVER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000303"
    );
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 9, 1);

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired ApproveLoanSettlementUseCase settlements;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean BusinessAuditPublisher auditPublisher;

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
                jdbc,
                transactionManager
        );
        when(currentUserProvider.currentUser()).thenReturn(accountingActor());
    }

    @Test
    void lateAuditFailureRollsBackEverySettlementEffect() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "SETTLEMENT-ROLLBACK-" + fixture.token()
        ));
        BigDecimal outstanding = amount(
                "select total_outstanding from loan_accounts where id=?",
                activation.loanAccountId()
        );
        BigDecimal paidBefore = amount(
                "select total_paid from loan_accounts where id=?",
                activation.loanAccountId()
        );
        BigDecimal progressBefore = amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        );
        BigDecimal usedBefore = amount(
                "select used_amount from salary_advance_limits where id=?",
                fixture.limitId()
        );
        String statusBefore = text(
                "select status from loan_accounts where id=?",
                activation.loanAccountId()
        );
        int accountHistoryBefore = count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        );
        int installmentHistoryBefore = count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_schedule_items item "
                        + "on item.id=history.repayment_schedule_item_id "
                        + "join repayment_schedules schedule "
                        + "on schedule.id=item.repayment_schedule_id "
                        + "where schedule.loan_account_id=?",
                activation.loanAccountId()
        );
        int auditBefore = count("select count(*) from audit_events");
        when(currentUserProvider.currentUser()).thenReturn(approverActor());
        doThrow(new IllegalStateException("injected settlement audit failure"))
                .when(auditPublisher).publish(any());

        assertThrows(IllegalStateException.class, () -> settlements.approve(
                new ApproveLoanSettlementUseCase.Command(
                        UUID.randomUUID(),
                        fixture.applicationId(),
                        outstanding,
                        PAYMENT_DATE,
                        "SETTLEMENT-FAIL-" + fixture.token()
                )
        ));

        assertEquals(0, count(
                "select count(*) from repayment_transactions "
                        + "where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_allocations allocation "
                        + "join repayment_transactions transaction_row "
                        + "on transaction_row.id=allocation.repayment_transaction_id "
                        + "where transaction_row.loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from repayment_operation_outcomes "
                        + "where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(0, count(
                "select count(*) from approved_loan_settlements "
                        + "where loan_application_id=?",
                fixture.applicationId()
        ));
        assertEquals(statusBefore, text(
                "select status from loan_accounts where id=?",
                activation.loanAccountId()
        ));
        assertEquals(0, paidBefore.compareTo(amount(
                "select total_paid from loan_accounts where id=?",
                activation.loanAccountId()
        )));
        assertEquals(0, progressBefore.compareTo(amount(
                "select sum(total_paid) from repayment_installment_progress "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        )));
        assertEquals(0, usedBefore.compareTo(amount(
                "select used_amount from salary_advance_limits where id=?",
                fixture.limitId()
        )));
        assertEquals(0, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_application_id=? "
                        + "and movement_type='REPAID_RELEASED'",
                fixture.applicationId()
        ));
        assertEquals(accountHistoryBefore, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        ));
        assertEquals(installmentHistoryBefore, count(
                "select count(*) from repayment_installment_status_transitions history "
                        + "join repayment_schedule_items item "
                        + "on item.id=history.repayment_schedule_item_id "
                        + "join repayment_schedules schedule "
                        + "on schedule.id=item.repayment_schedule_id "
                        + "where schedule.loan_account_id=?",
                activation.loanAccountId()
        ));
        assertEquals(auditBefore, count("select count(*) from audit_events"));
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

    private static AuthenticatedUser accountingActor() {
        return new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting@meridian.local",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse")
        );
    }

    private static AuthenticatedUser approverActor() {
        return new AuthenticatedUser(
                APPROVER_USER_ID,
                "approver@meridian.local",
                "STAFF",
                null,
                Set.of("APPROVER"),
                Set.of("loan:settlement:approve")
        );
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock settlementClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-01T10:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
