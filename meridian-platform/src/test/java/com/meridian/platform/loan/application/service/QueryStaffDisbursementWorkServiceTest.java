package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.service.FinalRepaymentScheduleGenerator;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffDisbursementWorkServiceTest {

    private static final UUID APPLICATION_ID = LoanContractTestData.APPLICATION_ID;
    private static final UUID CUSTOMER_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final LocalDateTime ACTIVATED_AT = LocalDateTime.of(2026, 7, 23, 10, 0);

    @Mock LoanApplicationRepository applications;
    @Mock LoanContractRepository contracts;
    @Mock LoanAccountRepository loanAccounts;
    @Mock ManualDisbursementRepository manualDisbursements;
    @Mock RepaymentScheduleRepository repaymentSchedules;
    @Mock CurrentUserProvider currentUserProvider;

    private QueryStaffDisbursementWorkService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffDisbursementWorkService(
                applications,
                contracts,
                loanAccounts,
                manualDisbursements,
                repaymentSchedules,
                new LoanContractMapper(),
                currentUserProvider
        );
    }

    @Test
    void queueUsesServerOwnedPendingMembershipProductFilterAndPaging() {
        LoanApplication application = application(LoanApplicationStatus.DISBURSEMENT_PENDING);
        LoanContract contract = LoanContractTestData.ready();
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                1,
                25
        )).thenReturn(new LoanApplicationRepository.StaffPage(
                1, 25, 26, 2, List.of(application)
        ));
        when(contracts.findCurrentByApplicationId(APPLICATION_ID)).thenReturn(Optional.of(contract));

        var result = service.queryWork(ProductCode.UNSECURED_CONSUMER_LOAN, 1, 25);

        assertEquals(26, result.totalElements());
        assertEquals("READY_TO_DISBURSE", result.items().getFirst().workStage());
        assertEquals("DISBURSEMENT_PENDING", result.items().getFirst().applicationStatus());
        assertEquals("****7890", result.items().getFirst().currentContract()
                .disbursementDestination().maskedAccountNumber());
        verify(applications).findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                1,
                25
        );
    }

    @Test
    void queueRejectsInvalidPagingBeforeRepositoryAccess() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());

        assertThrows(IllegalArgumentException.class, () -> service.queryWork(null, -1, 25));
        assertThrows(IllegalArgumentException.class, () -> service.queryWork(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.queryWork(null, 0, 101));

        verify(applications, never()).findStaffPage(null, LoanApplicationStatus.DISBURSEMENT_PENDING, -1, 25);
    }

    @Test
    void queueFailsClosedForUnexpectedStateOrExistingActivationEvidence() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findStaffPage(null, LoanApplicationStatus.DISBURSEMENT_PENDING, 0, 25))
                .thenReturn(page(application(LoanApplicationStatus.CONTRACT_PENDING)));
        assertSystemConflict(() -> service.queryWork(null, 0, 25));

        when(applications.findStaffPage(null, LoanApplicationStatus.DISBURSEMENT_PENDING, 0, 25))
                .thenReturn(page(application(LoanApplicationStatus.DISBURSEMENT_PENDING)));
        when(contracts.findCurrentByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(LoanContractTestData.ready()));
        when(loanAccounts.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(activation().account()));
        assertSystemConflict(() -> service.queryWork(null, 0, 25));
    }

    @Test
    void pendingCaseReturnsReadyContractWithoutActivation() {
        stubApplicationAndContract(LoanApplicationStatus.DISBURSEMENT_PENDING, LoanContractTestData.ready());

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("READY_TO_DISBURSE", result.workStage());
        assertEquals("READY_FOR_DISBURSEMENT", result.currentContract().status());
        assertEquals(1, result.currentContract().contractVersion());
        assertNull(result.activation());
    }

    @Test
    void pendingCaseFailsClosedForMissingNonReadyOrSupersededContract() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.DISBURSEMENT_PENDING)));
        when(contracts.findCurrentByApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
        assertSystemConflict(() -> service.queryCase(APPLICATION_ID));

        when(contracts.findCurrentByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(LoanContractTestData.acknowledged()));
        assertSystemConflict(() -> service.queryCase(APPLICATION_ID));

        when(contracts.findCurrentByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(LoanContractTestData.prepared().supersede(
                        UUID.randomUUID(), LocalDateTime.of(2026, 7, 23, 9, 0))));
        assertSystemConflict(() -> service.queryCase(APPLICATION_ID));
    }

    @Test
    void disbursedCaseReturnsDurableSafeActivationAndFinalSchedule() {
        ActivationEvidence evidence = stubCompletedCase();

        var result = service.queryCase(APPLICATION_ID);

        assertEquals("DISBURSED", result.applicationStatus());
        assertEquals("DISBURSED", result.workStage());
        assertEquals(evidence.account().id(), result.activation().loanAccountId());
        assertEquals("ACTIVE", result.activation().loanAccountStatus());
        assertEquals(LocalDate.of(2026, 8, 20), result.activation().firstRepaymentDate());
        assertEquals("FINAL", result.activation().scheduleType());
        assertEquals(1, result.activation().scheduleItems().size());
        assertTrue(result.toString().contains("activation="));
    }

    @Test
    void disbursedCaseFailsClosedWhenAnyDurableActivationRecordIsMissing() {
        LoanContract contract = LoanContractTestData.ready();
        ActivationEvidence evidence = activation();
        stubApplicationAndContract(LoanApplicationStatus.DISBURSED, contract);

        when(loanAccounts.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.account()));
        assertSystemConflict(() -> service.queryCase(APPLICATION_ID));

        when(manualDisbursements.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.disbursement()));
        assertSystemConflict(() -> service.queryCase(APPLICATION_ID));

        when(repaymentSchedules.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.schedule()));
        assertEquals("DISBURSED", service.queryCase(APPLICATION_ID).workStage());
    }

    @Test
    void disbursedCaseFailsClosedForActivationIdentityOrVersionMismatch() {
        LoanContract contract = LoanContractTestData.ready();
        ActivationEvidence evidence = activation();
        stubApplicationAndContract(LoanApplicationStatus.DISBURSED, contract);
        when(loanAccounts.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.account()));
        ManualDisbursement mismatched = new ManualDisbursement(
                evidence.disbursement().id(),
                evidence.disbursement().loanApplicationId(),
                evidence.disbursement().loanContractId(),
                evidence.disbursement().loanAccountId(),
                evidence.disbursement().requestId(),
                2,
                evidence.disbursement().externalTransferReference(),
                evidence.disbursement().disbursedAmount(),
                evidence.disbursement().valueDate(),
                evidence.disbursement().firstRepaymentDate(),
                evidence.disbursement().confirmedByUserId(),
                evidence.disbursement().confirmedAt()
        );
        when(manualDisbursements.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(mismatched));
        when(repaymentSchedules.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.schedule()));

        assertSystemConflict(() -> service.queryCase(APPLICATION_ID));
    }

    @Test
    void unrelatedCaseStateIsRejectedBeforeProjection() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(LoanApplicationStatus.CONTRACT_PENDING)));

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.queryCase(APPLICATION_ID)
        );

        assertEquals("INVALID_APPLICATION_STATE", exception.getErrorCode());
        verify(contracts, never()).findCurrentByApplicationId(APPLICATION_ID);
    }

    @Test
    void exactStaffPermissionAndAccountingRoleAreRequired() {
        for (AuthenticatedUser denied : List.of(
                staff("STAFF", null, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:read")),
                staff("CUSTOMER", CUSTOMER_ID, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse"))
        )) {
            when(currentUserProvider.currentUser()).thenReturn(denied);
            assertThrows(AuthorizationException.class, () -> service.queryWork(null, 0, 25));
        }

        when(currentUserProvider.currentUser()).thenReturn(
                staff("STAFF", null, Set.of("LOAN_OFFICER"), Set.of("loan:disburse"))
        );
        AuthorizationException roleRequired = assertThrows(
                AuthorizationException.class,
                () -> service.queryWork(null, 0, 25)
        );
        assertEquals("ACCOUNTING_ROLE_REQUIRED", roleRequired.getErrorCode());
    }

    private ActivationEvidence stubCompletedCase() {
        LoanContract contract = LoanContractTestData.ready();
        ActivationEvidence evidence = activation();
        stubApplicationAndContract(LoanApplicationStatus.DISBURSED, contract);
        when(loanAccounts.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.account()));
        when(manualDisbursements.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.disbursement()));
        when(repaymentSchedules.findByLoanApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(evidence.schedule()));
        return evidence;
    }

    private void stubApplicationAndContract(LoanApplicationStatus status, LoanContract contract) {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application(status)));
        when(contracts.findCurrentByApplicationId(APPLICATION_ID)).thenReturn(Optional.of(contract));
    }

    private static LoanApplicationRepository.StaffPage page(LoanApplication application) {
        return new LoanApplicationRepository.StaffPage(0, 25, 1, 1, List.of(application));
    }

    private static ActivationEvidence activation() {
        LoanContract contract = LoanContractTestData.ready();
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), contract, ACTIVATED_AT);
        ManualDisbursement disbursement = ManualDisbursement.confirmed(
                UUID.randomUUID(),
                contract,
                account,
                UUID.randomUUID(),
                contract.contractVersion(),
                "BANK-REFERENCE",
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 8, 20),
                UUID.randomUUID(),
                ACTIVATED_AT
        );
        RepaymentSchedule schedule = new FinalRepaymentScheduleGenerator().generate(
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                contract,
                account,
                disbursement.valueDate(),
                disbursement.firstRepaymentDate(),
                ACTIVATED_AT
        );
        return new ActivationEvidence(account, disbursement, schedule);
    }

    private static LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(
                APPLICATION_ID,
                CUSTOMER_ID,
                UUID.randomUUID(),
                "UCL-20260910-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                status,
                new BigDecimal("1000.00"),
                1,
                LocalDateTime.of(2026, 7, 23, 8, 0)
        );
    }

    private static AuthenticatedUser accounting() {
        return staff("STAFF", null, Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse"));
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

    private static void assertSystemConflict(org.junit.jupiter.api.function.Executable executable) {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                executable
        );
        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
    }

    private record ActivationEvidence(
            LoanAccount account,
            ManualDisbursement disbursement,
            RepaymentSchedule schedule
    ) {
    }
}
