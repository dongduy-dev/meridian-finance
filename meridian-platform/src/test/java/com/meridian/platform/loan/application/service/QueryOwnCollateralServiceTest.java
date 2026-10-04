package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CustomerCollateralDto;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryOwnCollateralServiceTest {

    private static final UUID APP = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final LocalDateTime SUBMITTED = LocalDateTime.of(2026, 10, 5, 8, 0);
    @Mock LoanApplicationRepository applications;
    @Mock CollateralRepository collaterals;
    @Mock CurrentUserProvider users;
    private QueryOwnCollateralService service;

    @BeforeEach
    void setUp() {
        service = new QueryOwnCollateralService(applications, collaterals, users);
    }

    @ParameterizedTest
    @EnumSource(OriginationChannel.class)
    void returnsOnlySubmittedFactsForEitherChannelWithoutWritesOrLocks(OriginationChannel channel) {
        when(users.currentUser()).thenReturn(customer(CUSTOMER, Set.of("loan:read:own")));
        when(applications.findById(APP)).thenReturn(Optional.of(application(channel, ProductCode.COLLATERAL_LOAN)));
        when(collaterals.findByLoanApplicationId(APP)).thenReturn(List.of(collateral(APP)));

        assertEquals(new CustomerCollateralDto("MOTORBIKE", "Submitted motorbike",
                BigDecimal.valueOf(35_000_000), "Customer owned", "Normal used condition"), service.query(APP));
        verify(applications).findById(APP);
        verify(collaterals).findByLoanApplicationId(APP);
        verifyNoMoreInteractions(applications, collaterals);
    }

    @Test
    void foreignAndMissingApplicationsAreConcealedBeforeReadingCollateral() {
        when(users.currentUser()).thenReturn(customer(UUID.randomUUID(), Set.of("loan:read:own")));
        when(applications.findById(APP)).thenReturn(Optional.of(application(OriginationChannel.CUSTOMER_DIGITAL, ProductCode.COLLATERAL_LOAN)));
        var foreign = assertThrows(EntityNotFoundException.class, () -> service.query(APP));
        when(applications.findById(APP)).thenReturn(Optional.empty());
        var missing = assertThrows(EntityNotFoundException.class, () -> service.query(APP));
        assertEquals("LOAN_APPLICATION_NOT_FOUND", foreign.getErrorCode());
        assertEquals(foreign.getErrorCode(), missing.getErrorCode());
        assertEquals(foreign.getMessage(), missing.getMessage());
        verifyNoInteractions(collaterals);
    }

    @ParameterizedTest
    @EnumSource(value = ProductCode.class, names = {"SALARY_ADVANCE", "UNSECURED_CONSUMER_LOAN"})
    void otherProductsUseExistingNotFoundSemantics(ProductCode product) {
        when(users.currentUser()).thenReturn(customer(CUSTOMER, Set.of("loan:read:own")));
        when(applications.findById(APP)).thenReturn(Optional.of(application(OriginationChannel.CUSTOMER_DIGITAL, product)));
        assertEquals("LOAN_APPLICATION_NOT_FOUND",
                assertThrows(EntityNotFoundException.class, () -> service.query(APP)).getErrorCode());
        verifyNoInteractions(collaterals);
    }

    @Test
    void zeroMultipleAndMismatchedRowsFailClosed() {
        when(users.currentUser()).thenReturn(customer(CUSTOMER, Set.of("loan:read:own")));
        when(applications.findById(APP)).thenReturn(Optional.of(application(OriginationChannel.CUSTOMER_DIGITAL, ProductCode.COLLATERAL_LOAN)));
        for (List<Collateral> facts : List.of(List.<Collateral>of(),
                List.of(collateral(APP), collateral(APP)), List.of(collateral(UUID.randomUUID())))) {
            when(collaterals.findByLoanApplicationId(APP)).thenReturn(facts);
            assertEquals("SYSTEM_STATE_CONFLICT",
                    assertThrows(BusinessStateConflictException.class, () -> service.query(APP)).getErrorCode());
        }
        verify(applications, never()).save(any());
        verify(applications, never()).acquireWorkflowLock(any());
        verify(collaterals, never()).save(any());
    }

    @Test
    void customerShapeContextAndExactPermissionAreRequiredBeforeRepositoryAccess() {
        for (AuthenticatedUser actor : List.of(
                customer(CUSTOMER, Set.of("loan:read")),
                customer(null, Set.of("loan:read:own")),
                new AuthenticatedUser(UUID.randomUUID(), "staff@example.test", "STAFF", null,
                        Set.of("LOAN_OFFICER"), Set.of("loan:read")),
                new AuthenticatedUser(UUID.randomUUID(), "staff@example.test", "STAFF", CUSTOMER,
                        Set.of("LOAN_OFFICER"), Set.of("loan:read:own")),
                new AuthenticatedUser(UUID.randomUUID(), "staff@example.test", "STAFF", null,
                        Set.of("LOAN_OFFICER"), Set.of("loan:read:own")))) {
            when(users.currentUser()).thenReturn(actor);
            assertEquals("LOAN_APPLICATION_ACCESS_DENIED",
                    assertThrows(AuthorizationException.class, () -> service.query(APP)).getErrorCode());
        }
        verifyNoInteractions(applications, collaterals);
    }

    @Test
    void queryDeclaresReadOnlyTransactionAndHasNoAuditOrWorkflowDependencies() throws Exception {
        assertTrue(QueryOwnCollateralService.class.getMethod("query", UUID.class)
                .getAnnotation(Transactional.class).readOnly());
        assertEquals(Set.of(LoanApplicationRepository.class, CollateralRepository.class, CurrentUserProvider.class),
                Set.of(QueryOwnCollateralService.class.getConstructors()[0].getParameterTypes()));
    }

    private static AuthenticatedUser customer(UUID id, Set<String> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), "customer@example.test", "CUSTOMER", id,
                Set.of("CUSTOMER"), permissions);
    }

    private static LoanApplication application(OriginationChannel channel, ProductCode product) {
        ProductType type = switch (product) {
            case COLLATERAL_LOAN -> ProductType.SECURED;
            case UNSECURED_CONSUMER_LOAN -> ProductType.UNSECURED;
            case SALARY_ADVANCE -> ProductType.SALARY_BASED;
        };
        return new LoanApplication(APP, CUSTOMER, UUID.randomUUID(), "CL-20261005-000001", product,
                type, channel, LoanApplicationStatus.SUBMITTED, BigDecimal.valueOf(25_000_000), 12, SUBMITTED);
    }

    private static Collateral collateral(UUID applicationId) {
        return new Collateral(UUID.randomUUID(), applicationId, CollateralType.MOTORBIKE,
                "Submitted motorbike", BigDecimal.valueOf(35_000_000), "Customer owned", "Normal used condition", SUBMITTED);
    }
}
