package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.model.ActionActor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanApplicationStatusTransition(
        UUID id,
        UUID loanApplicationId,
        UUID operationId,
        short sequenceNumber,
        LoanApplicationStatus fromStatus,
        LoanApplicationStatus toStatus,
        LoanApplicationTransitionAction action,
        String reason,
        ActionActor actor,
        LocalDateTime occurredAt
) {
    public LoanApplicationStatusTransition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (fromStatus != null && fromStatus == toStatus) {
            throw new IllegalArgumentException("A transition must change status");
        }
    }

    public static LoanApplicationStatusTransition from(
            UUID loanApplicationId,
            UUID operationId,
            short sequenceNumber,
            LoanApplicationTransitionFact fact,
            String reason,
            ActionActor actor,
            LocalDateTime occurredAt
    ) {
        return new LoanApplicationStatusTransition(
                UUID.randomUUID(),
                loanApplicationId,
                operationId,
                sequenceNumber,
                fact.fromStatus(),
                fact.toStatus(),
                fact.action(),
                reason,
                actor,
                occurredAt
        );
    }
}
