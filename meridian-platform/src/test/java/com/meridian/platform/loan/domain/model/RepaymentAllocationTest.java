package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepaymentAllocationTest {

    @Test
    void acceptsPositiveWholeVndEvidence() {
        UUID transactionId = UUID.randomUUID();
        RepaymentAllocation allocation = new RepaymentAllocation(
                UUID.randomUUID(),
                transactionId,
                1,
                UUID.randomUUID(),
                RepaymentAllocationComponent.FEE,
                money("100")
        );

        assertEquals(transactionId, allocation.repaymentTransactionId());
        assertEquals(1, allocation.allocationSequence());
        assertEquals(money("100"), allocation.amount());
    }

    @Test
    void rejectsNonPositiveFractionalAndNonSequentialEvidence() {
        for (BigDecimal invalid : new BigDecimal[]{
                money("0"),
                money("-1"),
                new BigDecimal("1.50")
        }) {
            assertThrows(BusinessRuleViolationException.class, () ->
                    allocation(1, invalid)
            );
        }
        assertThrows(BusinessRuleViolationException.class, () ->
                allocation(0, money("1"))
        );
    }

    private static RepaymentAllocation allocation(
            int sequence,
            BigDecimal amount
    ) {
        return new RepaymentAllocation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sequence,
                UUID.randomUUID(),
                RepaymentAllocationComponent.PRINCIPAL,
                amount
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
