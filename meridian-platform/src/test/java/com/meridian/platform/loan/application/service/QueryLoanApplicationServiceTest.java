package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class QueryLoanApplicationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID APPLICATION_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock LoanApplicationRepository applications;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryLoanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new QueryLoanApplicationService(applications, currentUserProvider);
        lenient().when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application(CUSTOMER_ID)));
    }

    @Test
    void customerReadsOwnMinimalStatusProjection() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));

        LoanApplicationStatusDto result = service.query(APPLICATION_ID);

        assertEquals(APPLICATION_ID, result.loanApplicationId());
        assertEquals("SALARY_ADVANCE", result.productCode());
        assertEquals("UNDER_REVIEW", result.status());
        verify(applications, never()).save(any());
        verify(applications, never()).acquireWorkflowLock(any());
    }

    @Test
    void foreignCustomerAndMissingApplicationUseTheSameConcealedNotFoundCode() {
        when(currentUserProvider.currentUser()).thenReturn(customer(UUID.randomUUID()));
        EntityNotFoundException foreign = assertThrows(
                EntityNotFoundException.class,
                () -> service.query(APPLICATION_ID)
        );

        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.empty());
        EntityNotFoundException missing = assertThrows(
                EntityNotFoundException.class,
                () -> service.query(APPLICATION_ID)
        );

        assertEquals("LOAN_APPLICATION_NOT_FOUND", foreign.getErrorCode());
        assertEquals(foreign.getErrorCode(), missing.getErrorCode());
    }

    @Test
    void staffWithLoanReadCanReadApplication() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));

        assertEquals("UNDER_REVIEW", service.query(APPLICATION_ID).status());
    }

    @Test
    void unrelatedPermissionsDoNotGrantReadAccess() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("approval:decide")));

        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> service.query(APPLICATION_ID)
        );

        assertEquals("LOAN_APPLICATION_ACCESS_DENIED", exception.getErrorCode());
    }

    private static LoanApplication application(UUID customerId) {
        return new LoanApplication(
                APPLICATION_ID,
                customerId,
                UUID.randomUUID(),
                "SA-20260810-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.UNDER_REVIEW,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                LocalDateTime.of(2026, 8, 10, 8, 0)
        );
    }

    private static AuthenticatedUser customer(UUID customerId) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("loan:read:own")
        );
    }

    private static AuthenticatedUser staff(Set<String> permissions) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), permissions
        );
    }
}
