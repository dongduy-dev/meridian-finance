package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractRepaymentItem;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.ProtectedDisbursementBankAccount;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollateralLoanActivationPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 10, 0);

    @Mock
    private CollateralLoanVerificationRepository verifications;

    private CollateralLoanActivationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new CollateralLoanActivationPolicy(verifications);
    }

    @Test
    void supportsCollateralAndActivatesVerifiedEvidenceWithoutExposureEffect() {
        Fixture fixture = fixture();
        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.of(fixture.verification));

        LoanProductActivationPolicy.ProductActivationResult result =
                policy.activate(fixture.activationCommand());

        assertEquals(ProductCode.COLLATERAL_LOAN, policy.supportedProduct());
        assertEquals(ProductCode.COLLATERAL_LOAN, result.productCode());
        assertFalse(result.exposureEffect().isPresent());
        policy.validateCompletedActivation(fixture.completedCommand());
    }

    @Test
    void rejectsMissingAndEveryNonVerifiedOutcomeBeforeActivation() {
        Fixture fixture = fixture();
        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.empty());
        assertInvalidVerification(() -> policy.activate(fixture.activationCommand()));

        for (ProductVerificationResult result : List.of(
                ProductVerificationResult.PENDING_MANUAL_REVIEW,
                ProductVerificationResult.FAILED,
                ProductVerificationResult.REQUIRES_MORE_INFORMATION
        )) {
            when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                    .thenReturn(Optional.of(verification(fixture.application.id(), result)));
            assertInvalidVerification(() -> policy.activate(fixture.activationCommand()));
        }
    }

    @Test
    void rejectsWrongProductNonReadyContractAndMismatchedReferences() {
        Fixture fixture = fixture();
        LoanApplication wrongProduct = application(
                fixture.application.id(), fixture.application.customerId(),
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED
        );
        assertSystemConflict(() -> policy.activate(command(
                wrongProduct, fixture.contract, fixture.account
        )));

        assertSystemConflict(() -> policy.activate(command(
                fixture.application, fixture.preparedContract, fixture.account
        )));

        assertSystemConflict(() -> policy.activate(command(
                application(UUID.randomUUID(), fixture.application.customerId(),
                        ProductCode.COLLATERAL_LOAN, ProductType.SECURED),
                fixture.contract,
                fixture.account
        )));
        assertSystemConflict(() -> policy.activate(command(
                fixture.application,
                fixture.contract,
                account(fixture, UUID.randomUUID(), fixture.contract.id(),
                        fixture.application.customerId(), fixture.account.approvedPrincipal())
        )));
        assertSystemConflict(() -> policy.activate(command(
                fixture.application,
                fixture.contract,
                account(fixture, fixture.application.id(), UUID.randomUUID(),
                        fixture.application.customerId(), fixture.account.approvedPrincipal())
        )));
        assertSystemConflict(() -> policy.activate(command(
                fixture.application,
                fixture.contract,
                account(fixture, fixture.application.id(), fixture.contract.id(),
                        UUID.randomUUID(), fixture.account.approvedPrincipal())
        )));
    }

    @Test
    void rejectsLoanAccountFinancialSnapshotMismatch() {
        Fixture fixture = fixture();

        assertSystemConflict(() -> policy.activate(command(
                fixture.application,
                fixture.contract,
                account(fixture, fixture.application.id(), fixture.contract.id(),
                        fixture.application.customerId(), money(24_999_999))
        )));
    }

    @Test
    void completedActivationValidationFailsClosedOnMissingOrInconsistentEvidence() {
        Fixture fixture = fixture();
        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.empty());
        assertSystemConflict(() -> policy.validateCompletedActivation(fixture.completedCommand()));

        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.of(verification(
                        fixture.application.id(), ProductVerificationResult.FAILED
                )));
        assertSystemConflict(() -> policy.validateCompletedActivation(fixture.completedCommand()));
    }

    private static void assertInvalidVerification(Runnable action) {
        BusinessStateConflictException failure = assertThrows(
                BusinessStateConflictException.class, action::run
        );
        assertEquals("COLLATERAL_VERIFICATION_INVALID", failure.getErrorCode());
    }

    private static void assertSystemConflict(Runnable action) {
        BusinessStateConflictException failure = assertThrows(
                BusinessStateConflictException.class, action::run
        );
        assertEquals("SYSTEM_STATE_CONFLICT", failure.getErrorCode());
    }

    private static LoanProductActivationPolicy.ProductActivationCommand command(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account
    ) {
        return new LoanProductActivationPolicy.ProductActivationCommand(
                application, contract, account, UUID.randomUUID(), NOW
        );
    }

    private static Fixture fixture() {
        UUID applicationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        LoanApplication application = application(
                applicationId, customerId, ProductCode.COLLATERAL_LOAN, ProductType.SECURED
        );
        ApprovedOfferFinancialTerms terms = new ApprovedOfferFinancialTerms(
                money(25_000_000), 12, InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"), money(4_500_000), money(0), money(29_500_000),
                RepaymentMethod.MONTHLY_INSTALLMENT
        );
        List<LoanContractRepaymentItem> items = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(number -> new LoanContractRepaymentItem(
                        UUID.randomUUID(), UUID.randomUUID(), number,
                        number == 12 ? money(2_083_337) : money(2_083_333),
                        money(375_000), money(0),
                        number == 12 ? money(2_458_337) : money(2_458_333)
                ))
                .toList();
        LoanContract prepared = LoanContract.prepared(
                UUID.randomUUID(), applicationId, UUID.randomUUID(), "MCT-COLLATERAL-ACTIVATION", 1,
                terms, items,
                new ProtectedDisbursementBankAccount(
                        customerId, UUID.randomUUID(), "TEST", "Test Bank", "COLLATERAL CUSTOMER",
                        "5678", true, true, NOW.minusDays(3), "AES-256-GCM", "v1",
                        new byte[12], new byte[]{1}, "DISBURSEMENT_ACCOUNT_V1"
                ),
                UUID.randomUUID(), null, null, UUID.randomUUID(), NOW.minusDays(3), null
        );
        LoanContract ready = prepared
                .acknowledge(UUID.randomUUID(), UUID.randomUUID(), NOW.minusDays(2))
                .confirmReady(UUID.randomUUID(), UUID.randomUUID(), NOW.minusDays(1));
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), ready, NOW);
        return new Fixture(
                application, prepared, ready, account,
                verification(applicationId, ProductVerificationResult.VERIFIED)
        );
    }

    private static LoanApplication application(
            UUID applicationId,
            UUID customerId,
            ProductCode productCode,
            ProductType productType
    ) {
        return new LoanApplication(
                applicationId, customerId, UUID.randomUUID(), "COLLATERAL-ACTIVATION-1",
                productCode, productType, LoanApplicationStatus.DISBURSEMENT_PENDING,
                money(25_000_000), 12, NOW.minusDays(10)
        );
    }

    private static CollateralLoanVerification verification(
            UUID applicationId,
            ProductVerificationResult result
    ) {
        if (result == ProductVerificationResult.PENDING_MANUAL_REVIEW) {
            return new CollateralLoanVerification(
                    UUID.randomUUID(), applicationId, result, NOW.minusDays(8)
            );
        }
        return new CollateralLoanVerification(
                UUID.randomUUID(), applicationId, 1, null, result,
                NOW.minusDays(8), UUID.randomUUID(), NOW.minusDays(7),
                "Reviewed Collateral evidence."
        );
    }

    private static LoanAccount account(
            Fixture fixture,
            UUID applicationId,
            UUID contractId,
            UUID customerId,
            BigDecimal approvedPrincipal
    ) {
        BigDecimal totalRepayment = approvedPrincipal
                .add(fixture.account.totalInterest())
                .add(fixture.account.feeAmount());
        return new LoanAccount(
                fixture.account.id(), applicationId, contractId, customerId,
                fixture.account.accountNumber(), fixture.account.status(), approvedPrincipal,
                fixture.account.approvedTermMonths(), fixture.account.totalInterest(),
                fixture.account.feeAmount(), totalRepayment,
                fixture.account.activatedAt()
        );
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private record Fixture(
            LoanApplication application,
            LoanContract preparedContract,
            LoanContract contract,
            LoanAccount account,
            CollateralLoanVerification verification
    ) {
        LoanProductActivationPolicy.ProductActivationCommand activationCommand() {
            return command(application, contract, account);
        }

        LoanProductActivationPolicy.CompletedActivationValidationCommand completedCommand() {
            return new LoanProductActivationPolicy.CompletedActivationValidationCommand(
                    application, contract, account
            );
        }
    }
}
