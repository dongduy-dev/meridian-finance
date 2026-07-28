package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.ContractBankAccountPort;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementSaveOutcome;
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
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmManualDisbursementServiceTest {

    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID OTHER_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000305");
    private static final String REFERENCE = "BANK-TRANSFER-001";
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 7, 27);
    private static final LocalDate FIRST_REPAYMENT_DATE = LocalDate.of(2026, 8, 27);
    private static final LocalDateTime OPERATION_TIME =
            LocalDateTime.of(2026, 7, 27, 10, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-27T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock LoanApplicationRepository applications;
    @Mock LoanContractRepository contracts;
    @Mock LoanAccountRepository loanAccounts;
    @Mock ManualDisbursementRepository manualDisbursements;
    @Mock RepaymentScheduleRepository repaymentSchedules;
    @Mock LoanProductActivationPolicyResolver activationPolicies;
    @Mock LoanProductActivationPolicy activationPolicy;
    @Mock LoanApplicationStatusTransitionRecorder transitionRecorder;
    @Mock BusinessAuditPublisher auditPublisher;
    @Mock CurrentUserProvider currentUserProvider;

    private ConfirmManualDisbursementService service;
    private LoanContract contract;
    private LoanApplication application;

    @BeforeEach
    void setUp() {
        contract = LoanContractTestData.ready();
        application = application(LoanApplicationStatus.DISBURSEMENT_PENDING,
                ProductCode.SALARY_ADVANCE);
        service = new ConfirmManualDisbursementService(
                applications,
                contracts,
                loanAccounts,
                manualDisbursements,
                repaymentSchedules,
                activationPolicies,
                transitionRecorder,
                auditPublisher,
                currentUserProvider,
                CLOCK
        );
        arrangeSuccessfulFoundation();
    }

    @Test
    void atomicallyOrchestratesExactContractActivationInCanonicalOrder() {
        ConfirmManualDisbursementUseCase.Result result = service.confirm(command(
                "  bank-transfer-001  ",
                VALUE_DATE,
                FIRST_REPAYMENT_DATE
        ));

        assertEquals(application.id(), result.loanApplicationId());
        assertEquals(LoanApplicationStatus.DISBURSED, result.applicationStatus());
        assertFalse(result.idempotentReplay());
        assertEquals(0, contract.financialTerms().approvedPrincipal().compareTo(
                result.disbursedAmount()));
        assertEquals(1, result.scheduleItems().size());
        assertEquals(contract.repaymentItems().getFirst().id(),
                result.scheduleItems().getFirst().sourceLoanContractRepaymentItemId());
        assertEquals(0, contract.repaymentItems().getFirst().principalDue().compareTo(
                result.scheduleItems().getFirst().principalDue()));
        assertEquals(0, contract.repaymentItems().getFirst().interestDue().compareTo(
                result.scheduleItems().getFirst().interestDue()));
        assertEquals(0, contract.repaymentItems().getFirst().feeDue().compareTo(
                result.scheduleItems().getFirst().feeDue()));
        assertEquals(0, contract.repaymentItems().getFirst().totalDue().compareTo(
                result.scheduleItems().getFirst().totalDue()));

        ArgumentCaptor<LoanAccount> accountCaptor = ArgumentCaptor.forClass(LoanAccount.class);
        verify(loanAccounts).save(accountCaptor.capture());
        LoanAccount account = accountCaptor.getValue();
        assertEquals(contract.id(), account.loanContractId());
        assertEquals(contract.disbursementBankAccount().customerId(), account.customerId());
        assertEquals(0, contract.financialTerms().approvedPrincipal().compareTo(
                account.approvedPrincipal()));
        assertEquals(contract.financialTerms().approvedTermMonths(),
                account.approvedTermMonths());
        assertEquals(0, contract.financialTerms().totalInterest().compareTo(
                account.totalInterest()));
        assertEquals(0, contract.financialTerms().feeAmount().compareTo(account.feeAmount()));
        assertEquals(0, contract.financialTerms().totalRepaymentAmount().compareTo(
                account.totalRepaymentAmount()));

        ArgumentCaptor<ManualDisbursement> disbursementCaptor =
                ArgumentCaptor.forClass(ManualDisbursement.class);
        verify(manualDisbursements).save(disbursementCaptor.capture());
        ManualDisbursement disbursement = disbursementCaptor.getValue();
        assertEquals(REFERENCE, disbursement.externalTransferReference());
        assertEquals(contract.id(), disbursement.loanContractId());
        assertEquals(account.id(), disbursement.loanAccountId());
        assertEquals(ACTOR_ID, disbursement.confirmedByUserId());
        assertEquals(OPERATION_TIME, disbursement.confirmedAt());

        InOrder order = inOrder(
                manualDisbursements,
                applications,
                contracts,
                loanAccounts,
                repaymentSchedules,
                activationPolicies,
                activationPolicy,
                transitionRecorder,
                auditPublisher
        );
        order.verify(manualDisbursements).acquireConfirmationRequestLock(any());
        order.verify(manualDisbursements).findByRequestId(any());
        order.verify(applications).acquireWorkflowLock(application.id());
        order.verify(manualDisbursements).findByRequestId(any());
        order.verify(applications).findByIdForUpdate(application.id());
        order.verify(contracts).findCurrentByApplicationIdForUpdate(application.id());
        order.verify(loanAccounts).findByLoanApplicationIdForUpdate(application.id());
        order.verify(manualDisbursements).findByLoanApplicationIdForUpdate(application.id());
        order.verify(repaymentSchedules).findByLoanApplicationId(application.id());
        order.verify(loanAccounts).save(any());
        order.verify(manualDisbursements).save(any());
        order.verify(repaymentSchedules).save(any());
        order.verify(activationPolicies).resolve(ProductCode.SALARY_ADVANCE);
        order.verify(activationPolicy).activate(any());
        order.verify(applications).save(any());
        order.verify(transitionRecorder).record(any(), any(), any());
        order.verify(auditPublisher).publish(any());
    }

    @Test
    void publishesOnePiiSafeOperationAuditWithHistory() {
        service.confirm(command());

        ArgumentCaptor<BusinessAuditEvent> auditCaptor =
                ArgumentCaptor.forClass(BusinessAuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        BusinessAuditEvent event = auditCaptor.getValue();
        assertEquals(1, event.entries().size());
        assertEquals(BusinessAuditAction.MANUAL_DISBURSEMENT_CONFIRMED,
                event.entries().getFirst().action());
        assertEquals(BusinessAuditEntityType.LOAN_APPLICATION,
                event.entries().getFirst().entityType());
        assertEquals(application.id(), event.entries().getFirst().entityId());
        assertEquals(application.id().toString(), event.entries().getFirst().payload()
                .values().get(BusinessAuditPayloadKey.LOAN_APPLICATION_ID.jsonName()));
        assertEquals(ProductCode.SALARY_ADVANCE.name(), event.entries().getFirst().payload()
                .values().get(BusinessAuditPayloadKey.PRODUCT_CODE.jsonName()));
        assertEquals(LoanApplicationStatus.DISBURSEMENT_PENDING.name(),
                event.entries().getFirst().payload().values().get(
                        BusinessAuditPayloadKey.PREVIOUS_APPLICATION_STATUS.jsonName()));
        assertEquals(LoanApplicationStatus.DISBURSED.name(),
                event.entries().getFirst().payload().values().get(
                        BusinessAuditPayloadKey.FINAL_APPLICATION_STATUS.jsonName()));
        assertFalse(event.toString().contains(REFERENCE));
        verify(transitionRecorder).record(
                event.operationContext(),
                List.of(application.confirmManualDisbursement().facts().getFirst()),
                null
        );
    }

    @Test
    void commandCannotSupplyFinancialDestinationCustomerOrGeneratedIdentity() {
        Constructor<?> constructor = ConfirmManualDisbursementUseCase.Command.class
                .getDeclaredConstructors()[0];
        List<Class<?>> parameterTypes = Arrays.asList(constructor.getParameterTypes());

        assertEquals(List.of(
                UUID.class,
                UUID.class,
                int.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        ), parameterTypes);
        assertFalse(Arrays.asList(ConfirmManualDisbursementService.class
                .getDeclaredConstructors()[0].getParameterTypes())
                .contains(ApprovedOfferRepository.class));
        assertFalse(Arrays.asList(ConfirmManualDisbursementService.class
                .getDeclaredConstructors()[0].getParameterTypes())
                .contains(ContractBankAccountPort.class));
    }

    @Test
    void identicalReplayReturnsOriginalEvidenceWithoutAnyMutation() {
        ReplayFixture replay = arrangeReplay();

        ConfirmManualDisbursementUseCase.Result result = service.confirm(command(
                " bank-transfer-001 ", VALUE_DATE, FIRST_REPAYMENT_DATE));

        assertTrue(result.idempotentReplay());
        assertEquals(replay.account().id(), result.loanAccountId());
        assertEquals(replay.disbursement().id(), result.manualDisbursementId());
        assertEquals(replay.schedule().id(), result.repaymentScheduleId());
        verify(applications, never()).acquireWorkflowLock(any());
        verify(loanAccounts, never()).save(any());
        verify(manualDisbursements, never()).save(any());
        verify(repaymentSchedules, never()).save(any());
        verify(activationPolicies, never()).resolve(any());
        verify(activationPolicy, never()).activate(any());
        verify(applications, never()).save(any());
        verify(transitionRecorder, never()).record(any(), any(), any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void requestReuseComparesEveryLogicalFieldAndNoGeneratedField() {
        ReplayFixture replay = arrangeReplay();
        List<ConfirmManualDisbursementUseCase.Command> conflicts = List.of(
                new ConfirmManualDisbursementUseCase.Command(
                        replay.disbursement().requestId(), UUID.randomUUID(), 1,
                        REFERENCE, VALUE_DATE, FIRST_REPAYMENT_DATE),
                new ConfirmManualDisbursementUseCase.Command(
                        replay.disbursement().requestId(), application.id(), 2,
                        REFERENCE, VALUE_DATE, FIRST_REPAYMENT_DATE),
                new ConfirmManualDisbursementUseCase.Command(
                        replay.disbursement().requestId(), application.id(), 1,
                        "OTHER-REFERENCE", VALUE_DATE, FIRST_REPAYMENT_DATE),
                new ConfirmManualDisbursementUseCase.Command(
                        replay.disbursement().requestId(), application.id(), 1,
                        REFERENCE, VALUE_DATE.minusDays(1), FIRST_REPAYMENT_DATE),
                new ConfirmManualDisbursementUseCase.Command(
                        replay.disbursement().requestId(), application.id(), 1,
                        REFERENCE, VALUE_DATE, FIRST_REPAYMENT_DATE.minusDays(1))
        );

        for (ConfirmManualDisbursementUseCase.Command conflicting : conflicts) {
            assertCode("IDEMPOTENCY_KEY_REUSED", () -> service.confirm(conflicting));
        }

        lenient().when(currentUserProvider.currentUser()).thenReturn(accounting(OTHER_ACTOR_ID));
        assertCode("IDEMPOTENCY_KEY_REUSED", () -> service.confirm(command()));
        verify(loanAccounts, never()).save(any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void differentRequestAfterCompletedActivationIsDeterministic() {
        when(applications.findByIdForUpdate(application.id())).thenReturn(Optional.of(
                application(LoanApplicationStatus.DISBURSED, ProductCode.SALARY_ADVANCE)
        ));

        assertCode("DISBURSEMENT_ALREADY_COMPLETED", () -> service.confirm(command()));
        verify(loanAccounts, never()).save(any());
    }

    @Test
    void rejectsMissingApplicationAndCurrentContract() {
        when(applications.findByIdForUpdate(application.id())).thenReturn(Optional.empty());
        EntityNotFoundException missingApplication = assertThrows(
                EntityNotFoundException.class,
                () -> service.confirm(command())
        );
        assertEquals("LOAN_APPLICATION_NOT_FOUND", missingApplication.getErrorCode());

        when(applications.findByIdForUpdate(application.id())).thenReturn(Optional.of(application));
        when(contracts.findCurrentByApplicationIdForUpdate(application.id()))
                .thenReturn(Optional.empty());
        assertCode("CURRENT_CONTRACT_MISSING", () -> service.confirm(command()));
    }

    @Test
    void rejectsInvalidApplicationAndContractLifecycle() {
        when(applications.findByIdForUpdate(application.id())).thenReturn(Optional.of(
                application(LoanApplicationStatus.CONTRACT_PENDING, ProductCode.SALARY_ADVANCE)
        ));
        assertCode("MANUAL_DISBURSEMENT_CONFIRMATION_NOT_ALLOWED",
                () -> service.confirm(command()));

        when(applications.findByIdForUpdate(application.id())).thenReturn(Optional.of(application));
        when(contracts.findCurrentByApplicationIdForUpdate(application.id()))
                .thenReturn(Optional.of(LoanContractTestData.acknowledged()));
        assertCode("CONTRACT_NOT_READY_FOR_DISBURSEMENT", () -> service.confirm(command()));

        LoanContract superseded = LoanContractTestData.acknowledged().supersede(
                ACTOR_ID, LocalDateTime.of(2026, 7, 24, 10, 0));
        when(contracts.findCurrentByApplicationIdForUpdate(application.id()))
                .thenReturn(Optional.of(superseded));
        assertCode("CONTRACT_NOT_READY_FOR_DISBURSEMENT", () -> service.confirm(command()));
    }

    @Test
    void rejectsStaleContractVersionBeforeWrites() {
        assertCode("CONTRACT_VERSION_STALE", () -> service.confirm(
                new ConfirmManualDisbursementUseCase.Command(
                        UUID.randomUUID(), application.id(), 2, REFERENCE,
                        VALUE_DATE, FIRST_REPAYMENT_DATE
                )
        ));
        verify(loanAccounts, never()).save(any());
    }

    @Test
    void validatesValueDateAgainstUtcTodayAndReadinessDate() {
        BusinessRuleViolationException beforeReadiness = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.confirm(command(
                        REFERENCE,
                        contract.confirmedAt().toLocalDate().minusDays(1),
                        FIRST_REPAYMENT_DATE
                ))
        );
        assertEquals("DISBURSEMENT_VALUE_DATE_INVALID", beforeReadiness.getErrorCode());

        BusinessRuleViolationException future = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.confirm(command(
                        REFERENCE,
                        VALUE_DATE.plusDays(1),
                        FIRST_REPAYMENT_DATE
                ))
        );
        assertEquals("DISBURSEMENT_VALUE_DATE_INVALID", future.getErrorCode());
        verify(loanAccounts, never()).save(any());
    }

    @Test
    void validatesFirstRepaymentDateBounds() {
        BusinessRuleViolationException equal = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.confirm(command(REFERENCE, VALUE_DATE, VALUE_DATE))
        );
        assertEquals("FIRST_REPAYMENT_DATE_INVALID", equal.getErrorCode());

        BusinessRuleViolationException tooLate = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.confirm(command(
                        REFERENCE,
                        VALUE_DATE,
                        VALUE_DATE.plusMonths(1).plusDays(1)
                ))
        );
        assertEquals("FIRST_REPAYMENT_DATE_INVALID", tooLate.getErrorCode());
    }

    @Test
    void acceptsReadinessDateAndOneCalendarMonthBoundary() {
        LocalDate readinessDate = contract.confirmedAt().toLocalDate();
        ConfirmManualDisbursementUseCase.Result result = service.confirm(command(
                REFERENCE,
                readinessDate,
                readinessDate.plusMonths(1)
        ));

        assertEquals(readinessDate, result.disbursementValueDate());
        assertEquals(readinessDate.plusMonths(1), result.firstRepaymentDate());
    }

    @Test
    void unsupportedProductAndPolicyFailureRollbackThroughException() {
        application = application(LoanApplicationStatus.DISBURSEMENT_PENDING,
                ProductCode.UNSECURED_CONSUMER_LOAN);
        when(applications.findByIdForUpdate(application.id())).thenReturn(Optional.of(application));
        when(activationPolicies.resolve(ProductCode.UNSECURED_CONSUMER_LOAN)).thenThrow(
                new BusinessStateConflictException(
                        "PRODUCT_ACTIVATION_NOT_SUPPORTED",
                        "Loan product activation is not supported."
                )
        );
        assertCode("PRODUCT_ACTIVATION_NOT_SUPPORTED", () -> service.confirm(command()));

        application = application(LoanApplicationStatus.DISBURSEMENT_PENDING,
                ProductCode.SALARY_ADVANCE);
        when(applications.findByIdForUpdate(application.id())).thenReturn(Optional.of(application));
        when(activationPolicies.resolve(ProductCode.SALARY_ADVANCE))
                .thenReturn(activationPolicy);
        doThrow(new BusinessStateConflictException(
                "SALARY_ADVANCE_RESERVATION_INVALID",
                "Reservation evidence is invalid."
        )).when(activationPolicy).activate(any());
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID", () -> service.confirm(command()));
    }

    @Test
    void mapsEveryManualDisbursementSaveConflictOutcome() {
        for (ManualDisbursementSaveOutcome.ConflictKind kind : List.of(
                ManualDisbursementSaveOutcome.ConflictKind.LOAN_APPLICATION,
                ManualDisbursementSaveOutcome.ConflictKind.LOAN_CONTRACT,
                ManualDisbursementSaveOutcome.ConflictKind.LOAN_ACCOUNT
        )) {
            doReturn(new ManualDisbursementSaveOutcome.Conflict(kind))
                    .when(manualDisbursements).save(any());
            assertCode("DISBURSEMENT_ALREADY_COMPLETED", () -> service.confirm(command()));
        }

        doReturn(new ManualDisbursementSaveOutcome.Conflict(
                ManualDisbursementSaveOutcome.ConflictKind.EXTERNAL_TRANSFER_REFERENCE))
                .when(manualDisbursements).save(any());
        assertCode("DUPLICATE_TRANSFER_REFERENCE", () -> service.confirm(command()));

        doReturn(new ManualDisbursementSaveOutcome.Conflict(
                ManualDisbursementSaveOutcome.ConflictKind.DISBURSEMENT_ID))
                .when(manualDisbursements).save(any());
        assertCode("SYSTEM_STATE_CONFLICT", () -> service.confirm(command()));

        doReturn(new ManualDisbursementSaveOutcome.UnresolvedConflict())
                .when(manualDisbursements).save(any());
        assertCode("SYSTEM_STATE_CONFLICT", () -> service.confirm(command()));
    }

    @Test
    void historyAndAuditFailuresPropagateAndPreventLaterEffects() {
        doThrow(new IllegalStateException("history unavailable"))
                .when(transitionRecorder).record(any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.confirm(command()));
        verify(auditPublisher, never()).publish(any());

        org.mockito.Mockito.reset(transitionRecorder);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditPublisher).publish(any());
        assertThrows(IllegalStateException.class, () -> service.confirm(command()));
    }

    @Test
    void commandResultAndScheduleItemStringsRedactSensitiveEvidence() {
        ConfirmManualDisbursementUseCase.Command command = command();
        ConfirmManualDisbursementUseCase.Result result = service.confirm(command);

        assertFalse(command.toString().contains(REFERENCE));
        assertTrue(command.toString().contains("externalTransferReference=redacted"));
        assertFalse(result.toString().contains(result.disbursedAmount().toPlainString()));
        assertTrue(result.toString().contains("transferAndFinancialEvidence=redacted"));
        assertFalse(result.scheduleItems().getFirst().toString().contains(
                result.scheduleItems().getFirst().principalDue().toPlainString()));
        assertThrows(UnsupportedOperationException.class, () ->
                result.scheduleItems().add(result.scheduleItems().getFirst()));
    }

    @Test
    void ownsOneRequiredSpringTransaction() throws Exception {
        Transactional transactional = ConfirmManualDisbursementService.class
                .getMethod("confirm", ConfirmManualDisbursementUseCase.Command.class)
                .getAnnotation(Transactional.class);

        assertInstanceOf(Transactional.class, transactional);
        assertEquals(Propagation.REQUIRED, transactional.propagation());
    }

    private void arrangeSuccessfulFoundation() {
        lenient().when(currentUserProvider.currentUser()).thenReturn(accounting(ACTOR_ID));
        lenient().when(manualDisbursements.findByRequestId(any()))
                .thenReturn(Optional.empty());
        lenient().when(applications.findByIdForUpdate(application.id()))
                .thenReturn(Optional.of(application));
        lenient().when(contracts.findCurrentByApplicationIdForUpdate(application.id()))
                .thenReturn(Optional.of(contract));
        lenient().when(loanAccounts.findByLoanApplicationIdForUpdate(application.id()))
                .thenReturn(Optional.empty());
        lenient().when(manualDisbursements.findByLoanApplicationIdForUpdate(application.id()))
                .thenReturn(Optional.empty());
        lenient().when(repaymentSchedules.findByLoanApplicationId(application.id()))
                .thenReturn(Optional.empty());
        lenient().when(loanAccounts.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0));
        lenient().when(manualDisbursements.save(any())).thenAnswer(invocation ->
                new ManualDisbursementSaveOutcome.Inserted(invocation.getArgument(0)));
        lenient().when(repaymentSchedules.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0));
        lenient().when(activationPolicies.resolve(ProductCode.SALARY_ADVANCE))
                .thenReturn(activationPolicy);
        lenient().when(activationPolicy.activate(any())).thenAnswer(invocation -> {
            LoanProductActivationPolicy.ProductActivationCommand activation =
                    invocation.getArgument(0);
            return new LoanProductActivationPolicy.ProductActivationResult(
                    ProductCode.SALARY_ADVANCE,
                    UUID.randomUUID(),
                    activation.movementId(),
                    contract.financialTerms().approvedPrincipal(),
                    contract.financialTerms().approvedPrincipal(),
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2)
            );
        });
        lenient().when(applications.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0));
    }

    private ReplayFixture arrangeReplay() {
        LoanApplication disbursed = application(
                LoanApplicationStatus.DISBURSED,
                ProductCode.SALARY_ADVANCE
        );
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), contract, OPERATION_TIME);
        ManualDisbursement disbursement = ManualDisbursement.confirmed(
                UUID.randomUUID(),
                contract,
                account,
                command().requestId(),
                1,
                REFERENCE,
                VALUE_DATE,
                FIRST_REPAYMENT_DATE,
                ACTOR_ID,
                OPERATION_TIME
        );
        RepaymentSchedule schedule = new FinalRepaymentScheduleGenerator().generate(
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                contract,
                account,
                VALUE_DATE,
                FIRST_REPAYMENT_DATE,
                OPERATION_TIME
        );
        when(manualDisbursements.findByRequestId(disbursement.requestId()))
                .thenReturn(Optional.of(disbursement));
        lenient().when(applications.findById(application.id()))
                .thenReturn(Optional.of(disbursed));
        lenient().when(contracts.findCurrentByApplicationId(application.id()))
                .thenReturn(Optional.of(contract));
        lenient().when(loanAccounts.findById(account.id())).thenReturn(Optional.of(account));
        lenient().when(repaymentSchedules.findByLoanAccountId(account.id()))
                .thenReturn(Optional.of(schedule));
        return new ReplayFixture(account, disbursement, schedule);
    }

    private ConfirmManualDisbursementUseCase.Command command() {
        return command(REFERENCE, VALUE_DATE, FIRST_REPAYMENT_DATE);
    }

    private ConfirmManualDisbursementUseCase.Command command(
            String reference,
            LocalDate valueDate,
            LocalDate firstRepaymentDate
    ) {
        return new ConfirmManualDisbursementUseCase.Command(
                UUID.fromString("12121212-1212-1212-1212-121212121212"),
                application.id(),
                1,
                reference,
                valueDate,
                firstRepaymentDate
        );
    }

    private LoanApplication application(
            LoanApplicationStatus status,
            ProductCode productCode
    ) {
        return new LoanApplication(
                contract.loanApplicationId(),
                contract.disbursementBankAccount().customerId(),
                UUID.fromString("34343434-3434-3434-3434-343434343434"),
                "SA-20260727-0001",
                productCode,
                productCode == ProductCode.SALARY_ADVANCE
                        ? ProductType.SALARY_BASED
                        : ProductType.UNSECURED,
                status,
                contract.financialTerms().approvedPrincipal(),
                contract.financialTerms().approvedTermMonths(),
                OPERATION_TIME.minusMonths(1)
        );
    }

    private static AuthenticatedUser accounting(UUID userId) {
        return new AuthenticatedUser(
                userId,
                "accounting@meridian.test",
                "STAFF",
                null,
                Set.of("ACCOUNTING_OFFICER"),
                Set.of("loan:disburse")
        );
    }

    private static void assertCode(String expected, Runnable operation) {
        BusinessStateConflictException failure = assertThrows(
                BusinessStateConflictException.class,
                operation::run
        );
        assertEquals(expected, failure.getErrorCode());
        assertFalse(failure.getMessage().contains(REFERENCE));
    }

    private record ReplayFixture(
            LoanAccount account,
            ManualDisbursement disbursement,
            RepaymentSchedule schedule
    ) {
    }
}
