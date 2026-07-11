package com.meridian.platform.shared.application.audit;

import com.meridian.platform.shared.domain.model.ActionActor;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuditRecordRequestedEvent(
        UUID operationId,
        short sequenceNumber,
        ActionActor actor,
        AuditEntityType entityType,
        UUID entityId,
        AuditAction action,
        List<AuditPayloadEntry> payload,
        LocalDateTime occurredAt
) {
    public AuditRecordRequestedEvent {
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        payload = List.copyOf(Objects.requireNonNull(payload, "payload must not be null"));
        rejectDuplicatePayloadKeys(payload);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static void rejectDuplicatePayloadKeys(List<AuditPayloadEntry> payload) {
        Set<AuditPayloadKey> keys = EnumSet.noneOf(AuditPayloadKey.class);
        for (AuditPayloadEntry entry : payload) {
            if (!keys.add(entry.key())) {
                throw new IllegalArgumentException("payload contains duplicate key " + entry.key());
            }
        }
    }
}
