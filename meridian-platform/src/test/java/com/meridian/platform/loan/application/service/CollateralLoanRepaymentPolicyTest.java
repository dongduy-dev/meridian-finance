package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollateralLoanRepaymentPolicyTest {

    @Mock LoanApplication application;
    @Mock LoanAccount account;

    private final CollateralLoanRepaymentPolicy policy =
            new CollateralLoanRepaymentPolicy();
    private UUID applicationId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        when(application.id()).thenReturn(applicationId);
        when(application.customerId()).thenReturn(customerId);
        when(application.productCode()).thenReturn(ProductCode.COLLATERAL_LOAN);
        when(account.loanApplicationId()).thenReturn(applicationId);
        when(account.customerId()).thenReturn(customerId);
        when(account.approvedPrincipal()).thenReturn(money("1000"));
    }

    @Test
    void supportsCollateralAndReleasesNoProductExposure() {
        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = List.of(
                allocation(transactionId, RepaymentAllocationComponent.INTEREST, "50"),
                allocation(transactionId, RepaymentAllocationComponent.PRINCIPAL, "250")
        );

        assertEquals(ProductCode.COLLATERAL_LOAN, policy.supportedProduct());
        assertEquals(0, policy.releasePrincipal(command(transactionId, allocations))
                .compareTo(BigDecimal.ZERO));
        assertDoesNotThrow(() -> policy.validateCompletedRelease(
                new LoanProductRepaymentPolicy.CompletedReleaseCommand(
                        application, account, transactionId, allocations, BigDecimal.ZERO
                )
        ));
    }

    @Test
    void rejectsWrongProductAndApplicationOrCustomerIdentityMismatch() {
        when(application.productCode()).thenReturn(ProductCode.UNSECURED_CONSUMER_LOAN);
        assertConflict(() -> policy.releasePrincipal(command(UUID.randomUUID(), List.of())));

        when(application.productCode()).thenReturn(ProductCode.COLLATERAL_LOAN);
        when(account.loanApplicationId()).thenReturn(UUID.randomUUID());
        assertConflict(() -> policy.releasePrincipal(command(UUID.randomUUID(), List.of())));

        when(account.loanApplicationId()).thenReturn(applicationId);
        when(account.customerId()).thenReturn(UUID.randomUUID());
        assertConflict(() -> policy.releasePrincipal(command(UUID.randomUUID(), List.of())));
    }

    @Test
    void rejectsAllocationFromAnotherRepaymentTransaction() {
        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = List.of(allocation(
                UUID.randomUUID(), RepaymentAllocationComponent.PRINCIPAL, "100"
        ));

        assertConflict(() -> policy.releasePrincipal(command(transactionId, allocations)));
        assertConflict(() -> policy.validateReleaseSemantics(
                new LoanProductRepaymentPolicy.ReleaseValidationCommand(
                        application, account, transactionId, allocations, BigDecimal.ZERO
                )
        ));
    }

    @Test
    void rejectsNonZeroCompletedRelease() {
        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = List.of(allocation(
                transactionId, RepaymentAllocationComponent.PRINCIPAL, "100"
        ));

        assertConflict(() -> policy.validateCompletedRelease(
                new LoanProductRepaymentPolicy.CompletedReleaseCommand(
                        application, account, transactionId, allocations, money("100")
                )
        ));
    }

    @Test
    void acceptsCompletedPayoffWithExactPrincipalAndZeroRelease() {
        var first = evidence(UUID.randomUUID(), "400", "0");
        var second = evidence(UUID.randomUUID(), "600", "0");

        assertDoesNotThrow(() -> policy.validateCompletedPayoff(
                new LoanProductRepaymentPolicy.CompletedPayoffCommand(
                        application, account, List.of(first, second)
                )
        ));
    }

    @Test
    void rejectsDuplicateNegativeReleasedOrIncompletePayoffEvidence() {
        UUID transactionId = UUID.randomUUID();
        assertConflict(() -> validatePayoff(List.of(
                evidence(transactionId, "400", "0"),
                evidence(transactionId, "600", "0")
        )));
        assertConflict(() -> validatePayoff(List.of(
                evidence(UUID.randomUUID(), "-1", "0"),
                evidence(UUID.randomUUID(), "1001", "0")
        )));
        assertConflict(() -> validatePayoff(List.of(
                evidence(UUID.randomUUID(), "400", "1"),
                evidence(UUID.randomUUID(), "600", "0")
        )));
        assertConflict(() -> validatePayoff(List.of(
                evidence(UUID.randomUUID(), "999", "0")
        )));
    }

    private void validatePayoff(List<LoanProductRepaymentPolicy.ReleaseEvidence> evidence) {
        policy.validateCompletedPayoff(new LoanProductRepaymentPolicy.CompletedPayoffCommand(
                application, account, evidence
        ));
    }

    private LoanProductRepaymentPolicy.PrincipalReleaseCommand command(
            UUID transactionId,
            List<RepaymentAllocation> allocations
    ) {
        return new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                application, account, transactionId, allocations,
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
    }

    private static RepaymentAllocation allocation(
            UUID transactionId,
            RepaymentAllocationComponent component,
            String amount
    ) {
        return new RepaymentAllocation(
                UUID.randomUUID(), transactionId, 1, UUID.randomUUID(), component,
                money(amount)
        );
    }

    private static LoanProductRepaymentPolicy.ReleaseEvidence evidence(
            UUID transactionId,
            String allocated,
            String released
    ) {
        return new LoanProductRepaymentPolicy.ReleaseEvidence(
                transactionId, money(allocated), money(released)
        );
    }

    private static void assertConflict(Runnable action) {
        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class, action::run
        );
        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
