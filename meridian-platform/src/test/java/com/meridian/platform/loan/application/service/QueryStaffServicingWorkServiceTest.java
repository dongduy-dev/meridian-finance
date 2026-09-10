package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.StaffLoanAccountWorkQuery;
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
class QueryStaffServicingWorkServiceTest {

    @Mock StaffLoanAccountWorkQuery workQuery;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffServicingWorkService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffServicingWorkService(workQuery, currentUserProvider);
    }

    @Test
    void returnsServerOwnedServiceableRowsWithFiltersAndPaging() {
        when(currentUserProvider.currentUser()).thenReturn(staff("STAFF", null, Set.of("loan:read")));
        when(workQuery.findPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanAccountStatus.OVERDUE,
                1,
                25
        )).thenReturn(new StaffLoanAccountWorkQuery.Page(1, 25, 26, 2, List.of(row(
                LoanApplicationStatus.DISBURSED,
                LoanAccountStatus.OVERDUE,
                new BigDecimal("900.00")
        ))));

        var result = service.queryWork(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanAccountStatus.OVERDUE,
                1,
                25
        );

        assertEquals(26, result.totalElements());
        assertEquals("OVERDUE", result.items().getFirst().accountStatus());
        assertEquals("UNSECURED_CONSUMER_LOAN", result.items().getFirst().productCode());
        verify(workQuery).findPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanAccountStatus.OVERDUE,
                1,
                25
        );
    }

    @Test
    void omittedStatusPassesBothServiceableStatesToTheServerQuery() {
        when(currentUserProvider.currentUser()).thenReturn(staff("STAFF", null, Set.of("loan:read")));
        when(workQuery.findPage(null, null, 0, 25))
                .thenReturn(new StaffLoanAccountWorkQuery.Page(0, 25, 2, 1, List.of(
                        row(LoanApplicationStatus.DISBURSED, LoanAccountStatus.ACTIVE,
                                new BigDecimal("1000.00")),
                        row(LoanApplicationStatus.DISBURSED, LoanAccountStatus.OVERDUE,
                                new BigDecimal("800.00"))
                )));

        var result = service.queryWork(null, null, 0, 25);

        assertEquals(List.of("ACTIVE", "OVERDUE"), result.items().stream()
                .map(item -> item.accountStatus()).toList());
    }

    @Test
    void settledClosedAndInvalidPagingAreRejectedBeforeQueryAccess() {
        when(currentUserProvider.currentUser()).thenReturn(staff("STAFF", null, Set.of("loan:read")));

        assertThrows(IllegalArgumentException.class,
                () -> service.queryWork(null, LoanAccountStatus.SETTLED, 0, 25));
        assertThrows(IllegalArgumentException.class,
                () -> service.queryWork(null, LoanAccountStatus.CLOSED, 0, 25));
        assertThrows(IllegalArgumentException.class,
                () -> service.queryWork(null, null, -1, 25));
        assertThrows(IllegalArgumentException.class,
                () -> service.queryWork(null, null, 0, 101));

        verify(workQuery, never()).findPage(null, null, 0, 25);
    }

    @Test
    void exactStaffReadAuthorityIsRequiredWithoutRoleRequirement() {
        when(currentUserProvider.currentUser()).thenReturn(staff(
                "STAFF", null, Set.of("loan:read")
        ));
        when(workQuery.findPage(null, null, 0, 25))
                .thenReturn(new StaffLoanAccountWorkQuery.Page(0, 25, 0, 0, List.of()));
        service.queryWork(null, null, 0, 25);

        for (AuthenticatedUser denied : List.of(
                staff("STAFF", null, Set.of("repayment:update")),
                staff("CUSTOMER", UUID.randomUUID(), Set.of("loan:read"))
        )) {
            when(currentUserProvider.currentUser()).thenReturn(denied);
            assertThrows(AuthorizationException.class,
                    () -> service.queryWork(null, null, 0, 25));
        }
    }

    @Test
    void impossibleServiceableRowsFailClosed() {
        when(currentUserProvider.currentUser()).thenReturn(staff("STAFF", null, Set.of("loan:read")));
        for (StaffLoanAccountWorkQuery.Row invalid : List.of(
                row(LoanApplicationStatus.CONTRACT_PENDING, LoanAccountStatus.ACTIVE,
                        new BigDecimal("1000.00")),
                row(LoanApplicationStatus.DISBURSED, LoanAccountStatus.SETTLED,
                        BigDecimal.ZERO),
                row(LoanApplicationStatus.DISBURSED, LoanAccountStatus.ACTIVE,
                        BigDecimal.ZERO)
        )) {
            when(workQuery.findPage(null, null, 0, 25))
                    .thenReturn(new StaffLoanAccountWorkQuery.Page(0, 25, 1, 1,
                            List.of(invalid)));
            BusinessStateConflictException exception = assertThrows(
                    BusinessStateConflictException.class,
                    () -> service.queryWork(null, null, 0, 25)
            );
            assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
        }
    }

    private static StaffLoanAccountWorkQuery.Row row(
            LoanApplicationStatus applicationStatus,
            LoanAccountStatus accountStatus,
            BigDecimal outstanding
    ) {
        return new StaffLoanAccountWorkQuery.Row(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "UCL-20260910-000001",
                "LA-20260910-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                applicationStatus,
                accountStatus,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                outstanding,
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 9),
                LocalDateTime.of(2026, 9, 9, 8, 0)
        );
    }

    private static AuthenticatedUser staff(
            String userType,
            UUID customerId,
            Set<String> permissions
    ) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "staff@meridian.test",
                userType,
                customerId,
                Set.of("LOAN_OFFICER"),
                permissions
        );
    }
}
