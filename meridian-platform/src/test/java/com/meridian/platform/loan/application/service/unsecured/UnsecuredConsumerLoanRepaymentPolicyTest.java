package com.meridian.platform.loan.application.service.unsecured;

import com.meridian.platform.loan.application.service.LoanProductRepaymentPolicy;

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
class UnsecuredConsumerLoanRepaymentPolicyTest {

    @Mock LoanApplication application;
    @Mock LoanAccount account;

    private final UnsecuredConsumerLoanRepaymentPolicy policy =
            new UnsecuredConsumerLoanRepaymentPolicy();
    private UUID applicationId;
    private UUID accountId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        when(application.id()).thenReturn(applicationId);
        when(application.customerId()).thenReturn(customerId);
        when(application.productCode()).thenReturn(ProductCode.UNSECURED_CONSUMER_LOAN);
        when(account.id()).thenReturn(accountId);
        when(account.loanApplicationId()).thenReturn(applicationId);
        when(account.customerId()).thenReturn(customerId);
        when(account.approvedPrincipal()).thenReturn(money("1000"));
    }

    @Test
    void allocatesContractualPrincipalWithoutCreatingProductExposureRelease() {
        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = List.of(
                allocation(transactionId, RepaymentAllocationComponent.INTEREST, "50"),
                allocation(transactionId, RepaymentAllocationComponent.PRINCIPAL, "250")
        );

        BigDecimal released = policy.releasePrincipal(
                new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                        application, account, transactionId, allocations,
                        LocalDateTime.of(2026, 8, 12, 10, 0)
                )
        );

        assertEquals(0, released.compareTo(BigDecimal.ZERO));
        assertDoesNotThrow(() -> policy.validateCompletedRelease(
                new LoanProductRepaymentPolicy.CompletedReleaseCommand(
                        application, account, transactionId, allocations, BigDecimal.ZERO
                )
        ));
    }

    @Test
    void rejectsNonZeroReleaseAndMismatchedOperationIdentity() {
        UUID transactionId = UUID.randomUUID();
        List<RepaymentAllocation> allocations = List.of(
                allocation(transactionId, RepaymentAllocationComponent.PRINCIPAL, "100")
        );

        assertThrows(BusinessStateConflictException.class,
                () -> policy.validateCompletedRelease(
                        new LoanProductRepaymentPolicy.CompletedReleaseCommand(
                                application, account, transactionId, allocations, money("100")
                        )
                ));
        assertThrows(BusinessStateConflictException.class,
                () -> policy.releasePrincipal(
                        new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                                application, account, UUID.randomUUID(), allocations,
                                LocalDateTime.of(2026, 8, 12, 10, 0)
                        )
                ));
    }

    @Test
    void validatesCompletedPayoffUsingZeroReleaseEvidenceOnly() {
        var first = new LoanProductRepaymentPolicy.ReleaseEvidence(
                UUID.randomUUID(), money("400"), BigDecimal.ZERO
        );
        var second = new LoanProductRepaymentPolicy.ReleaseEvidence(
                UUID.randomUUID(), money("600"), BigDecimal.ZERO
        );

        assertDoesNotThrow(() -> policy.validateCompletedPayoff(
                new LoanProductRepaymentPolicy.CompletedPayoffCommand(
                        application, account, List.of(first, second)
                )
        ));
        assertThrows(BusinessStateConflictException.class,
                () -> policy.validateCompletedPayoff(
                        new LoanProductRepaymentPolicy.CompletedPayoffCommand(
                                application, account, List.of(first,
                                new LoanProductRepaymentPolicy.ReleaseEvidence(
                                        UUID.randomUUID(), money("600"), money("600")
                                ))
                        )
                ));
    }

    @Test
    void rejectsNonUclAndCrossCustomerAccounts() {
        when(application.productCode()).thenReturn(ProductCode.COLLATERAL_LOAN);
        assertThrows(BusinessStateConflictException.class,
                () -> policy.releasePrincipal(command(UUID.randomUUID(), List.of())));

        when(application.productCode()).thenReturn(ProductCode.UNSECURED_CONSUMER_LOAN);
        when(account.customerId()).thenReturn(UUID.randomUUID());
        assertThrows(BusinessStateConflictException.class,
                () -> policy.releasePrincipal(command(UUID.randomUUID(), List.of())));
    }

    private LoanProductRepaymentPolicy.PrincipalReleaseCommand command(
            UUID transactionId,
            List<RepaymentAllocation> allocations
    ) {
        return new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                application, account, transactionId, allocations,
                LocalDateTime.of(2026, 8, 12, 10, 0)
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

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
