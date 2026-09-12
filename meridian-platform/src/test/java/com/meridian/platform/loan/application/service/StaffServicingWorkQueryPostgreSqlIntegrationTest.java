package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffServicingWorkUseCase;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.FIRST_REPAYMENT_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.loan.overdue-evaluation.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class StaffServicingWorkQueryPostgreSqlIntegrationTest {

    private static final String SCHEMA = "staff_servicing_work_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired EvaluateLoanAccountOverdueUseCase overdueEvaluator;
    @Autowired QueryStaffServicingWorkUseCase servicingWork;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;
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
        clock.set(Instant.parse("2026-07-28T10:00:00Z"));
        support = new ManualDisbursementActivationPostgreSqlTestSupport(jdbc, transactionManager);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID,
                "accounting@meridian.test",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse", "loan:read")
        ));
    }

    @Test
    void filtersAndPagesServiceableAccountsWithDeterministicPostgreSqlOrdering() {
        var olderActive = activate(ProductCode.UNSECURED_CONSUMER_LOAN, "SERVICING-A");
        clock.set(Instant.parse("2026-07-28T10:00:01Z"));
        var newerActive = activate(ProductCode.SALARY_ADVANCE, "SERVICING-B");
        clock.set(Instant.parse("2026-07-28T10:00:02Z"));
        var overdue = activate(ProductCode.UNSECURED_CONSUMER_LOAN, "SERVICING-C");

        markOverdue(overdue);

        var firstPage = servicingWork.queryWork(null, null, 0, 2);
        assertEquals(3, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(overdue.applicationId(), firstPage.items().getFirst().loanApplicationId());
        assertEquals(newerActive.applicationId(), firstPage.items().get(1).loanApplicationId());

        var overdueOnly = servicingWork.queryWork(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanAccountStatus.OVERDUE,
                0,
                25
        );
        assertEquals(1, overdueOnly.totalElements());
        assertEquals(overdue.applicationId(), overdueOnly.items().getFirst().loanApplicationId());
        assertEquals("OVERDUE", overdueOnly.items().getFirst().accountStatus());
        assertEquals("DISBURSED", jdbc.queryForObject(
                "select status from loan_applications where id = ?",
                String.class,
                overdue.applicationId()
        ));
    }

    private Activated activate(
            ProductCode productCode,
            String reference
    ) {
        var fixture = support.createFixture(true, productCode);
        var activation = disbursements.confirm(support.command(
                fixture,
                UUID.randomUUID(),
                reference + "-" + fixture.token()
        ));
        return new Activated(fixture.applicationId(), activation.loanAccountId());
    }

    private void markOverdue(Activated activated) {
        var evaluationDate = FIRST_REPAYMENT_DATE.plusDays(1);
        overdueEvaluator.evaluate(new EvaluateLoanAccountOverdueUseCase.Command(
                activated.applicationId(),
                activated.accountId(),
                evaluationDate,
                evaluationDate.atStartOfDay()
        ));
    }

    private record Activated(UUID applicationId, UUID accountId) {
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock staffServicingWorkClock() {
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
