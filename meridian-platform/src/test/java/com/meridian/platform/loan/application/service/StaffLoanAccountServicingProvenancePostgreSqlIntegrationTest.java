package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanAccountServicingProvenanceUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import com.meridian.platform.loan.application.port.out.StaffActorDirectoryPort;
import com.meridian.platform.loan.application.port.out.StaffActorSummary;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.ACCOUNTING_USER_ID;
import static com.meridian.platform.loan.application.service.ManualDisbursementActivationPostgreSqlTestSupport.VALUE_DATE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Import(RecordRepaymentPostgreSqlIntegrationTest.FixedClockConfiguration.class)
class StaffLoanAccountServicingProvenancePostgreSqlIntegrationTest {
    private static final String SCHEMA = "staff_servicing_provenance_"
            + UUID.randomUUID().toString().replace("-", "");

    @Autowired ConfirmManualDisbursementUseCase disbursements;
    @Autowired RecordRepaymentUseCase repayments;
    @Autowired QueryStaffLoanAccountServicingProvenanceUseCase provenance;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean StaffActorDirectoryPort staffActors;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void exactStoredPaymentActorNeedsNoStatusChangeAndActivationHistoryIsImmutable() {
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                ACCOUNTING_USER_ID, "accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse", "loan:read", "repayment:update")));
        when(staffActors.findByUserIds(anySet())).thenAnswer(invocation -> {
            Set<UUID> ids = invocation.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                    id -> new StaffActorSummary(id, "Accounting Officer", "accounting@meridian.test")));
        });
        var support = new ManualDisbursementActivationPostgreSqlTestSupport(jdbc, transactionManager);
        var fixture = support.createFixture(true, ProductCode.UNSECURED_CONSUMER_LOAN);
        var activation = disbursements.confirm(support.command(fixture, UUID.randomUUID(),
                "PROVENANCE-" + fixture.token()));
        var payment = repayments.record(new RecordRepaymentUseCase.Command(UUID.randomUUID(),
                fixture.applicationId(), "PAY-" + fixture.token(), new BigDecimal("100.00"), VALUE_DATE));

        var result = provenance.query(fixture.applicationId(), 0, 20);
        assertEquals(activation.loanAccountId(), result.loanAccountId());
        assertEquals(ACCOUNTING_USER_ID, result.originatingDisbursement().actor().staff().userId());
        assertEquals(payment.repaymentTransactionId(), result.repaymentHistory().items().getFirst()
                .financial().repaymentTransactionId());
        assertEquals(ACCOUNTING_USER_ID, result.repaymentHistory().items().getFirst()
                .actor().staff().userId());
        assertEquals(1, result.statusHistory().size());

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update loan_account_status_transitions set occurred_at = "
                        + "occurred_at + interval '1 second' "
                        + "where loan_account_id = ? and sequence_number = 1",
                activation.loanAccountId()));
    }
}
