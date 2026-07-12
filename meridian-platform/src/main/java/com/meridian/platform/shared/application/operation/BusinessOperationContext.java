package com.meridian.platform.shared.application.operation;

import com.meridian.platform.shared.domain.model.ActorType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record BusinessOperationContext(
        UUID operationId,
        ActorType actorType,
        UUID actorUserId,
        LocalDateTime occurredAt
) {

    public BusinessOperationContext {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (actorType == ActorType.USER && actorUserId == null) {
            throw new IllegalArgumentException("USER operations require actorUserId.");
        }
        if (actorType == ActorType.SYSTEM && actorUserId != null) {
            throw new IllegalArgumentException("SYSTEM operations must not have actorUserId.");
        }
    }

    public static BusinessOperationContext user(
            UUID operationId,
            UUID actorUserId,
            LocalDateTime occurredAt
    ) {
        return new BusinessOperationContext(operationId, ActorType.USER, actorUserId, occurredAt);
    }

    public static BusinessOperationContext system(
            UUID operationId,
            LocalDateTime occurredAt
    ) {
        return new BusinessOperationContext(operationId, ActorType.SYSTEM, null, occurredAt);
    }
}
