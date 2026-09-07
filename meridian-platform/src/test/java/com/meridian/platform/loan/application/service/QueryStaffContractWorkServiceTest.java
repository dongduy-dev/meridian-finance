package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.domain.model.ContractReadinessBlockerCode;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.testsupport.LoanContractTestData;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffContractWorkServiceTest {

    private static final UUID APPLICATION_ID = LoanContractTestData.APPLICATION_ID;

    @Mock LoanApplicationRepository applications;
    @Mock LoanContractRepository contracts;
    @Mock QueryContractReadinessUseCase readiness;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffContractWorkService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffContractWorkService(
                applications,
                contracts,
                readiness,
                new LoanContractMapper(),
                currentUserProvider
        );
    }

    @Test
    void queueUsesServerOwnedContractPendingMembershipProductFilterAndPaging() {
        LoanApplication application = application(LoanApplicationStatus.CONTRACT_PENDING);
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.CONTRACT_PENDING,
                1,
                25
        )).thenReturn(new LoanApplicationRepository.StaffPage(
                1, 25, 26, 2, List.of(application)
        ));
        when(contracts.findCurrentByApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
        when(readiness.query(APPLICATION_ID, null)).thenReturn(snapshot(
                null,
                null,
                false,
                ContractReadinessBlockerCode.CURRENT_CONTRACT_MISSING
        ));

        var result = service.queryWork(ProductCode.UNSECURED_CONSUMER_LOAN, 1, 25);

        assertEquals(26, result.totalElements());
        assertEquals("NEEDS_PREPARATION", result.items().getFirst().workStage());
        assertEquals("CONTRACT_PENDING", result.items().getFirst().applicationStatus());
        assertNull(result.items().getFirst().currentContract());
        verify(applications).findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.CONTRACT_PENDING,
                1,
                25
        );
    }

    @Test
    void queueRejectsInvalidPagingBeforeRepositoryAccess() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());

        assertThrows(IllegalArgumentException.class, () -> service.queryWork(null, -1, 25));
        assertThrows(IllegalArgumentException.class, () -> service.queryWork(null, 0, 101));

        verify(applications, never()).findStaffPage(null, LoanApplicationStatus.CONTRACT_PENDING, -1, 25);
    }

    @Test
    void queueFailsClosedIfPersistenceReturnsAnUnrelatedLifecycleState() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findStaffPage(null, LoanApplicationStatus.CONTRACT_PENDING, 0, 25))
                .thenReturn(new LoanApplicationRepository.StaffPage(
                        0,
                        25,
                        1,
                        1,
                        List.of(application(LoanApplicationStatus.DISBURSEMENT_PENDING))
                ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.queryWork(null, 0, 25)
        );

        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
        verify(contracts, never()).findCurrentByApplicationId(APPLICATION_ID);
    }

    @Test
    void caseRejectsAnUnrelatedLifecycleStateBeforeComposingContractEvidence() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(
                application(LoanApplicationStatus.UNDER_REVIEW)
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.queryCase(APPLICATION_ID)
        );

        assertEquals("INVALID_APPLICATION_STATE", exception.getErrorCode());
        verify(contracts, never()).findCurrentByApplicationId(APPLICATION_ID);
    }

    @Test
    void preparedContractRequiresCustomerAcknowledgment() {
        LoanContract contract = LoanContractTestData.prepared();
        stubCase(LoanApplicationStatus.CONTRACT_PENDING, contract, snapshot(
                contract.id(),
                contract.contractVersion(),
                false,
                ContractReadinessBlockerCode.ACKNOWLEDGMENT_MISSING
        ));

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("CUSTOMER_ACKNOWLEDGMENT_REQUIRED", result.workStage());
        assertEquals("ACKNOWLEDGE", result.currentContract().availableCustomerAction());
        assertEquals("****7890", result.currentContract().disbursementBankAccount().maskedAccountNumber());
        assertFalse(result.readiness().ready());
    }

    @Test
    void acknowledgedContractWithBlockersIsReadinessBlocked() {
        LoanContract contract = LoanContractTestData.acknowledged();
        stubCase(LoanApplicationStatus.CONTRACT_PENDING, contract, snapshot(
                contract.id(),
                contract.contractVersion(),
                false,
                ContractReadinessBlockerCode.DOCUMENTS_NOT_PROCESSING_READY
        ));

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("READINESS_BLOCKED", result.workStage());
        assertEquals(List.of("DOCUMENTS_NOT_PROCESSING_READY"), result.readiness().blockerCodes());
    }

    @Test
    void acknowledgedContractWithoutBlockersIsReadyToConfirm() {
        LoanContract contract = LoanContractTestData.acknowledged();
        stubCase(LoanApplicationStatus.CONTRACT_PENDING, contract, snapshot(
                contract.id(), contract.contractVersion(), true
        ));

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("READY_TO_CONFIRM", result.workStage());
        assertTrue(result.readiness().ready());
        assertEquals("POINT_IN_TIME_ADVISORY", result.readiness().calculationSemantics());
    }

    @Test
    void contradictoryAcknowledgedReadinessFailsClosed() {
        LoanContract contract = LoanContractTestData.acknowledged();
        stubCase(LoanApplicationStatus.CONTRACT_PENDING, contract, snapshot(
                contract.id(),
                contract.contractVersion(),
                true,
                ContractReadinessBlockerCode.DOCUMENTS_NOT_PROCESSING_READY
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.queryCase(APPLICATION_ID)
        );

        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
    }

    @Test
    void confirmedCaseIsReadableButDoesNotRemainInContractQueue() {
        LoanContract contract = LoanContractTestData.ready();
        stubCase(LoanApplicationStatus.DISBURSEMENT_PENDING, contract, snapshot(
                contract.id(),
                contract.contractVersion(),
                false,
                ContractReadinessBlockerCode.READINESS_ALREADY_CONFIRMED
        ));

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("DISBURSEMENT_PENDING", result.applicationStatus());
        assertEquals("READY_FOR_DISBURSEMENT", result.currentContract().status());
        assertEquals("READINESS_CONFIRMED", result.workStage());
    }

    @Test
    void contradictoryCurrentContractEvidenceFailsClosed() {
        LoanContract contract = LoanContractTestData.ready();
        stubCase(LoanApplicationStatus.CONTRACT_PENDING, contract, snapshot(
                contract.id(),
                contract.contractVersion(),
                false,
                ContractReadinessBlockerCode.CONFLICTING_COMPLETED_TRANSITION
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.queryCase(APPLICATION_ID)
        );

        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
    }

    @Test
    void readinessContractIdentityMismatchFailsClosed() {
        LoanContract contract = LoanContractTestData.acknowledged();
        stubCase(LoanApplicationStatus.CONTRACT_PENDING, contract, new QueryContractReadinessUseCase.Snapshot(
                APPLICATION_ID,
                UUID.randomUUID(),
                contract.contractVersion(),
                true,
                List.of()
        ));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.queryCase(APPLICATION_ID)
        );

        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
    }

    @Test
    void exactStaffPermissionAndAccountingRoleAreRequired() {
        for (AuthenticatedUser denied : List.of(
                staff("STAFF", null, Set.of("LOAN_OFFICER"), Set.of("loan:contract:read")),
                staff("STAFF", null, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:contract:read:all")),
                staff("CUSTOMER", UUID.randomUUID(), Set.of("ACCOUNTING_OFFICER"), Set.of("loan:contract:read"))
        )) {
            when(currentUserProvider.currentUser()).thenReturn(denied);
            assertThrows(AuthorizationException.class, () -> service.queryWork(null, 0, 25));
        }

        when(currentUserProvider.currentUser()).thenReturn(
                staff("STAFF", null, Set.of("LOAN_OFFICER"), Set.of("loan:contract:read"))
        );
        AuthorizationException roleRequired = assertThrows(
                AuthorizationException.class,
                () -> service.queryWork(null, 0, 25)
        );
        assertEquals("ACCOUNTING_ROLE_REQUIRED", roleRequired.getErrorCode());
    }

    private void stubCase(
            LoanApplicationStatus status,
            LoanContract contract,
            QueryContractReadinessUseCase.Snapshot readinessSnapshot
    ) {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application(status)));
        when(contracts.findCurrentByApplicationId(APPLICATION_ID)).thenReturn(Optional.ofNullable(contract));
        when(readiness.query(APPLICATION_ID, contract == null ? null : contract.contractVersion()))
                .thenReturn(readinessSnapshot);
    }

    private static QueryContractReadinessUseCase.Snapshot snapshot(
            UUID contractId,
            Integer contractVersion,
            boolean ready,
            ContractReadinessBlockerCode... blockers
    ) {
        return new QueryContractReadinessUseCase.Snapshot(
                APPLICATION_ID,
                contractId,
                contractVersion,
                ready,
                List.of(blockers)
        );
    }

    private static LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(
                APPLICATION_ID,
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                UUID.fromString("88888888-8888-4888-8888-888888888888"),
                "UCL-20260907-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                new BigDecimal("12000000.00"),
                6,
                LocalDateTime.of(2026, 9, 7, 8, 0)
        );
    }

    private static AuthenticatedUser accounting() {
        return staff(
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:contract:read")
        );
    }

    private static AuthenticatedUser staff(
            String userType,
            UUID customerId,
            Set<String> roles,
            Set<String> permissions
    ) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "staff@meridian.test",
                userType,
                customerId,
                roles,
                permissions
        );
    }
}
