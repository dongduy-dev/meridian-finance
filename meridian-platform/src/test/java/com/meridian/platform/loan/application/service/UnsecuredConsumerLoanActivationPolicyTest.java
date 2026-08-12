package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
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
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
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
class UnsecuredConsumerLoanActivationPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 10, 0);

    @Mock
    private UnsecuredConsumerLoanVerificationRepository verifications;

    private UnsecuredConsumerLoanActivationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UnsecuredConsumerLoanActivationPolicy(verifications);
    }

    @Test
    void validatesVerifiedUclActivationWithoutExposureEffect() {
        Fixture fixture = fixture();
        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.of(fixture.verification));

        LoanProductActivationPolicy.ProductActivationResult result =
                policy.activate(fixture.activationCommand());

        assertEquals(ProductCode.UNSECURED_CONSUMER_LOAN, result.productCode());
        assertFalse(result.exposureEffect().isPresent());
        policy.validateCompletedActivation(fixture.completedCommand());
    }

    @Test
    void rejectsMissingOrNonVerifiedEvidenceBeforeActivation() {
        Fixture fixture = fixture();
        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.empty());

        BusinessStateConflictException missing = assertThrows(
                BusinessStateConflictException.class,
                () -> policy.activate(fixture.activationCommand())
        );
        assertEquals("UCL_VERIFICATION_INVALID", missing.getErrorCode());

        UnsecuredConsumerLoanVerification pending = new UnsecuredConsumerLoanVerification(
                fixture.verification.id(), fixture.application.id(),
                ProductVerificationResult.PENDING_MANUAL_REVIEW, NOW.minusDays(2),
                null, null, null
        );
        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.of(pending));
        BusinessStateConflictException pendingFailure = assertThrows(
                BusinessStateConflictException.class,
                () -> policy.activate(fixture.activationCommand())
        );
        assertEquals("UCL_VERIFICATION_INVALID", pendingFailure.getErrorCode());
    }

    @Test
    void rejectsMismatchedProductAndActivationReferences() {
        Fixture fixture = fixture();
        LoanApplication salaryApplication = new LoanApplication(
                fixture.application.id(), fixture.application.customerId(),
                fixture.application.loanProductId(), fixture.application.applicationNumber(),
                ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED,
                LoanApplicationStatus.DISBURSEMENT_PENDING,
                fixture.application.requestedAmount(), fixture.application.requestedTermMonths(),
                fixture.application.submittedAt()
        );
        assertSystemConflict(() -> policy.activate(
                new LoanProductActivationPolicy.ProductActivationCommand(
                        salaryApplication, fixture.contract, fixture.account,
                        UUID.randomUUID(), NOW
                )
        ));

        LoanAccount wrongContractAccount = new LoanAccount(
                fixture.account.id(), fixture.application.id(), UUID.randomUUID(),
                fixture.application.customerId(), fixture.account.accountNumber(),
                fixture.account.status(), fixture.account.approvedPrincipal(),
                fixture.account.approvedTermMonths(), fixture.account.totalInterest(),
                fixture.account.feeAmount(), fixture.account.totalRepaymentAmount(),
                fixture.account.activatedAt()
        );
        assertSystemConflict(() -> policy.activate(
                new LoanProductActivationPolicy.ProductActivationCommand(
                        fixture.application, fixture.contract, wrongContractAccount,
                        UUID.randomUUID(), NOW
                )
        ));
    }

    @Test
    void completedReplayFailsClosedWhenVerificationEvidenceIsMissing() {
        Fixture fixture = fixture();
        when(verifications.findLatestByLoanApplicationId(fixture.application.id()))
                .thenReturn(Optional.empty());

        assertSystemConflict(() -> policy.validateCompletedActivation(
                fixture.completedCommand()
        ));
    }

    private static void assertSystemConflict(Runnable action) {
        BusinessStateConflictException failure = assertThrows(
                BusinessStateConflictException.class,
                action::run
        );
        assertEquals("SYSTEM_STATE_CONFLICT", failure.getErrorCode());
    }

    private static Fixture fixture() {
        UUID applicationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        LoanApplication application = new LoanApplication(
                applicationId, customerId, UUID.randomUUID(), "UCL-ACTIVATION-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.DISBURSEMENT_PENDING, money(3_000_000), 3,
                NOW.minusDays(10)
        );
        ApprovedOfferFinancialTerms terms = new ApprovedOfferFinancialTerms(
                money(3_000_000), 3, InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.018000"), money(162_000), money(0), money(3_162_000),
                RepaymentMethod.MONTHLY_INSTALLMENT
        );
        List<LoanContractRepaymentItem> items = java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(number -> new LoanContractRepaymentItem(
                        UUID.randomUUID(), UUID.randomUUID(), number,
                        money(1_000_000), money(54_000), money(0), money(1_054_000)
                ))
                .toList();
        LoanContract prepared = LoanContract.prepared(
                UUID.randomUUID(), applicationId, UUID.randomUUID(), "MCT-UCL-ACTIVATION", 1,
                terms, items,
                new ProtectedDisbursementBankAccount(
                        customerId, UUID.randomUUID(), "TEST", "Test Bank", "UCL CUSTOMER",
                        "5678", true, true, NOW.minusDays(3), "AES-256-GCM", "v1",
                        new byte[12], new byte[]{1}, "DISBURSEMENT_ACCOUNT_V1"
                ),
                UUID.randomUUID(), null, null, UUID.randomUUID(), NOW.minusDays(3), null
        );
        LoanContract ready = prepared
                .acknowledge(UUID.randomUUID(), UUID.randomUUID(), NOW.minusDays(2))
                .confirmReady(UUID.randomUUID(), UUID.randomUUID(), NOW.minusDays(1));
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), ready, NOW);
        UnsecuredConsumerLoanVerification verification = new UnsecuredConsumerLoanVerification(
                UUID.randomUUID(), applicationId, ProductVerificationResult.VERIFIED,
                NOW.minusDays(8), UUID.randomUUID(), NOW.minusDays(7),
                "Verified UCL evidence."
        );
        return new Fixture(application, ready, account, verification);
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private record Fixture(
            LoanApplication application,
            LoanContract contract,
            LoanAccount account,
            UnsecuredConsumerLoanVerification verification
    ) {
        LoanProductActivationPolicy.ProductActivationCommand activationCommand() {
            return new LoanProductActivationPolicy.ProductActivationCommand(
                    application, contract, account, UUID.randomUUID(), NOW
            );
        }

        LoanProductActivationPolicy.CompletedActivationValidationCommand completedCommand() {
            return new LoanProductActivationPolicy.CompletedActivationValidationCommand(
                    application, contract, account
            );
        }
    }
}
