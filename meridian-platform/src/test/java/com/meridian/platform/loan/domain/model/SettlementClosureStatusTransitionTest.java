package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementClosureStatusTransitionTest {

    @Test
    void acceptsApprovedSettlementAndAdministrativeClosureTransitions() {
        UUID actorId = UUID.randomUUID();
        LoanAccountStatusTransition settlement = transition(
                LoanAccountStatus.ACTIVE,
                LoanAccountStatus.SETTLED,
                LoanAccountServicingAction.APPROVED_SETTLEMENT,
                ActorType.USER,
                actorId
        );
        LoanAccountStatusTransition closure = transition(
                LoanAccountStatus.SETTLED,
                LoanAccountStatus.CLOSED,
                LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE,
                ActorType.USER,
                actorId
        );

        assertEquals(LoanAccountStatus.SETTLED, settlement.toStatus());
        assertEquals(LoanAccountStatus.CLOSED, closure.toStatus());
    }

    @Test
    void rejectsWrongSettlementOrClosureTransitionShape() {
        UUID actorId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> transition(
                LoanAccountStatus.SETTLED,
                LoanAccountStatus.CLOSED,
                LoanAccountServicingAction.APPROVED_SETTLEMENT,
                ActorType.USER,
                actorId
        ));
        assertThrows(IllegalArgumentException.class, () -> transition(
                LoanAccountStatus.ACTIVE,
                LoanAccountStatus.CLOSED,
                LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE,
                ActorType.USER,
                actorId
        ));
        assertThrows(IllegalArgumentException.class, () -> transition(
                LoanAccountStatus.ACTIVE,
                LoanAccountStatus.SETTLED,
                LoanAccountServicingAction.APPROVED_SETTLEMENT,
                ActorType.SYSTEM,
                null
        ));
    }

    private static LoanAccountStatusTransition transition(
            LoanAccountStatus from,
            LoanAccountStatus to,
            LoanAccountServicingAction action,
            ActorType actorType,
            UUID actorId
    ) {
        return new LoanAccountStatusTransition(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                from,
                to,
                action,
                actorType,
                actorId,
                LocalDate.of(2026, 8, 9),
                LocalDateTime.of(2026, 8, 9, 10, 0)
        );
    }
}
