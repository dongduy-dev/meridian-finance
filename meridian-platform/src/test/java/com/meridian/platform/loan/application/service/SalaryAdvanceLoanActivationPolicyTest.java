package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.loan.testsupport.LoanContractTestData;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryAdvanceLoanActivationPolicyTest {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("10101010-1010-1010-1010-101010101010");
    private static final UUID LIMIT_ID =
            UUID.fromString("20202020-2020-2020-2020-202020202020");
    private static final UUID LINK_ID =
            UUID.fromString("30303030-3030-3030-3030-303030303030");
    private static final UUID MOVEMENT_ID =
            UUID.fromString("40404040-4040-4040-4040-404040404040");
    private static final UUID RESERVATION_ID =
            UUID.fromString("50505050-5050-5050-5050-505050505050");
    private static final LocalDateTime OCCURRED_AT =
            LocalDateTime.of(2026, 7, 27, 10, 0);
    private static final BigDecimal PRINCIPAL = money(1_000);
    private static final BigDecimal EXISTING_USED = money(500);
    private static final BigDecimal AVAILABLE = money(3_500);

    @Mock
    private SalaryAdvanceVerificationRepository verifications;

    @Mock
    private SalaryAdvanceLimitRepository limits;

    @Mock
    private SalaryAdvanceLimitMovementRepository movements;

    private SalaryAdvanceLoanActivationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SalaryAdvanceLoanActivationPolicy(verifications, limits, movements);
    }

    @ParameterizedTest
    @EnumSource(SalaryAdvanceLimitStatus.class)
    void convertsExactContractPrincipalWithoutEligibilityStatusGate(SalaryAdvanceLimitStatus status) {
        Fixture fixture = fixture(status);
        arrangeValid(fixture);

        LoanProductActivationPolicy.ProductActivationResult result =
                policy.activate(fixture.command());

        assertEquals(ProductCode.SALARY_ADVANCE, result.productCode());
        assertEquals(LIMIT_ID, result.productExposureId());
        assertEquals(MOVEMENT_ID, result.movementId());
        assertMoney(PRINCIPAL, result.convertedAmount());
        assertMoney(money(1_500), result.resultingUsedAmount());
        assertMoney(BigDecimal.ZERO, result.resultingReservedAmount());
        assertMoney(AVAILABLE, result.resultingAvailableAmount());

        ArgumentCaptor<SalaryAdvanceLimit> limitCaptor =
                ArgumentCaptor.forClass(SalaryAdvanceLimit.class);
        verify(limits).save(limitCaptor.capture());
        SalaryAdvanceLimit persistedLimit = limitCaptor.getValue();
        assertEquals(status, persistedLimit.status());
        assertMoney(money(1_500), persistedLimit.usedAmount());
        assertMoney(BigDecimal.ZERO, persistedLimit.reservedAmount());
        assertMoney(AVAILABLE, persistedLimit.availableAmount());

        ArgumentCaptor<SalaryAdvanceLimitMovement> movementCaptor =
                ArgumentCaptor.forClass(SalaryAdvanceLimitMovement.class);
        verify(movements).save(movementCaptor.capture());
        SalaryAdvanceLimitMovement persistedMovement = movementCaptor.getValue();
        assertEquals(MOVEMENT_ID, persistedMovement.id());
        assertEquals(LIMIT_ID, persistedMovement.salaryAdvanceLimitId());
        assertEquals(fixture.application().id(), persistedMovement.loanApplicationId());
        assertEquals(ACCOUNT_ID, persistedMovement.loanAccountId());
        assertEquals(SalaryAdvanceLimitMovementType.DISBURSED_TO_USED,
                persistedMovement.movementType());
        assertMoney(PRINCIPAL, persistedMovement.amount());
        assertEquals(OCCURRED_AT, persistedMovement.occurredAt());
    }

    @Test
    void acquiresProductLocksInCanonicalOrder() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);

        policy.activate(fixture.command());

        InOrder order = inOrder(verifications, limits, movements);
        order.verify(verifications).findByLoanApplicationIdForUpdate(fixture.application().id());
        order.verify(limits).acquireCustomerLinkLock(fixture.application().customerId(), LINK_ID);
        order.verify(limits).findByIdForUpdate(LIMIT_ID);
        order.verify(movements).findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED);
        order.verify(movements).findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED);
        order.verify(movements).findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.DISBURSED_TO_USED);
    }

    @Test
    void verificationLockOperationIsAbstractAndCannotFallBackToUnlockedRead() throws Exception {
        var method = SalaryAdvanceVerificationRepository.class.getMethod(
                "findByLoanApplicationIdForUpdate",
                UUID.class
        );

        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertFalse(method.isDefault());
    }

    @Test
    void rejectsApplicationThatIsNotSalaryAdvance() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        LoanApplication unsupported = applicationWith(
                fixture.application(),
                fixture.application().id(),
                fixture.application().customerId(),
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED
        );

        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(command(
                unsupported, fixture.contract(), fixture.account()
        )));
    }

    @Test
    void rejectsContractBelongingToAnotherApplication() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        LoanContract wrongContract = contractWithApplication(
                fixture.contract(),
                UUID.randomUUID()
        );

        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(command(
                fixture.application(), wrongContract, fixture.account()
        )));
    }

    @Test
    void rejectsLoanAccountBelongingToAnotherApplication() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        LoanAccount wrongAccount = accountWith(
                fixture,
                UUID.randomUUID(),
                fixture.contract().id(),
                fixture.application().customerId()
        );

        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(command(
                fixture.application(), fixture.contract(), wrongAccount
        )));
    }

    @Test
    void rejectsLoanAccountBelongingToAnotherContract() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        LoanAccount wrongAccount = accountWith(
                fixture,
                fixture.application().id(),
                UUID.randomUUID(),
                fixture.application().customerId()
        );

        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(command(
                fixture.application(), fixture.contract(), wrongAccount
        )));
    }

    @Test
    void rejectsLoanAccountBelongingToAnotherCustomer() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        LoanAccount wrongAccount = accountWith(
                fixture,
                fixture.application().id(),
                fixture.contract().id(),
                UUID.randomUUID()
        );

        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(command(
                fixture.application(), fixture.contract(), wrongAccount
        )));
    }

    @Test
    void rejectsVerificationBelongingToAnotherApplication() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification(
                        fixture, UUID.randomUUID(), fixture.application().customerId(),
                        LIMIT_ID, LINK_ID, ProductVerificationResult.VERIFIED
                )));

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsVerificationBelongingToAnotherCustomer() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification(
                        fixture, fixture.application().id(), UUID.randomUUID(),
                        LIMIT_ID, LINK_ID, ProductVerificationResult.VERIFIED
                )));

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsVerificationReferencingAnotherLimit() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification(
                        fixture, fixture.application().id(), fixture.application().customerId(),
                        UUID.randomUUID(), LINK_ID, ProductVerificationResult.VERIFIED
                )));
        when(limits.findByIdForUpdate(any())).thenReturn(Optional.of(fixture.limit()));

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsReservationBelongingToAnotherApplication() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(SalaryAdvanceLimitMovement.reserved(
                        RESERVATION_ID, LIMIT_ID, UUID.randomUUID(), PRINCIPAL,
                        OCCURRED_AT.minusDays(10)
                )));

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsReservationBelongingToAnotherLimit() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(SalaryAdvanceLimitMovement.reserved(
                        RESERVATION_ID, UUID.randomUUID(), fixture.application().id(), PRINCIPAL,
                        OCCURRED_AT.minusDays(10)
                )));

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsReservationAmountDifferentFromContractPrincipal() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(SalaryAdvanceLimitMovement.reserved(
                        RESERVATION_ID, LIMIT_ID, fixture.application().id(), money(999),
                        OCCURRED_AT.minusDays(10)
                )));

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsMissingReservation() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of());

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsInsufficientReservedBalance() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        SalaryAdvanceLimit insufficient = new SalaryAdvanceLimit(
                LIMIT_ID,
                fixture.application().customerId(),
                LINK_ID,
                money(5_000),
                EXISTING_USED,
                money(999),
                money(3_501),
                SalaryAdvanceLimitStatus.ACTIVE,
                OCCURRED_AT.minusDays(1)
        );
        when(limits.findByIdForUpdate(LIMIT_ID)).thenReturn(Optional.of(insufficient));
        when(movements.calculateOutstandingReservedAmount(LIMIT_ID)).thenReturn(money(999));

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsMissingOrMismatchedVerification() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.empty());

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
        verify(limits, never()).save(any());

        SalaryAdvanceVerification mismatched = verification(
                fixture,
                UUID.randomUUID(),
                fixture.application().customerId(),
                ProductVerificationResult.VERIFIED
        );
        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(mismatched));
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsVerificationForAnotherCustomerOrUnverifiedResult() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification(
                        fixture,
                        fixture.application().id(),
                        UUID.randomUUID(),
                        ProductVerificationResult.VERIFIED
                )));
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));

        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification(
                        fixture,
                        fixture.application().id(),
                        fixture.application().customerId(),
                        ProductVerificationResult.FAILED
                )));
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsMissingOrMismatchedLimit() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        SalaryAdvanceVerification verification = verification(fixture);
        when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification));
        when(limits.findByIdForUpdate(LIMIT_ID)).thenReturn(Optional.empty());

        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));

        SalaryAdvanceLimit wrongCustomer = new SalaryAdvanceLimit(
                LIMIT_ID,
                UUID.randomUUID(),
                LINK_ID,
                money(5_000),
                EXISTING_USED,
                PRINCIPAL,
                AVAILABLE,
                SalaryAdvanceLimitStatus.ACTIVE,
                OCCURRED_AT.minusDays(1)
        );
        when(limits.findByIdForUpdate(LIMIT_ID)).thenReturn(Optional.of(wrongCustomer));
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsMissingOrIncorrectReservationEvidence() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of());
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));

        SalaryAdvanceLimitMovement wrongAmount = SalaryAdvanceLimitMovement.reserved(
                RESERVATION_ID, LIMIT_ID, fixture.application().id(), money(999),
                OCCURRED_AT.minusDays(10)
        );
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(wrongAmount));
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsReleasedOrPreviouslyConvertedReservation() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(),
                SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
        )).thenReturn(List.of(SalaryAdvanceLimitMovement.reservationReleased(
                UUID.randomUUID(), LIMIT_ID, fixture.application().id(), PRINCIPAL, OCCURRED_AT
        )));
        assertCode("SALARY_ADVANCE_RESERVATION_RELEASED",
                () -> policy.activate(fixture.command()));

        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(),
                SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
        )).thenReturn(List.of());
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(),
                SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
        )).thenReturn(List.of(SalaryAdvanceLimitMovement.disbursedToUsed(
                UUID.randomUUID(), LIMIT_ID, fixture.application().id(), ACCOUNT_ID,
                PRINCIPAL, OCCURRED_AT
        )));
        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsAggregateReservedOrUsedMismatch() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);
        when(movements.calculateOutstandingReservedAmount(LIMIT_ID)).thenReturn(money(999));
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));

        when(movements.calculateOutstandingReservedAmount(LIMIT_ID)).thenReturn(PRINCIPAL);
        when(movements.calculateUsedAmount(LIMIT_ID)).thenReturn(money(499));
        assertCode("SALARY_ADVANCE_RESERVATION_INVALID",
                () -> policy.activate(fixture.command()));
    }

    @Test
    void rejectsMismatchedActivationSourcesAndUnsupportedProduct() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        LoanApplication wrongApplication = new LoanApplication(
                UUID.randomUUID(),
                fixture.application().customerId(),
                fixture.application().loanProductId(),
                fixture.application().applicationNumber(),
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                PRINCIPAL,
                1,
                OCCURRED_AT.minusMonths(1)
        );
        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(
                new LoanProductActivationPolicy.ProductActivationCommand(
                        wrongApplication,
                        fixture.contract(),
                        fixture.account(),
                        MOVEMENT_ID,
                        OCCURRED_AT
                )
        ));

        LoanApplication unsupported = new LoanApplication(
                fixture.application().id(),
                fixture.application().customerId(),
                fixture.application().loanProductId(),
                fixture.application().applicationNumber(),
                ProductCode.UNSECURED_CONSUMER_LOAN,
                ProductType.UNSECURED,
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                PRINCIPAL,
                1,
                OCCURRED_AT.minusMonths(1)
        );
        assertCode("SYSTEM_STATE_CONFLICT", () -> policy.activate(
                new LoanProductActivationPolicy.ProductActivationCommand(
                        unsupported,
                        fixture.contract(),
                        fixture.account(),
                        MOVEMENT_ID,
                        OCCURRED_AT
                )
        ));
        verify(limits, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(SalaryAdvanceLimitStatus.class)
    void validatesCompleteExposureWithoutRequiringActiveLimit(
            SalaryAdvanceLimitStatus status
    ) {
        Fixture fixture = fixture(status);
        arrangeCompleted(fixture);

        policy.validateCompletedActivation(fixture.completedCommand());

        verify(limits, never()).save(any());
        verify(movements, never()).save(any());
    }

    @Test
    void rejectsIncompleteOrContradictoryCompletedExposure() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeCompleted(fixture);

        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(),
                SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
        )).thenReturn(List.of());
        assertCode("SYSTEM_STATE_CONFLICT", () ->
                policy.validateCompletedActivation(fixture.completedCommand()));

        arrangeCompleted(fixture);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(),
                SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
        )).thenReturn(List.of(SalaryAdvanceLimitMovement.disbursedToUsed(
                MOVEMENT_ID,
                LIMIT_ID,
                fixture.application().id(),
                UUID.randomUUID(),
                PRINCIPAL,
                OCCURRED_AT
        )));
        assertCode("SYSTEM_STATE_CONFLICT", () ->
                policy.validateCompletedActivation(fixture.completedCommand()));

        arrangeCompleted(fixture);
        when(movements.calculateUsedAmount(LIMIT_ID)).thenReturn(money(1_499));
        assertCode("SYSTEM_STATE_CONFLICT", () ->
                policy.validateCompletedActivation(fixture.completedCommand()));
    }
    @Test
    void commandAndResultToStringRedactOperationalAndFinancialEvidence() {
        Fixture fixture = fixture(SalaryAdvanceLimitStatus.ACTIVE);
        arrangeValid(fixture);

        LoanProductActivationPolicy.ProductActivationResult result =
                policy.activate(fixture.command());

        assertTrue(fixture.command().toString().contains("operationEvidence=redacted"));
        assertFalse(fixture.command().toString().contains(PRINCIPAL.toPlainString()));
        assertTrue(result.toString().contains("exposureAmounts=redacted"));
        assertFalse(result.toString().contains(PRINCIPAL.toPlainString()));
    }

    private void arrangeCompleted(Fixture fixture) {
        SalaryAdvanceLimit completedLimit = new SalaryAdvanceLimit(
                LIMIT_ID,
                fixture.application().customerId(),
                LINK_ID,
                money(5_000),
                money(1_500),
                BigDecimal.ZERO.setScale(2),
                AVAILABLE,
                fixture.limit().status(),
                fixture.limit().lastRefreshedAt()
        );
        lenient().when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification(fixture)));
        lenient().when(limits.findByIdForUpdate(LIMIT_ID))
                .thenReturn(Optional.of(completedLimit));
        lenient().when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(fixture.reservation()));
        lenient().when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED))
                .thenReturn(List.of());
        lenient().when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.DISBURSED_TO_USED))
                .thenReturn(List.of(SalaryAdvanceLimitMovement.disbursedToUsed(
                        MOVEMENT_ID,
                        LIMIT_ID,
                        fixture.application().id(),
                        ACCOUNT_ID,
                        PRINCIPAL,
                        OCCURRED_AT
                )));
        lenient().when(movements.calculateOutstandingReservedAmount(LIMIT_ID))
                .thenReturn(BigDecimal.ZERO.setScale(2));
        lenient().when(movements.calculateUsedAmount(LIMIT_ID))
                .thenReturn(money(1_500));
    }
    private void arrangeValid(Fixture fixture) {
        lenient().when(verifications.findByLoanApplicationIdForUpdate(fixture.application().id()))
                .thenReturn(Optional.of(verification(fixture)));
        lenient().when(limits.findByIdForUpdate(LIMIT_ID))
                .thenReturn(Optional.of(fixture.limit()));
        lenient().when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVED))
                .thenReturn(List.of(fixture.reservation()));
        lenient().when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.RESERVATION_RELEASED))
                .thenReturn(List.of());
        lenient().when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                fixture.application().id(), SalaryAdvanceLimitMovementType.DISBURSED_TO_USED))
                .thenReturn(List.of());
        lenient().when(movements.calculateOutstandingReservedAmount(LIMIT_ID))
                .thenReturn(PRINCIPAL);
        lenient().when(movements.calculateUsedAmount(LIMIT_ID))
                .thenReturn(EXISTING_USED);
        lenient().when(limits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(movements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Fixture fixture(SalaryAdvanceLimitStatus status) {
        LoanContract contract = LoanContractTestData.ready();
        LoanApplication application = new LoanApplication(
                contract.loanApplicationId(),
                contract.disbursementBankAccount().customerId(),
                UUID.fromString("60606060-6060-6060-6060-606060606060"),
                "LA-20260727-0001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                PRINCIPAL,
                contract.financialTerms().approvedTermMonths(),
                OCCURRED_AT.minusMonths(1)
        );
        LoanAccount account = LoanAccount.activate(ACCOUNT_ID, contract, OCCURRED_AT);
        SalaryAdvanceLimit limit = new SalaryAdvanceLimit(
                LIMIT_ID,
                application.customerId(),
                LINK_ID,
                money(5_000),
                EXISTING_USED,
                PRINCIPAL,
                AVAILABLE,
                status,
                OCCURRED_AT.minusDays(1)
        );
        SalaryAdvanceLimitMovement reservation = SalaryAdvanceLimitMovement.reserved(
                RESERVATION_ID,
                LIMIT_ID,
                application.id(),
                PRINCIPAL,
                OCCURRED_AT.minusDays(10)
        );
        return new Fixture(application, contract, account, limit, reservation);
    }

    private static LoanProductActivationPolicy.ProductActivationCommand command(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account
    ) {
        return new LoanProductActivationPolicy.ProductActivationCommand(
                application,
                contract,
                account,
                MOVEMENT_ID,
                OCCURRED_AT
        );
    }

    private static LoanApplication applicationWith(
            LoanApplication source,
            UUID applicationId,
            UUID customerId,
            ProductCode productCode,
            ProductType productType
    ) {
        return new LoanApplication(
                applicationId,
                customerId,
                source.loanProductId(),
                source.applicationNumber(),
                productCode,
                productType,
                source.status(),
                source.requestedAmount(),
                source.requestedTermMonths(),
                source.submittedAt()
        );
    }

    private static LoanContract contractWithApplication(
            LoanContract source,
            UUID applicationId
    ) {
        return new LoanContract(
                source.id(), applicationId, source.approvedOfferId(), source.contractReference(),
                source.contractVersion(), source.status(), source.financialTerms(),
                source.repaymentItems(), source.disbursementBankAccount(),
                source.preparationRequestId(), source.expectedPreviousVersion(),
                source.supersessionReason(), source.preparedByUserId(), source.preparedAt(),
                source.acknowledgmentRequestId(), source.acknowledgedByUserId(),
                source.acknowledgedAt(), source.confirmationRequestId(),
                source.confirmedByUserId(), source.confirmedAt(), source.supersedesContractId(),
                source.supersededByUserId(), source.supersededAt()
        );
    }

    private static LoanAccount accountWith(
            Fixture fixture,
            UUID applicationId,
            UUID contractId,
            UUID customerId
    ) {
        LoanAccount source = fixture.account();
        return new LoanAccount(
                source.id(),
                applicationId,
                contractId,
                customerId,
                source.accountNumber(),
                source.status(),
                source.approvedPrincipal(),
                source.approvedTermMonths(),
                source.totalInterest(),
                source.feeAmount(),
                source.totalRepaymentAmount(),
                source.activatedAt()
        );
    }

    private static SalaryAdvanceVerification verification(Fixture fixture) {
        return verification(
                fixture,
                fixture.application().id(),
                fixture.application().customerId(),
                ProductVerificationResult.VERIFIED
        );
    }

    private static SalaryAdvanceVerification verification(
            Fixture fixture,
            UUID applicationId,
            UUID customerId,
            UUID limitId,
            UUID linkId,
            ProductVerificationResult result
    ) {
        return verification(
                fixture, applicationId, customerId, limitId, linkId, result,
                UUID.fromString("70707070-7070-7070-7070-707070707070")
        );
    }

    private static SalaryAdvanceVerification verification(
            Fixture fixture,
            UUID applicationId,
            UUID customerId,
            ProductVerificationResult result
    ) {
        return verification(fixture, applicationId, customerId, LIMIT_ID, LINK_ID, result,
                UUID.fromString("70707070-7070-7070-7070-707070707070"));
    }

    private static SalaryAdvanceVerification verification(
            Fixture fixture,
            UUID applicationId,
            UUID customerId,
            UUID limitId,
            UUID linkId,
            ProductVerificationResult result,
            UUID verificationId
    ) {
        return new SalaryAdvanceVerification(
                verificationId,
                applicationId,
                customerId,
                linkId,
                limitId,
                UUID.fromString("80808080-8080-8080-8080-808080808080"),
                UUID.fromString("90909090-9090-9090-9090-909090909090"),
                UUID.fromString("abababab-abab-abab-abab-abababababab"),
                SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                result,
                fixture.limit().totalLimit(),
                fixture.limit().usedAmount(),
                fixture.limit().reservedAmount(),
                fixture.limit().availableAmount(),
                OCCURRED_AT.minusDays(10)
        );
    }

    private static void assertCode(String expected, Runnable operation) {
        BusinessStateConflictException exception =
                assertThrows(BusinessStateConflictException.class, operation::run);
        assertEquals(expected, exception.getErrorCode());
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount).setScale(2);
    }

    private record Fixture(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account,
            SalaryAdvanceLimit limit,
            SalaryAdvanceLimitMovement reservation
    ) {
        LoanProductActivationPolicy.ProductActivationCommand command() {
            return new LoanProductActivationPolicy.ProductActivationCommand(
                    application,
                    contract,
                    account,
                    MOVEMENT_ID,
                    OCCURRED_AT
            );
        }

        LoanProductActivationPolicy.CompletedActivationValidationCommand completedCommand() {
            return new LoanProductActivationPolicy.CompletedActivationValidationCommand(
                    application,
                    contract,
                    account
            );
        }
    }
}
