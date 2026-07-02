package com.meridian.platform.approval.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalDecisionTest {

    @Test
    void recordsApprovalDecisionWithoutReason() {
        ApprovalDecision decision = ApprovalDecision.recorded(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ApprovalDecisionAction.APPROVE,
                null,
                "ready",
                LocalDateTime.now()
        );

        assertEquals(ApprovalDecisionAction.APPROVE, decision.action());
        assertNull(decision.reason());
    }

    @Test
    void requiresReasonForRejectionDecision() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> ApprovalDecision.recorded(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ApprovalDecisionAction.REJECT,
                        " ",
                        null,
                        LocalDateTime.now()
                )
        );

        assertEquals("APPROVAL_DECISION_REASON_REQUIRED", exception.getErrorCode());
    }

    @Test
    void trimsReasonForReturnDecision() {
        ApprovalDecision decision = ApprovalDecision.recorded(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW,
                " needs more review ",
                " note ",
                LocalDateTime.now()
        );

        assertEquals("needs more review", decision.reason());
        assertEquals("note", decision.internalNotes());
    }
}
