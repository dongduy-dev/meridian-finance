package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;
import com.meridian.platform.loan.domain.model.RepaymentAllocationComponent;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryAdvanceRepaymentPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 10, 0);

    @Mock SalaryAdvanceVerificationRepository verifications;
    @Mock SalaryAdvanceLimitRepository limits;
    @Mock SalaryAdvanceLimitMovementRepository movements;
    @Mock LoanApplication application;
    @Mock LoanAccount account;
    @Mock SalaryAdvanceVerification verification;

    private UUID applicationId;
    private UUID accountId;
    private UUID customerId;
    private UUID linkId;
    private UUID limitId;
    private SalaryAdvanceRepaymentPolicy policy;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        linkId = UUID.randomUUID();
        limitId = UUID.randomUUID();
        policy = new SalaryAdvanceRepaymentPolicy(verifications, limits, movements);

        when(application.id()).thenReturn(applicationId);
        when(application.customerId()).thenReturn(customerId);
        when(application.productCode()).thenReturn(ProductCode.SALARY_ADVANCE);
        when(account.loanApplicationId()).thenReturn(applicationId);
        when(account.customerId()).thenReturn(customerId);
        when(verification.customerId()).thenReturn(customerId);
        when(verification.customerPartnerEmployeeLinkId()).thenReturn(linkId);
        when(verification.salaryAdvanceLimitId()).thenReturn(limitId);
        when(verification.productVerificationResult())
                .thenReturn(ProductVerificationResult.VERIFIED);
        when(verifications.findByLoanApplicationIdForUpdate(applicationId))
                .thenReturn(Optional.of(verification));
    }

    @ParameterizedTest
    @EnumSource(SalaryAdvanceLimitStatus.class)
    void releasesPrincipalExactlyForEveryServicingLimitStatus(
            SalaryAdvanceLimitStatus status
    ) {
        SalaryAdvanceLimit limit = limit(status);
        UUID transactionId = UUID.randomUUID();
        RepaymentAllocation allocation = new RepaymentAllocation(
                UUID.randomUUID(), transactionId, 1, UUID.randomUUID(),
                RepaymentAllocationComponent.PRINCIPAL, money("100")
        );
        stubEvidence(limit, List.of());
        when(limits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal released = policy.releasePrincipal(
                new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                        application, account, transactionId, List.of(allocation), NOW
                )
        );

        assertEquals(money("100"), released);
        ArgumentCaptor<SalaryAdvanceLimit> limitCaptor =
                ArgumentCaptor.forClass(SalaryAdvanceLimit.class);
        verify(limits).save(limitCaptor.capture());
        SalaryAdvanceLimit updated = limitCaptor.getValue();
        assertEquals(money("400"), updated.usedAmount());
        assertEquals(money("600"), updated.availableAmount());
        assertEquals(money("0"), updated.reservedAmount());
        assertEquals(money("1000"), updated.totalLimit());
        assertEquals(NOW.minusDays(1), updated.lastRefreshedAt());
        assertEquals(status, updated.status());

        ArgumentCaptor<SalaryAdvanceLimitMovement> movementCaptor =
                ArgumentCaptor.forClass(SalaryAdvanceLimitMovement.class);
        verify(movements).save(movementCaptor.capture());
        SalaryAdvanceLimitMovement movement = movementCaptor.getValue();
        assertEquals(SalaryAdvanceLimitMovementType.REPAID_RELEASED,
                movement.movementType());
        assertEquals(money("100"), movement.amount());
        assertEquals(limitId, movement.salaryAdvanceLimitId());
        assertEquals(applicationId, movement.loanApplicationId());
        assertEquals(accountId, movement.loanAccountId());
        assertEquals(transactionId, movement.repaymentTransactionId());
    }

    @Test
    void feeOnlyRepaymentStillRequiresAuthoritativeConversionAndReleasesNothing() {
        SalaryAdvanceLimit limit = limit(SalaryAdvanceLimitStatus.SUSPENDED);
        UUID transactionId = UUID.randomUUID();
        RepaymentAllocation allocation = new RepaymentAllocation(
                UUID.randomUUID(), transactionId, 1, UUID.randomUUID(),
                RepaymentAllocationComponent.FEE, money("100")
        );
        when(limits.findByIdForUpdate(limitId)).thenReturn(Optional.of(limit));
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                applicationId, SalaryAdvanceLimitMovementType.REPAID_RELEASED
        )).thenReturn(List.of());
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                applicationId, SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
        )).thenReturn(List.of());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> policy.releasePrincipal(
                        new LoanProductRepaymentPolicy.PrincipalReleaseCommand(
                                application, account, transactionId,
                                List.of(allocation), NOW
                        )
                )
        );

        assertEquals("SYSTEM_STATE_CONFLICT", exception.getErrorCode());
        verify(limits, never()).save(any());
        verify(movements, never()).save(any());
    }

    private void stubEvidence(
            SalaryAdvanceLimit limit,
            List<SalaryAdvanceLimitMovement> releases
    ) {
        when(account.id()).thenReturn(accountId);
        when(account.approvedPrincipal()).thenReturn(money("500"));
        SalaryAdvanceLimitMovement conversion =
                SalaryAdvanceLimitMovement.disbursedToUsed(
                        UUID.randomUUID(), limitId, applicationId, accountId,
                        money("500"), NOW.minusHours(1)
                );
        when(limits.findByIdForUpdate(limitId)).thenReturn(Optional.of(limit));
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                applicationId, SalaryAdvanceLimitMovementType.REPAID_RELEASED
        )).thenReturn(releases);
        when(movements.findByLoanApplicationIdAndMovementTypeForUpdate(
                applicationId, SalaryAdvanceLimitMovementType.DISBURSED_TO_USED
        )).thenReturn(List.of(conversion));
        when(movements.calculateUsedAmount(limitId)).thenReturn(limit.usedAmount());
    }

    private SalaryAdvanceLimit limit(SalaryAdvanceLimitStatus status) {
        return new SalaryAdvanceLimit(
                limitId, customerId, linkId, money("1000"), money("500"),
                money("0"), money("500"), status, NOW.minusDays(1)
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
