package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
@org.springframework.context.annotation.Import(
        CloseLoanAccountRollbackPostgreSqlIntegrationTest.FixedClockConfiguration.class
)
class CloseLoanAccountRollbackPostgreSqlIntegrationTest {

    private static final String SCHEMA = "closure_rollback_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 9, 2);

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired CloseLoanAccountUseCase closures;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
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
        reset(auditPublisher);
        support = new ManualDisbursementActivationPostgreSqlTestSupport(
                jdbc,
                transactionManager
        );
        when(currentUserProvider.currentUser()).thenReturn(accountingActor());
    }

    @Test
    void lateAuditFailureRollsBackEveryAdministrativeClosureEffect() {
        var fixture = support.createFixture(true, ProductCode.SALARY_ADVANCE);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                "CLOSURE-ROLLBACK-" + fixture.token()
        ));
        BigDecimal outstanding = amount(
                "select total_outstanding from loan_accounts where id=?",
                activation.loanAccountId()
        );
        repayments.record(new RecordRepaymentUseCase.Command(
                UUID.randomUUID(),
                fixture.applicationId(),
                "CLOSURE-ROLLBACK-PAYOFF-" + fixture.token(),
                outstanding,
                PAYMENT_DATE
        ));

        String accountBefore = accountFingerprint(activation.loanAccountId());
        String progressBefore = progressFingerprint(activation.loanAccountId());
        int transactionsBefore = count(
                "select count(*) from repayment_transactions where loan_account_id=?",
                activation.loanAccountId()
        );
        int allocationsBefore = count(
                "select count(*) from repayment_allocations allocation "
                        + "join repayment_transactions transaction_row "
                        + "on transaction_row.id=allocation.repayment_transaction_id "
                        + "where transaction_row.loan_account_id=?",
                activation.loanAccountId()
        );
        int movementsBefore = count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        );
        int historyBefore = count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        );
        int auditBefore = count("select count(*) from audit_events");
        doThrow(new IllegalStateException("injected closure audit failure"))
                .when(auditPublisher).publish(any());

        assertThrows(IllegalStateException.class, () -> closures.close(
                new CloseLoanAccountUseCase.Command(
                        UUID.randomUUID(),
                        fixture.applicationId()
                )
        ));

        assertEquals(0, count(
                "select count(*) from loan_account_closures where loan_account_id=?",
                activation.loanAccountId()
        ));
        assertEquals("SETTLED", jdbc.queryForObject(
                "select status from loan_accounts where id=?",
                String.class,
                activation.loanAccountId()
        ));
        assertEquals(accountBefore, accountFingerprint(activation.loanAccountId()));
        assertEquals(progressBefore, progressFingerprint(activation.loanAccountId()));
        assertEquals(transactionsBefore, count(
                "select count(*) from repayment_transactions where loan_account_id=?",
                activation.loanAccountId()
        ));
        assertEquals(allocationsBefore, count(
                "select count(*) from repayment_allocations allocation "
                        + "join repayment_transactions transaction_row "
                        + "on transaction_row.id=allocation.repayment_transaction_id "
                        + "where transaction_row.loan_account_id=?",
                activation.loanAccountId()
        ));
        assertEquals(movementsBefore, count(
                "select count(*) from salary_advance_limit_movements "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        ));
        assertEquals(historyBefore, count(
                "select count(*) from loan_account_status_transitions "
                        + "where loan_account_id=?",
                activation.loanAccountId()
        ));
        assertEquals(auditBefore, count("select count(*) from audit_events"));
    }

    private String accountFingerprint(UUID accountId) {
        return jdbc.queryForObject(
                "select concat_ws('|',principal_paid,interest_paid,fee_paid,"
                        + "total_paid,principal_outstanding,interest_outstanding,"
                        + "fee_outstanding,total_outstanding,last_payment_value_date,"
                        + "last_payment_recorded_at,servicing_evaluation_date) "
                        + "from loan_accounts where id=?",
                String.class,
                accountId
        );
    }

    private String progressFingerprint(UUID accountId) {
        return jdbc.queryForObject(
                "select string_agg(concat_ws('|',installment_number,status,"
                        + "principal_paid,interest_paid,fee_paid,total_paid,"
                        + "total_outstanding),',' order by installment_number) "
                        + "from repayment_installment_progress where loan_account_id=?",
                String.class,
                accountId
        );
    }

    private BigDecimal amount(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, BigDecimal.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private static AuthenticatedUser accountingActor() {
        return new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting@meridian.test",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse", "repayment:update", "loan:account:close")
        );
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock closureClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-02T10:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
