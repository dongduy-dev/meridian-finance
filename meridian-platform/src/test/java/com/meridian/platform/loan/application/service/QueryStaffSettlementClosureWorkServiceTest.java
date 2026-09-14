package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.StaffSettlementClosureWorkQuery;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffSettlementClosureWorkServiceTest {
    @Mock StaffSettlementClosureWorkQuery query;
    @Mock CurrentUserProvider users;

    private QueryStaffSettlementWorkService settlements;
    private QueryStaffClosureWorkService closures;

    @BeforeEach
    void setUp() {
        settlements = new QueryStaffSettlementWorkService(query, users);
        closures = new QueryStaffClosureWorkService(query, users);
    }

    @Test
    void settlementRequiresExactPermissionAndApproverRole() {
        when(users.currentUser()).thenReturn(actor(
                "STAFF", null, Set.of("APPROVER"), Set.of("loan:settlement:approve")
        ));
        when(query.findSettlementPage(null, 0, 25)).thenReturn(page(List.of(
                row(LoanApplicationStatus.DISBURSED, LoanAccountStatus.ACTIVE,
                        money("1000"), true, null)
        )));
        assertEquals(1, settlements.queryWork(null, 0, 25).totalElements());

        for (AuthenticatedUser denied : List.of(
                actor("STAFF", null, Set.of("ACCOUNTING_OFFICER"),
                        Set.of("loan:settlement:approve")),
                actor("STAFF", null, Set.of("APPROVER"), Set.of("loan:read")),
                actor("CUSTOMER", UUID.randomUUID(), Set.of("APPROVER"),
                        Set.of("loan:settlement:approve"))
        )) {
            when(users.currentUser()).thenReturn(denied);
            assertThrows(AuthorizationException.class,
                    () -> settlements.queryWork(null, 0, 25));
        }
    }

    @Test
    void closureRequiresExactPermissionAndAccountingRole() {
        when(users.currentUser()).thenReturn(actor(
                "STAFF", null, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:account:close")
        ));
        when(query.findClosurePage(ProductCode.SALARY_ADVANCE, 1, 10))
                .thenReturn(new StaffSettlementClosureWorkQuery.Page(1, 10, 11, 2,
                        List.of(row(LoanApplicationStatus.DISBURSED,
                                LoanAccountStatus.SETTLED, BigDecimal.ZERO, true,
                                "APPROVED_SETTLEMENT"))));
        var result = closures.queryWork(ProductCode.SALARY_ADVANCE, 1, 10);
        assertEquals("APPROVED_SETTLEMENT", result.items().getFirst().payoffProvenance());

        for (AuthenticatedUser denied : List.of(
                actor("STAFF", null, Set.of("APPROVER"),
                        Set.of("loan:account:close")),
                actor("STAFF", null, Set.of("ACCOUNTING_OFFICER"),
                        Set.of("loan:read")),
                actor("CUSTOMER", UUID.randomUUID(), Set.of("ACCOUNTING_OFFICER"),
                        Set.of("loan:account:close"))
        )) {
            when(users.currentUser()).thenReturn(denied);
            assertThrows(AuthorizationException.class,
                    () -> closures.queryWork(null, 0, 25));
        }
    }

    @Test
    void contradictoryCandidateEvidenceFailsClosed() {
        when(users.currentUser()).thenReturn(actor(
                "STAFF", null, Set.of("APPROVER"), Set.of("loan:settlement:approve")
        ));
        when(query.findSettlementPage(null, 0, 25)).thenReturn(page(List.of(
                row(LoanApplicationStatus.DISBURSED, LoanAccountStatus.OVERDUE,
                        money("1000"), false, null)
        )));
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(
                BusinessStateConflictException.class,
                () -> settlements.queryWork(null, 0, 25)
        ).getErrorCode());

        when(users.currentUser()).thenReturn(actor(
                "STAFF", null, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:account:close")
        ));
        when(query.findClosurePage(null, 0, 25)).thenReturn(page(List.of(
                row(LoanApplicationStatus.DISBURSED, LoanAccountStatus.SETTLED,
                        BigDecimal.ZERO, false, "CONTRACTUAL_PAYOFF")
        )));
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(
                BusinessStateConflictException.class,
                () -> closures.queryWork(null, 0, 25)
        ).getErrorCode());
    }

    @Test
    void invalidPagingNeverReachesPersistence() {
        when(users.currentUser()).thenReturn(actor(
                "STAFF", null, Set.of("APPROVER"), Set.of("loan:settlement:approve")
        ));
        assertThrows(IllegalArgumentException.class,
                () -> settlements.queryWork(null, -1, 25));
        assertThrows(IllegalArgumentException.class,
                () -> settlements.queryWork(null, 0, 101));
        verify(query, never()).findSettlementPage(null, -1, 25);
    }

    private static StaffSettlementClosureWorkQuery.Page page(
            List<StaffSettlementClosureWorkQuery.Row> rows
    ) {
        return new StaffSettlementClosureWorkQuery.Page(0, 25, rows.size(),
                rows.isEmpty() ? 0 : 1, rows);
    }

    private static StaffSettlementClosureWorkQuery.Row row(
            LoanApplicationStatus applicationStatus,
            LoanAccountStatus accountStatus,
            BigDecimal outstanding,
            boolean coherent,
            String provenance
    ) {
        return new StaffSettlementClosureWorkQuery.Row(
                UUID.randomUUID(), UUID.randomUUID(), "UCL-20260914-000001",
                "LA-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                applicationStatus, accountStatus, LocalDateTime.of(2026, 9, 1, 10, 0),
                money("500"), outstanding, LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 13), LocalDateTime.of(2026, 9, 13, 9, 0),
                coherent, provenance
        );
    }

    private static AuthenticatedUser actor(
            String type, UUID customerId, Set<String> roles, Set<String> permissions
    ) {
        return new AuthenticatedUser(UUID.randomUUID(), "staff@meridian.test", type,
                customerId, roles, permissions);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
