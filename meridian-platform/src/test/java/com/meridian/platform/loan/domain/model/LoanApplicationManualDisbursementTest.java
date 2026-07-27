package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanApplicationManualDisbursementTest {

    @Test
    void confirmsManualDisbursementOnlyFromDisbursementPending() {
        LoanApplicationTransitionResult result = application(
                LoanApplicationStatus.DISBURSEMENT_PENDING
        ).confirmManualDisbursement();

        assertEquals(LoanApplicationStatus.DISBURSED, result.loanApplication().status());
        assertEquals(1, result.facts().size());
        LoanApplicationTransitionFact fact = result.facts().getFirst();
        assertEquals(LoanApplicationStatus.DISBURSEMENT_PENDING, fact.fromStatus());
        assertEquals(LoanApplicationStatus.DISBURSED, fact.toStatus());
        assertEquals(LoanApplicationTransitionAction.CONFIRM_MANUAL_DISBURSEMENT, fact.action());
    }

    @Test
    void rejectsInvalidSourceAndRepeatedTransition() {
        assertThrows(
                BusinessStateConflictException.class,
                () -> application(LoanApplicationStatus.CONTRACT_PENDING).confirmManualDisbursement()
        );
        assertThrows(
                BusinessStateConflictException.class,
                () -> application(LoanApplicationStatus.DISBURSED).confirmManualDisbursement()
        );
    }

    private static LoanApplication application(LoanApplicationStatus status) {
        return new LoanApplication(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SA-DISBURSEMENT-1",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                BigDecimal.valueOf(1_000).setScale(2),
                1,
                LocalDateTime.now()
        );
    }
}
