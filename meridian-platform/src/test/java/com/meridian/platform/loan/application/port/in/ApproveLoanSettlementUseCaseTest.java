package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApproveLoanSettlementUseCaseTest {

    @Test
    void canonicalizesReferenceAndRedactsOperationIdentity() {
        UUID requestId = UUID.randomUUID();
        String sensitiveReference = " bank-settlement/001 ";

        ApproveLoanSettlementUseCase.Command command =
                new ApproveLoanSettlementUseCase.Command(
                        requestId,
                        UUID.randomUUID(),
                        new BigDecimal("100.00"),
                        LocalDate.of(2026, 8, 9),
                        sensitiveReference
                );

        assertEquals("BANK-SETTLEMENT/001", command.externalPaymentReference());
        assertFalse(command.toString().contains(requestId.toString()));
        assertFalse(command.toString().contains("BANK-SETTLEMENT/001"));
        assertFalse(command.toString().contains(sensitiveReference));
    }

    @Test
    void rejectsMissingIdentityInvalidAmountAndMissingDate() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 9);

        assertThrows(BusinessRuleViolationException.class, () ->
                new ApproveLoanSettlementUseCase.Command(
                        null, id, BigDecimal.ONE, date, "SETTLE-1"));
        assertThrows(BusinessRuleViolationException.class, () ->
                new ApproveLoanSettlementUseCase.Command(
                        id, id, BigDecimal.ZERO, date, "SETTLE-1"));
        assertThrows(BusinessRuleViolationException.class, () ->
                new ApproveLoanSettlementUseCase.Command(
                        id, id, new BigDecimal("1.50"), date, "SETTLE-1"));
        assertThrows(BusinessRuleViolationException.class, () ->
                new ApproveLoanSettlementUseCase.Command(
                        id, id, BigDecimal.ONE, null, "SETTLE-1"));
    }
}
