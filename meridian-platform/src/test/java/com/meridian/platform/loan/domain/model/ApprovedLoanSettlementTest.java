package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovedLoanSettlementTest {

    @Test
    void derivesImmutableSettlementIdentityFromPaymentEvidence() {
        RepaymentTransaction payment = settlementPayment();
        ApprovedLoanSettlement settlement = ApprovedLoanSettlement.from(
                UUID.randomUUID(),
                payment
        );

        assertEquals(payment.loanApplicationId(), settlement.loanApplicationId());
        assertEquals(payment.loanAccountId(), settlement.loanAccountId());
        assertEquals(payment.id(), settlement.repaymentTransactionId());
        assertEquals(payment.requestId(), settlement.requestId());
        assertEquals(payment.receivedAmount(), settlement.settlementAmount());
        assertEquals(payment.recordedByUserId(), settlement.approvedByUserId());
        assertEquals(payment.recordedAt(), settlement.approvedAt());
        assertFalse(settlement.toString().contains(payment.requestId().toString()));
    }

    @Test
    void rejectsOrdinaryRepaymentAsApprovedSettlementEvidence() {
        RepaymentTransaction settlementPayment = settlementPayment();
        RepaymentTransaction repayment = new RepaymentTransaction(
                settlementPayment.id(),
                settlementPayment.loanApplicationId(),
                settlementPayment.loanAccountId(),
                settlementPayment.repaymentScheduleId(),
                settlementPayment.requestId(),
                settlementPayment.externalPaymentReference(),
                settlementPayment.receivedAmount(),
                settlementPayment.paymentValueDate(),
                settlementPayment.recordedByUserId(),
                settlementPayment.recordedAt(),
                settlementPayment.allocations()
        );

        assertThrows(
                BusinessRuleViolationException.class,
                () -> ApprovedLoanSettlement.from(UUID.randomUUID(), repayment)
        );
    }

    private static RepaymentTransaction settlementPayment() {
        UUID transactionId = UUID.randomUUID();
        return RepaymentTransaction.approvedSettlement(
                transactionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INTERNAL-SETTLEMENT-REFERENCE",
                money("100"),
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 9),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 9, 10, 0),
                List.of(new RepaymentAllocation(
                        UUID.randomUUID(),
                        transactionId,
                        1,
                        UUID.randomUUID(),
                        RepaymentAllocationComponent.PRINCIPAL,
                        money("100")
                ))
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
