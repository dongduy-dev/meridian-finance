package com.meridian.platform.loan.domain.model;

import com.meridian.platform.loan.testsupport.LoanContractTestData;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanAccountClosureTest {

    @Test
    void recordsEvidenceFromTheCorrespondingClosedAccountState() {
        LoanAccount closed = closedAccount();
        UUID requestId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        LoanAccountClosure closure = LoanAccountClosure.recorded(
                UUID.randomUUID(),
                closed,
                requestId,
                actorId,
                closed.updatedAt()
        );

        assertEquals(closed.loanApplicationId(), closure.loanApplicationId());
        assertEquals(closed.id(), closure.loanAccountId());
        assertEquals(requestId, closure.requestId());
        assertEquals(actorId, closure.closedByUserId());
        assertFalse(closure.toString().contains("reasonCode"));
    }

    @Test
    void rejectsEvidenceThatDoesNotMatchClosedAccountTime() {
        LoanAccount closed = closedAccount();

        assertThrows(BusinessRuleViolationException.class, () ->
                LoanAccountClosure.recorded(
                        UUID.randomUUID(),
                        closed,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        closed.updatedAt().plusSeconds(1)
                )
        );
    }

    private static LoanAccount closedAccount() {
        LoanAccount active = LoanAccount.activate(
                UUID.randomUUID(),
                LoanContractTestData.ready(),
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );
        LocalDateTime settledAt = LocalDateTime.of(2026, 8, 9, 9, 0);
        RepaymentBalance paid = new RepaymentBalance(
                active.approvedPrincipal(),
                active.totalInterest(),
                active.feeAmount(),
                active.totalRepaymentAmount(),
                zero(), zero(), zero(), zero(),
                LocalDate.of(2026, 8, 9),
                settledAt,
                LocalDate.of(2026, 8, 9)
        );
        LoanAccount settled = active.withServicingState(
                paid,
                LoanAccountStatus.SETTLED,
                settledAt
        );
        return settled.closeAdministratively(settledAt.plusMinutes(1));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
