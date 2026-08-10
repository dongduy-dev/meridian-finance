package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordRepaymentUseCaseTest {
    @Test
    void canonicalisesReferenceAtBoundaryAndRedactsItFromRendering() {
        String sensitiveReference = " payroll-secret/001 ";
        UUID requestId = UUID.randomUUID();
        RecordRepaymentUseCase.Command command = new RecordRepaymentUseCase.Command(
                requestId, UUID.randomUUID(), sensitiveReference,
                new BigDecimal("100.00"), LocalDate.of(2026, 7, 30)
        );

        assertTrue(command.externalPaymentReference().equals("PAYROLL-SECRET/001"));
        assertFalse(command.toString().contains(requestId.toString()));
        assertFalse(command.toString().contains("PAYROLL-SECRET/001"));
        assertFalse(command.toString().contains(sensitiveReference));
    }

    @Test
    void rejectsMissingIdentityFractionalAmountAndMissingDate() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 7, 30);

        assertThrows(BusinessRuleViolationException.class, () ->
                new RecordRepaymentUseCase.Command(null, id, "PAY-1", BigDecimal.ONE, date));
        assertThrows(BusinessRuleViolationException.class, () ->
                new RecordRepaymentUseCase.Command(id, id, "PAY-1",
                        new BigDecimal("1.50"), date));
        assertThrows(BusinessRuleViolationException.class, () ->
                new RecordRepaymentUseCase.Command(id, id, "PAY-1", BigDecimal.ONE, null));
    }
}
