package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.model.ActorType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanApplicationStatusTransition(
        UUID id,
        UUID loanApplicationId,
        UUID operationId,
        int sequenceNumber,
        LoanApplicationStatus fromStatus,
        LoanApplicationStatus toStatus,
        LoanApplicationTransitionAction action,
        String reason,
        ActorType actorType,
        UUID actorUserId,
        LocalDateTime occurredAt
) {

    public LoanApplicationStatusTransition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive.");
        }
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (fromStatus != null && fromStatus == toStatus) {
            throw new IllegalArgumentException("Transition must represent a status change.");
        }
        reason = normalizeOptionalText(reason);
        Objects.requireNonNull(actorType, "actorType must not be null");
        if (actorType == ActorType.USER && actorUserId == null) {
            throw new IllegalArgumentException("USER transitions require actorUserId.");
        }
        if (actorType == ActorType.SYSTEM && actorUserId != null) {
            throw new IllegalArgumentException("SYSTEM transitions must not have actorUserId.");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
