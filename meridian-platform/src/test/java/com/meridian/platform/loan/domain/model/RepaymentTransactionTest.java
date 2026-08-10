package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepaymentTransactionTest {

    @Test
    void acceptsCanonicalReferenceAndOwnsImmutableOrderedAllocations() {
        UUID transactionId = UUID.randomUUID();
        RepaymentAllocation allocation = allocation(
                transactionId,
                1,
                RepaymentAllocationComponent.PRINCIPAL,
                "100"
        );
        ArrayList<RepaymentAllocation> source = new ArrayList<>(List.of(allocation));

        RepaymentTransaction transaction = RepaymentTransaction.recorded(
                transactionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BANK.REF/1",
                money("100"),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 7, 27, 10, 0),
                source
        );
        source.clear();

        assertTrue(transaction.externalPaymentReference().equals("BANK.REF/1"));
        assertEquals(RepaymentTransactionType.REPAYMENT, transaction.transactionType());
        assertEquals(1, transaction.allocations().size());
        assertThrows(UnsupportedOperationException.class,
                () -> transaction.allocations().clear());
        assertFalse(transaction.toString().contains(
                transaction.externalPaymentReference()
        ));
    }

    @Test
    void rejectsNoncanonicalReferenceWithoutRenderingIt() {
        String noncanonical = " private-reference ";
        UUID transactionId = UUID.randomUUID();
        BusinessRuleViolationException rejected = assertThrows(
                BusinessRuleViolationException.class,
                () -> transaction(
                        transactionId,
                        money("100"),
                        List.of(allocation(
                                transactionId,
                                1,
                                RepaymentAllocationComponent.PRINCIPAL,
                                "100"
                        )),
                        noncanonical
                )
        );

        assertFalse(rejected.getMessage().contains(noncanonical.trim()));
    }

    @Test
    void validatesG5DateBoundariesAndRejectsFutureOrPreDisbursementDates() {
        LocalDate disbursement = LocalDate.of(2026, 7, 20);
        LocalDate today = LocalDate.of(2026, 7, 27);

        RepaymentTransaction.validateValueDate(disbursement, disbursement, today);
        RepaymentTransaction.validateValueDate(today, disbursement, today);
        assertThrows(BusinessRuleViolationException.class, () ->
                RepaymentTransaction.validateValueDate(
                        disbursement.minusDays(1),
                        disbursement,
                        today
                ));
        assertThrows(BusinessRuleViolationException.class, () ->
                RepaymentTransaction.validateValueDate(
                        today.plusDays(1),
                        disbursement,
                        today
                ));
    }

    @Test
    void rejectsFractionalZeroMismatchedAndNonSequentialAllocations() {
        UUID transactionId = UUID.randomUUID();
        assertThrows(BusinessRuleViolationException.class, () ->
                new RepaymentAllocation(
                        UUID.randomUUID(),
                        transactionId,
                        1,
                        UUID.randomUUID(),
                        RepaymentAllocationComponent.FEE,
                        money("0")
                ));
        assertThrows(BusinessRuleViolationException.class, () ->
                transaction(
                        transactionId,
                        new BigDecimal("100.50"),
                        List.of(allocation(
                                transactionId,
                                1,
                                RepaymentAllocationComponent.PRINCIPAL,
                                "100"
                        ))
                ));
        assertThrows(BusinessRuleViolationException.class, () ->
                transaction(
                        transactionId,
                        money("100"),
                        List.of(new RepaymentAllocation(
                                UUID.randomUUID(),
                                transactionId,
                                2,
                                UUID.randomUUID(),
                                RepaymentAllocationComponent.PRINCIPAL,
                                money("100")
                        ))
                ));
    }

    @Test
    void recordsApprovedSettlementAsDistinctPaymentOperation() {
        UUID transactionId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 9, 10, 0);

        RepaymentTransaction transaction = RepaymentTransaction.approvedSettlement(
                transactionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SETTLEMENT-REFERENCE",
                money("100"),
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 9),
                approverId,
                approvedAt,
                List.of(allocation(
                        transactionId,
                        1,
                        RepaymentAllocationComponent.PRINCIPAL,
                        "100"
                ))
        );

        assertEquals(
                RepaymentTransactionType.APPROVED_SETTLEMENT,
                transaction.transactionType()
        );
        assertEquals(approverId, transaction.recordedByUserId());
        assertEquals(approvedAt, transaction.recordedAt());
        assertFalse(transaction.toString().contains("SETTLEMENT-REFERENCE"));
    }

    private static RepaymentTransaction transaction(
            UUID transactionId,
            BigDecimal received,
            List<RepaymentAllocation> allocations
    ) {
        return transaction(transactionId, received, allocations, "SAFE-REFERENCE");
    }

    private static RepaymentTransaction transaction(
            UUID transactionId,
            BigDecimal received,
            List<RepaymentAllocation> allocations,
            String externalPaymentReference
    ) {
        return new RepaymentTransaction(
                transactionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                externalPaymentReference,
                received,
                LocalDate.of(2026, 7, 27),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 7, 27, 10, 0),
                allocations
        );
    }

    private static RepaymentAllocation allocation(
            UUID transactionId,
            int sequence,
            RepaymentAllocationComponent component,
            String amount
    ) {
        return new RepaymentAllocation(
                UUID.randomUUID(),
                transactionId,
                sequence,
                UUID.randomUUID(),
                component,
                money(amount)
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
