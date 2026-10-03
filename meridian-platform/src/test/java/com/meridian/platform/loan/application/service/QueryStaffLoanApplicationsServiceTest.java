package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.application.port.out.CustomerLoanCaseContactPort;
import com.meridian.platform.loan.application.port.out.CustomerLoanCaseContactSnapshot;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort;
import com.meridian.platform.loan.application.port.out.StaffActorSummary;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralType;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock CustomerLoanCaseContactPort customerContacts;
    @Mock CollateralRepository collaterals;
    @Mock LoanReviewCycleRepository reviewCycles;
    @Mock WorkflowActorDirectoryPort staffActors;
    @Mock AssistedCustomerActionProvenanceComposer assistedActions;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffLoanApplicationsService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffLoanApplicationsService(
                applications,
                transitions,
                customerReadiness,
                customerContacts,
                collaterals,
                reviewCycles,
                staffActors,
                assistedActions,
                currentUserProvider
        );
        org.mockito.Mockito.lenient().when(staffActors.findByUserIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
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
        UUID reviewActorId = reviewTransition().actorUserId();
        when(reviewCycles.findLatestByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(
                LoanApplicationReviewCycle.active(
                        UUID.randomUUID(), APPLICATION_ID, 1, reviewActorId,
                        LocalDateTime.of(2026, 9, 2, 9, 0)
                )
        ));
        when(staffActors.findByUserIds(Set.of(reviewActorId))).thenReturn(Map.of(
                reviewActorId,
                new WorkflowActorDirectoryPort.ActorSummary(reviewActorId, "STAFF", null,
                        new StaffActorSummary(reviewActorId, "Deni Loan Officer", "deni@meridian.local"))
        ));

        var result = service.queryCase(APPLICATION_ID);

        assertEquals(APPLICATION_ID, result.loanApplicationId());
        assertEquals("VERIFIED", result.customerReadiness().verificationStatus());
        assertEquals(true, result.formalReviewRecorded());
        assertEquals("Deni Loan Officer", result.assignedLoanOfficer().displayName());
        assertEquals(2, result.lifecycleHistory().size());
        assertNull(result.lifecycleHistory().getFirst().fromStatus());
        assertEquals("SUBMIT_APPLICATION", result.lifecycleHistory().getFirst().action());
        assertEquals("START_REVIEW", result.lifecycleHistory().getLast().action());
        assertEquals("STAFF", result.lifecycleHistory().getLast().actorType());
        assertEquals("Deni Loan Officer", result.lifecycleHistory().getLast().actor().displayName());
        assertEquals("deni@meridian.local", result.lifecycleHistory().getLast().actor().email());
        assertNull(result.lifecycleHistory().getFirst().actor());
        assertNull(result.customerContext());
        assertNull(result.collateralContext());
        verifyNoInteractions(customerContacts, collaterals);
    }

    @Test
    void authorizedCustomerContactUsesOnlyTheNarrowProvider() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read", "customer:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        when(customerContacts.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerLoanCaseContactSnapshot("CUST-001", "Nguyen Van A", "0901234567", "****8901")
        ));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID))
                .thenReturn(List.of());

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("CUST-001", result.customerContext().customerNumber());
        assertEquals("Nguyen Van A", result.customerContext().fullName());
        assertEquals("0901234567", result.customerContext().phoneNumber());
        assertEquals(4, result.customerContext().getClass().getRecordComponents().length);
        assertNull(result.customerContext().maskedIdentityReference());
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read", "customer:read", "customer:identity:reveal")));
        assertEquals("****8901", service.queryCase(APPLICATION_ID).customerContext().maskedIdentityReference());
        for (String role : List.of("APPROVER", "ACCOUNTING_OFFICER", "BACK_OFFICE_ADMIN", "CUSTOM_ROLE")) {
            when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(UUID.randomUUID(), "fictional@meridian.test", "STAFF", null, Set.of(role), Set.of("loan:read", "customer:read", "customer:identity:reveal")));
            assertNull(service.queryCase(APPLICATION_ID).customerContext().maskedIdentityReference());
        }
        verify(customerContacts, org.mockito.Mockito.atLeastOnce()).findByCustomerId(CUSTOMER_ID);
    }

    @Test
    void approverAccountingAndBackOfficeDoNotGainGenericCustomerContext() {
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")));
        for (String role : List.of("APPROVER", "ACCOUNTING_OFFICER", "BACK_OFFICE_ADMIN")) {
            when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                    UUID.randomUUID(), "staff@meridian.test", "STAFF", null, Set.of(role), Set.of("loan:read")));
            assertNull(service.queryCase(APPLICATION_ID).customerContext());
        }
        verifyNoInteractions(customerContacts);
    }

    @Test
    void missingAuthorizedCustomerContactIsSystemConflict() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read", "customer:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        when(customerContacts.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        var error = assertThrows(BusinessStateConflictException.class, () -> service.queryCase(APPLICATION_ID));
        assertEquals("SYSTEM_STATE_CONFLICT", error.getErrorCode());
    }

    @Test
    void collateralFactsComeFromTheSingleLoanOwnedRecord() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(collateralApplication()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        when(collaterals.findByLoanApplicationId(APPLICATION_ID)).thenReturn(List.of(collateral()));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID))
                .thenReturn(List.of());

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("CAR", result.collateralContext().collateralType());
        assertEquals("Vehicle", result.collateralContext().description());
        assertEquals(new BigDecimal("3200000000"), result.collateralContext().estimatedValue());
        assertNull(result.customerContext());
        verifyNoInteractions(customerContacts);
    }

    @Test
    void approverAndAccountingCaseReadsDoNotQueryCustomerContacts() {
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID))
                .thenReturn(List.of());

        for (String role : List.of("APPROVER", "ACCOUNTING_OFFICER")) {
            when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                    UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                    Set.of(role), Set.of("loan:read", "repayment:update", "loan:account:close", "loan:settlement:approve")
            ));
            assertNull(service.queryCase(APPLICATION_ID).customerContext());
        }
        verifyNoInteractions(customerContacts);
    }

    @Test
    void salaryAdvanceHasNoCollateralContext() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(new LoanApplication(
                APPLICATION_ID, CUSTOMER_ID, UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                "SA-20260902-000001", ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED,
                LoanApplicationStatus.UNDER_REVIEW, new BigDecimal("3000000.00"), 1,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        )));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID))
                .thenReturn(List.of());

        assertNull(service.queryCase(APPLICATION_ID).collateralContext());
        verifyNoInteractions(collaterals);
    }

    @Test
    void missingOrMultipleCollateralFactsFailClosed() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(collateralApplication()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")
        ));
        when(collaterals.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(List.of(), List.of(collateral(), collateral()));

        for (int attempt = 0; attempt < 2; attempt++) {
            var error = assertThrows(BusinessStateConflictException.class, () -> service.queryCase(APPLICATION_ID));
            assertEquals("SYSTEM_STATE_CONFLICT", error.getErrorCode());
        }
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

    @Test
    void classifiesCustomerStaffSystemAndUnavailableInOneBatchWithoutExposingCustomerLogin() {
        UUID customerUserId = UUID.randomUUID(), missingId = UUID.randomUUID();
        UUID staffId = reviewTransition().actorUserId();
        prepareActorCase();
        var customerSubmission = transition(1, null, LoanApplicationStatus.SUBMITTED,
                LoanApplicationTransitionAction.SUBMIT_APPLICATION, ActorType.USER, customerUserId,
                LocalDateTime.of(2026, 9, 2, 8, 0));
        var customerResubmission = transition(3, LoanApplicationStatus.RETURNED_FOR_REVISION,
                LoanApplicationStatus.SUBMITTED, LoanApplicationTransitionAction.RESUBMIT_CORRECTION,
                ActorType.USER, customerUserId, LocalDateTime.of(2026, 9, 2, 10, 0));
        var unavailable = transition(4, LoanApplicationStatus.SUBMITTED, LoanApplicationStatus.UNDER_REVIEW,
                LoanApplicationTransitionAction.START_REVIEW, ActorType.USER, missingId,
                LocalDateTime.of(2026, 9, 2, 11, 0));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID)).thenReturn(
                List.of(customerSubmission, reviewTransition(), customerResubmission, unavailable, initialTransition()));
        when(staffActors.findByUserIds(Set.of(customerUserId, staffId, missingId))).thenReturn(Map.of(
                customerUserId, new WorkflowActorDirectoryPort.ActorSummary(customerUserId, "CUSTOMER", CUSTOMER_ID, null),
                staffId, new WorkflowActorDirectoryPort.ActorSummary(staffId, "STAFF", null,
                        new StaffActorSummary(staffId, "Deni Loan Officer", "deni@meridian.local"))));
        var result = service.queryCase(APPLICATION_ID);
        assertEquals(List.of("CUSTOMER_SELF_SERVICE", "STAFF", "CUSTOMER_SELF_SERVICE", "UNAVAILABLE", "SYSTEM"),
                result.lifecycleHistory().stream().map(item -> item.actorType()).toList());
        assertNull(result.lifecycleHistory().getFirst().actor());
        assertNull(result.lifecycleHistory().get(2).actor());
        String json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(result);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains(customerUserId.toString()));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("customer@meridian.test"));
        verify(staffActors).findByUserIds(Set.of(customerUserId, staffId, missingId));
        org.mockito.Mockito.verifyNoMoreInteractions(staffActors);
        verify(applications, never()).save(org.mockito.ArgumentMatchers.any());
        verify(transitions, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(customerContacts);
    }

    @Test
    void contradictoryCustomerAssociationFailsClosedInsteadOfUsingStaffLookupAbsence() {
        UUID userId = UUID.randomUUID();
        prepareActorCase();
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID)).thenReturn(List.of(
                transition(1, null, LoanApplicationStatus.SUBMITTED, LoanApplicationTransitionAction.SUBMIT_APPLICATION,
                        ActorType.USER, userId, LocalDateTime.of(2026, 9, 2, 8, 0))));
        when(staffActors.findByUserIds(Set.of(userId))).thenReturn(Map.of(userId,
                new WorkflowActorDirectoryPort.ActorSummary(userId, "CUSTOMER", UUID.randomUUID(), null)));
        var error = assertThrows(BusinessStateConflictException.class, () -> service.queryCase(APPLICATION_ID));
        assertEquals("SYSTEM_STATE_CONFLICT", error.getErrorCode());
        verify(applications, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void prepareActorCase() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
        when(customerReadiness.findReadinessByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerReadinessSnapshot(CUSTOMER_ID, true, true, true, "VERIFIED")));
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

    private static LoanApplication collateralApplication() {
        return new LoanApplication(
                APPLICATION_ID, CUSTOMER_ID, UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                "COL-20260902-000001", ProductCode.COLLATERAL_LOAN, ProductType.SECURED,
                LoanApplicationStatus.UNDER_REVIEW, new BigDecimal("12000000.00"), 6,
                LocalDateTime.of(2026, 9, 2, 8, 0)
        );
    }

    private static Collateral collateral() {
        return new Collateral(
                UUID.randomUUID(), APPLICATION_ID, CollateralType.CAR, "Vehicle",
                new BigDecimal("3200000000"), "Owner", "Very good",
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
