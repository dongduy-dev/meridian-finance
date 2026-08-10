package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.model.ActorType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record RepaymentInstallmentStatusTransition(
        UUID id,
        UUID repaymentScheduleItemId,
        int sequenceNumber,
        UUID operationId,
        RepaymentInstallmentStatus fromStatus,
        RepaymentInstallmentStatus toStatus,
        RepaymentInstallmentServicingAction action,
        ActorType actorType,
        UUID actorUserId,
        LocalDate servicingEvaluationDate,
        LocalDateTime occurredAt
) {

    public RepaymentInstallmentStatusTransition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(repaymentScheduleItemId,
                "repaymentScheduleItemId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive.");
        }
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(servicingEvaluationDate,
                "servicingEvaluationDate must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (fromStatus == toStatus) {
            throw new IllegalArgumentException(
                    "Transition must change installment status."
            );
        }
        validateActor(actorType, actorUserId);
        if (sequenceNumber == 1
                && (fromStatus != null
                || action
                != RepaymentInstallmentServicingAction.ACTIVATION_INITIALIZED)) {
            throw new IllegalArgumentException(
                    "Initial installment transition must represent activation."
            );
        }
        if (sequenceNumber > 1 && fromStatus == null) {
            throw new IllegalArgumentException(
                    "Non-initial installment transition requires fromStatus."
            );
        }
        if (action == RepaymentInstallmentServicingAction.APPROVED_SETTLEMENT
                && actorType != ActorType.USER) {
            throw new IllegalArgumentException(
                    "Approved settlement installment transitions require a user actor."
            );
        }
    }

    private static void validateActor(ActorType actorType, UUID actorUserId) {
        if (actorType == ActorType.USER && actorUserId == null) {
            throw new IllegalArgumentException("USER transitions require actorUserId.");
        }
        if (actorType == ActorType.SYSTEM && actorUserId != null) {
            throw new IllegalArgumentException("SYSTEM transitions cannot have actorUserId.");
        }
    }
}
