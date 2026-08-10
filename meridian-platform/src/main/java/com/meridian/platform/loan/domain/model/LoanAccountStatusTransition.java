package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.model.ActorType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanAccountStatusTransition(
        UUID id,
        UUID loanAccountId,
        int sequenceNumber,
        UUID operationId,
        LoanAccountStatus fromStatus,
        LoanAccountStatus toStatus,
        LoanAccountServicingAction action,
        ActorType actorType,
        UUID actorUserId,
        LocalDate servicingEvaluationDate,
        LocalDateTime occurredAt
) {

    public LoanAccountStatusTransition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
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
            throw new IllegalArgumentException("Transition must change LoanAccount status.");
        }
        validateActor(actorType, actorUserId);
        if (sequenceNumber == 1
                && (fromStatus != null
                || action != LoanAccountServicingAction.ACTIVATION_INITIALIZED)) {
            throw new IllegalArgumentException(
                    "Initial LoanAccount transition must represent activation."
            );
        }
        if (sequenceNumber > 1 && fromStatus == null) {
            throw new IllegalArgumentException(
                    "Non-initial LoanAccount transition requires fromStatus."
            );
        }
        if (action == LoanAccountServicingAction.APPROVED_SETTLEMENT
                && (actorType != ActorType.USER
                || (fromStatus != LoanAccountStatus.ACTIVE
                && fromStatus != LoanAccountStatus.OVERDUE)
                || toStatus != LoanAccountStatus.SETTLED)) {
            throw new IllegalArgumentException(
                    "Approved settlement must move an open LoanAccount to SETTLED."
            );
        }
        if (action == LoanAccountServicingAction.ADMINISTRATIVE_CLOSURE
                && (actorType != ActorType.USER
                || fromStatus != LoanAccountStatus.SETTLED
                || toStatus != LoanAccountStatus.CLOSED)) {
            throw new IllegalArgumentException(
                    "Administrative closure must move a settled LoanAccount to CLOSED."
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
