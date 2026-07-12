package com.meridian.platform.audit.domain.model;

import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.model.ActorType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        UUID operationId,
        int sequenceNumber,
        ActorType actorType,
        UUID actorUserId,
        BusinessAuditEntityType entityType,
        UUID entityId,
        BusinessAuditAction action,
        BusinessAuditPayload payload,
        LocalDateTime occurredAt
) {

    public AuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive.");
        }
        Objects.requireNonNull(actorType, "actorType must not be null");
        if (actorType == ActorType.USER && actorUserId == null) {
            throw new IllegalArgumentException("USER audit events require actorUserId.");
        }
        if (actorType == ActorType.SYSTEM && actorUserId != null) {
            throw new IllegalArgumentException("SYSTEM audit events must not have actorUserId.");
        }
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        payload = payload == null ? BusinessAuditPayload.empty() : payload;
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
