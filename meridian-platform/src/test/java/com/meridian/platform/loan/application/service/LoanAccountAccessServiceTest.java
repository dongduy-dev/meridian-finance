package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.RevealDisbursementDestinationUseCase;
import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtector;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.RepaymentInstallmentProgressRepository;
import com.meridian.platform.loan.application.port.out.RepaymentScheduleRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProtectedDisbursementBankAccount;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.service.FinalRepaymentScheduleGenerator;
import com.meridian.platform.loan.testsupport.LoanContractTestData;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LoanAccountAccessServiceTest {

    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID CUSTOMER_ID = LoanContractTestData.ready()
            .disbursementBankAccount().customerId();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @Mock LoanApplicationRepository applications;
    @Mock LoanContractRepository contracts;
    @Mock LoanAccountRepository loanAccounts;
    @Mock RepaymentScheduleRepository repaymentSchedules;
    @Mock RepaymentInstallmentProgressRepository installmentProgress;
    @Mock DisbursementBankAccountProtector protector;
    @Mock BusinessAuditPublisher auditPublisher;
    @Mock CurrentUserProvider currentUserProvider;

    private RevealDisbursementDestinationService revealService;
    private QueryLoanAccountService queryService;
    private LoanApplication pendingApplication;
    private LoanApplication disbursedApplication;
    private LoanAccount loanAccount;
    private RepaymentSchedule schedule;

    @BeforeEach
    void setUp() {
        pendingApplication = application(LoanApplicationStatus.DISBURSEMENT_PENDING);
        disbursedApplication = application(LoanApplicationStatus.DISBURSED);
        loanAccount = LoanAccount.activate(
                UUID.fromString("12121212-1212-1212-1212-121212121212"),
                LoanContractTestData.ready(),
                LocalDateTime.of(2026, 7, 28, 10, 0)
        );
        schedule = new FinalRepaymentScheduleGenerator().generate(
                UUID.fromString("13131313-1313-1313-1313-131313131313"),
                List.of(UUID.fromString("14141414-1414-1414-1414-141414141414")),
                LoanContractTestData.ready(), loanAccount,
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 28),
                LocalDateTime.of(2026, 7, 28, 10, 0)
        );
        revealService = new RevealDisbursementDestinationService(
                applications, contracts, protector, auditPublisher,
                currentUserProvider, CLOCK
        );
        queryService = new QueryLoanAccountService(
                applications, contracts, loanAccounts, repaymentSchedules,
                installmentProgress, currentUserProvider
        );
    }

    @Test
    void revealsOnlyTheLockedContractSnapshotAndPublishesSafeAuditEvidence() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findByIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(pendingApplication));
        when(contracts.findCurrentByApplicationIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(LoanContractTestData.ready()));
        when(protector.revealToBytes(any(), any()))
                .thenReturn("01234567890".getBytes(StandardCharsets.UTF_8));

        RevealDisbursementDestinationUseCase.Result result = revealService.reveal(
                new RevealDisbursementDestinationUseCase.Command(
                        pendingApplication.id(), 1));

        assertEquals("01234567890", result.accountNumber());
        assertEquals("VCB", result.bankCode());
        assertFalse(result.toString().contains("01234567890"));
        InOrder order = inOrder(applications, contracts, protector, auditPublisher);
        order.verify(applications).acquireWorkflowLock(pendingApplication.id());
        order.verify(applications).findByIdForUpdate(pendingApplication.id());
        order.verify(contracts).findCurrentByApplicationIdForUpdate(pendingApplication.id());
        order.verify(protector).revealToBytes(any(), any());
        order.verify(auditPublisher).publish(any());

        ArgumentCaptor<BusinessAuditEvent> event =
                ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        var captured = event.getValue();
        assertEquals(ACTOR_ID, captured.operationContext().actorUserId());
        assertEquals(LocalDateTime.of(2026, 7, 28, 10, 0),
                captured.operationContext().occurredAt());
        assertEquals(1, captured.entries().size());
        var entry = captured.entries().getFirst();
        assertEquals(BusinessAuditAction
                        .LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED,
                entry.action());
        assertEquals(BusinessAuditEntityType.LOAN_CONTRACT, entry.entityType());
        assertEquals(Set.of(
                        BusinessAuditPayloadKey.LOAN_APPLICATION_ID.jsonName(),
                        BusinessAuditPayloadKey.LOAN_CONTRACT_ID.jsonName()),
                entry.payload().values().keySet());
        String auditText = captured.toString();
        assertFalse(auditText.contains("01234567890"));
        assertFalse(auditText.contains("MERIDIAN CUSTOMER"));
        assertFalse(auditText.contains("VCB"));
    }

    @Test
    void rejectsStaleOrCompletedRevealBeforeDecryptionAndAudit() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findByIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(pendingApplication));
        when(contracts.findCurrentByApplicationIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(LoanContractTestData.ready()));
        BusinessStateConflictException stale = assertThrows(
                BusinessStateConflictException.class,
                () -> revealService.reveal(new RevealDisbursementDestinationUseCase.Command(
                        pendingApplication.id(), 2)));
        assertEquals("CONTRACT_VERSION_STALE", stale.getErrorCode());

        when(applications.findByIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(disbursedApplication));
        BusinessStateConflictException completed = assertThrows(
                BusinessStateConflictException.class,
                () -> revealService.reveal(new RevealDisbursementDestinationUseCase.Command(
                        pendingApplication.id(), 1)));
        assertEquals("DISBURSEMENT_DESTINATION_REVEAL_NOT_ALLOWED",
                completed.getErrorCode());
        verify(protector, never()).revealToBytes(any(), any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void mapsProtectionFailureToGenericSafeErrorWithoutAudit() {
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findByIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(pendingApplication));
        when(contracts.findCurrentByApplicationIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(LoanContractTestData.ready()));
        when(protector.revealToBytes(any(), any()))
                .thenThrow(new IllegalStateException("key v1 failed for secret material"));

        BusinessStateConflictException failure = assertThrows(
                BusinessStateConflictException.class,
                () -> revealService.reveal(new RevealDisbursementDestinationUseCase.Command(
                        pendingApplication.id(), 1)));

        assertEquals("DISBURSEMENT_DESTINATION_UNAVAILABLE", failure.getErrorCode());
        assertFalse(failure.getMessage().contains("key"));
        assertFalse(failure.getMessage().contains("secret"));
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void auditFailurePreventsRevealAndDoesNotLeakPlaintext(
            CapturedOutput output
    ) {
        byte[] plaintext = "01234567890".getBytes(StandardCharsets.UTF_8);
        when(currentUserProvider.currentUser()).thenReturn(accounting());
        when(applications.findByIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(pendingApplication));
        when(contracts.findCurrentByApplicationIdForUpdate(pendingApplication.id()))
                .thenReturn(Optional.of(LoanContractTestData.ready()));
        when(protector.revealToBytes(any(), any())).thenReturn(plaintext);
        doThrow(new IllegalStateException("Audit persistence unavailable."))
                .when(auditPublisher).publish(any());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> revealService.reveal(new RevealDisbursementDestinationUseCase.Command(
                        pendingApplication.id(), 1))
        );

        assertEquals("Audit persistence unavailable.", failure.getMessage());
        assertFalse(failure.getMessage().contains("01234567890"));
        assertFalse(failure.getMessage().contains("MERIDIAN CUSTOMER"));
        assertFalse(output.getAll().contains("01234567890"));
        assertFalse(output.getAll().contains("MERIDIAN CUSTOMER"));
        assertArrayEquals(new byte[plaintext.length], plaintext);
        verify(auditPublisher).publish(any());
    }

    @Test
    void rejectsCustomerOrStaffWithoutDisbursementPermission() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        assertThrows(AuthorizationException.class,
                () -> revealService.reveal(new RevealDisbursementDestinationUseCase.Command(
                        pendingApplication.id(), 1)));
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        assertThrows(AuthorizationException.class,
                () -> revealService.reveal(new RevealDisbursementDestinationUseCase.Command(
                        pendingApplication.id(), 1)));
        verify(applications, never()).acquireWorkflowLock(any());
    }

    @Test
    void customerOwnerAndStaffCanQueryReconciledMaskedAccountEvidence() {
        arrangeCompletedQuery();
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));

        QueryLoanAccountUseCase.Result owner = queryService.query(disbursedApplication.id());

        assertEquals("********", owner.destination().maskedAccountNumber());
        assertEquals(1, owner.scheduleItems().size());
        assertFalse(owner.toString().contains("MERIDIAN CUSTOMER"));
        assertFalse(owner.toString().contains("7890"));

        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        QueryLoanAccountUseCase.Result staff = queryService.query(disbursedApplication.id());
        assertEquals(owner.loanAccountId(), staff.loanAccountId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "12", "123", "1234"})
    void queryUsesAFixedMaskWithoutReturningAnyStoredSuffix(String suffix) {
        LoanContract contract = contractWithSuffix(suffix);
        LoanAccount account = LoanAccount.activate(
                UUID.randomUUID(), contract, LocalDateTime.of(2026, 7, 28, 10, 0));
        RepaymentSchedule finalSchedule = new FinalRepaymentScheduleGenerator().generate(
                UUID.randomUUID(), List.of(UUID.randomUUID()), contract, account,
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 28),
                LocalDateTime.of(2026, 7, 28, 10, 0)
        );
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        when(applications.findById(disbursedApplication.id()))
                .thenReturn(Optional.of(disbursedApplication));
        when(loanAccounts.findByLoanApplicationId(disbursedApplication.id()))
                .thenReturn(Optional.of(account));
        when(contracts.findCurrentByApplicationId(disbursedApplication.id()))
                .thenReturn(Optional.of(contract));
        when(repaymentSchedules.findByLoanAccountId(account.id()))
                .thenReturn(Optional.of(finalSchedule));
        when(installmentProgress.findByRepaymentScheduleId(finalSchedule.id()))
                .thenReturn(initialProgress(finalSchedule, account));

        String masked = queryService.query(disbursedApplication.id())
                .destination().maskedAccountNumber();

        assertEquals("********", masked);
        assertFalse(masked.contains(suffix));
        verify(protector, never()).revealToBytes(any(), any());
    }

    @Test
    void customerOwnershipIsolationUsesTheGenericNotFoundResponse() {
        when(currentUserProvider.currentUser()).thenReturn(customer(UUID.randomUUID()));
        when(applications.findById(disbursedApplication.id()))
                .thenReturn(Optional.of(disbursedApplication));

        EntityNotFoundException failure = assertThrows(EntityNotFoundException.class,
                () -> queryService.query(disbursedApplication.id()));

        assertEquals("LOAN_ACCOUNT_NOT_FOUND", failure.getErrorCode());
        verify(loanAccounts, never()).findByLoanApplicationId(any());
    }

    @Test
    void missingLoanAccountIsNotFoundAndContradictoryEvidenceConflicts() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));
        when(applications.findById(disbursedApplication.id()))
                .thenReturn(Optional.of(disbursedApplication));
        when(loanAccounts.findByLoanApplicationId(disbursedApplication.id()))
                .thenReturn(Optional.empty());
        EntityNotFoundException missing = assertThrows(EntityNotFoundException.class,
                () -> queryService.query(disbursedApplication.id()));
        assertEquals("LOAN_ACCOUNT_NOT_FOUND", missing.getErrorCode());

        when(loanAccounts.findByLoanApplicationId(disbursedApplication.id()))
                .thenReturn(Optional.of(loanAccount));
        when(contracts.findCurrentByApplicationId(disbursedApplication.id()))
                .thenReturn(Optional.of(LoanContractTestData.ready()));
        when(repaymentSchedules.findByLoanAccountId(loanAccount.id()))
                .thenReturn(Optional.empty());
        BusinessStateConflictException conflict = assertThrows(
                BusinessStateConflictException.class,
                () -> queryService.query(disbursedApplication.id()));
        assertEquals("SYSTEM_STATE_CONFLICT", conflict.getErrorCode());
    }

    private void arrangeCompletedQuery() {
        when(applications.findById(disbursedApplication.id()))
                .thenReturn(Optional.of(disbursedApplication));
        when(loanAccounts.findByLoanApplicationId(disbursedApplication.id()))
                .thenReturn(Optional.of(loanAccount));
        when(contracts.findCurrentByApplicationId(disbursedApplication.id()))
                .thenReturn(Optional.of(LoanContractTestData.ready()));
        when(repaymentSchedules.findByLoanAccountId(loanAccount.id()))
                .thenReturn(Optional.of(schedule));
        when(installmentProgress.findByRepaymentScheduleId(schedule.id()))
                .thenReturn(initialProgress(schedule, loanAccount));
    }

    private static List<RepaymentInstallmentProgress> initialProgress(
            RepaymentSchedule finalSchedule,
            LoanAccount account
    ) {
        return finalSchedule.items().stream()
                .map(item -> RepaymentInstallmentProgress.initial(finalSchedule, item,
                        account.servicingEvaluationDate(), account.activatedAt()))
                .toList();
    }
    private static LoanContract contractWithSuffix(String suffix) {
        LoanContract source = LoanContractTestData.ready();
        var destination = source.disbursementBankAccount();
        var replacedDestination = new ProtectedDisbursementBankAccount(
                destination.customerId(), destination.sourceBankAccountId(),
                destination.bankCode(), destination.bankNameSnapshot(),
                destination.accountHolderName(), suffix,
                destination.primaryAtCapture(), destination.activeAtCapture(),
                destination.capturedAt(), destination.protectionScheme(),
                destination.keyId(), destination.nonce(), destination.ciphertext(),
                destination.aadVersion()
        );
        return new LoanContract(
                source.id(),
                source.loanApplicationId(),
                source.approvedOfferId(),
                source.contractReference(),
                source.contractVersion(),
                source.status(),
                source.financialTerms(),
                source.repaymentItems(),
                replacedDestination,
                source.preparationRequestId(),
                source.expectedPreviousVersion(),
                source.supersessionReason(),
                source.preparedByUserId(),
                source.preparedAt(),
                source.acknowledgmentRequestId(),
                source.acknowledgedByUserId(),
                source.acknowledgedAt(),
                source.confirmationRequestId(),
                source.confirmedByUserId(),
                source.confirmedAt(),
                source.supersedesContractId(),
                source.supersededByUserId(),
                source.supersededAt()
        );
    }
    private static LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(
                LoanContractTestData.APPLICATION_ID, CUSTOMER_ID, UUID.randomUUID(),
                "APP-I4", ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED,
                status, LoanContractTestData.ready().financialTerms().approvedPrincipal(),
                1, LocalDateTime.of(2026, 7, 1, 10, 0)
        );
    }

    private static AuthenticatedUser accounting() {
        return new AuthenticatedUser(
                ACTOR_ID, "accounting@meridian.test", "STAFF", null,
                Set.of("ACCOUNTING_OFFICER"), Set.of("loan:disburse", "loan:read")
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
