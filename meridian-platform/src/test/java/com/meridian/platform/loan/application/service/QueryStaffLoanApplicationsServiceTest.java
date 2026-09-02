package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffLoanApplicationsServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString(
            "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
    );
    private static final UUID CUSTOMER_ID = UUID.fromString(
            "99999999-9999-4999-8999-999999999999"
    );

    @Mock LoanApplicationRepository applications;
    @Mock LoanApplicationStatusTransitionRepository transitions;
    @Mock CustomerReadinessPort customerReadiness;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffLoanApplicationsService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffLoanApplicationsService(
                applications,
                transitions,
                customerReadiness,
                currentUserProvider
        );
    }

    @Test
    void returnsPagedSafeApplicationFactsWithRequestedFilters() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.UNDER_REVIEW,
                2,
                20
        )).thenReturn(new LoanApplicationRepository.StaffPage(
                2, 20, 43, 3, List.of(application())
        ));

        var result = service.queryApplications(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.UNDER_REVIEW,
                2,
                20
        );

        assertEquals(2, result.page());
        assertEquals(20, result.size());
        assertEquals(43, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(APPLICATION_ID, result.items().getFirst().loanApplicationId());
        assertEquals("UNDER_REVIEW", result.items().getFirst().status());
    }

    @Test
    void rejectsInvalidPageArgumentsBeforeRepositoryAccess() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.queryApplications(null, null, -1, 20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.queryApplications(null, null, 0, 101)
        );

        verify(applications, never()).findStaffPage(null, null, -1, 20);
    }

    @Test
    void customerAndUnsupportedStaffCannotQueryTheStaffIndex() {
        when(currentUserProvider.currentUser()).thenReturn(customer(Set.of("loan:read:own")));
        assertThrows(
                AuthorizationException.class,
                () -> service.queryApplications(null, null, 0, 20)
        );

        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read:all")));
        assertThrows(
                AuthorizationException.class,
                () -> service.queryApplications(null, null, 0, 20)
        );
    }

    @Test
    void composesPurposeLimitedReadinessAndOrderedSafeLifecycleEvidence() {
        LoanApplication application = application();
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID))
                .thenReturn(List.of(initialTransition(), reviewTransition()));

        var result = service.queryCase(APPLICATION_ID);

        assertEquals(APPLICATION_ID, result.loanApplicationId());
        assertEquals("VERIFIED", result.customerReadiness().verificationStatus());
        assertEquals(2, result.lifecycleHistory().size());
        assertNull(result.lifecycleHistory().getFirst().fromStatus());
        assertEquals("SUBMIT_APPLICATION", result.lifecycleHistory().getFirst().action());
        assertEquals("START_REVIEW", result.lifecycleHistory().getLast().action());
        assertEquals("USER", result.lifecycleHistory().getLast().actorType());
    }

    @Test
    void missingApplicationAndReadinessFailureRemainSafe() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.empty());
        EntityNotFoundException missing = assertThrows(
                EntityNotFoundException.class,
                () -> service.queryCase(APPLICATION_ID)
        );
        assertEquals("LOAN_APPLICATION_NOT_FOUND", missing.getErrorCode());

        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.empty());
        BusinessStateConflictException unavailable = assertThrows(
                BusinessStateConflictException.class,
                () -> service.queryCase(APPLICATION_ID)
        );
        assertEquals("SYSTEM_STATE_CONFLICT", unavailable.getErrorCode());
    }

    private static LoanApplication application() {
        return new LoanApplication(
                APPLICATION_ID,
                CUSTOMER_ID,
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                "UCL-20260902-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                LoanApplicationStatus.UNDER_REVIEW,
                new BigDecimal("12000000.00"),
                6,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
    }

    private static LoanApplicationStatusTransition initialTransition() {
        return transition(
                1,
                null,
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationTransitionAction.SUBMIT_APPLICATION,
                ActorType.SYSTEM,
                null,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
    }

    private static LoanApplicationStatusTransition reviewTransition() {
        return transition(
                2,
                LoanApplicationStatus.SUBMITTED,
                LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationTransitionAction.START_REVIEW,
                ActorType.USER,
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                LocalDateTime.of(2026, 9, 2, 9, 0)
        );
    }

    private static LoanApplicationStatusTransition transition(
            int sequence,
            LoanApplicationStatus fromStatus,
            LoanApplicationStatus toStatus,
            LoanApplicationTransitionAction action,
            ActorType actorType,
            UUID actorUserId,
            LocalDateTime occurredAt
    ) {
        return new LoanApplicationStatusTransition(
                UUID.randomUUID(),
                APPLICATION_ID,
                UUID.randomUUID(),
                sequence,
                fromStatus,
                toStatus,
                action,
                "restricted reason",
                actorType,
                actorUserId,
                occurredAt
        );
    }

    private static AuthenticatedUser staff(Set<String> permissions) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "staff@meridian.test",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                permissions
        );
    }

    private static AuthenticatedUser customer(Set<String> permissions) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "customer@meridian.test",
                "CUSTOMER",
                CUSTOMER_ID,
                Set.of("CUSTOMER"),
                permissions
        );
    }
}
