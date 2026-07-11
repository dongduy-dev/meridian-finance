package com.meridian.platform.audit.domain.model;

import com.meridian.platform.shared.domain.model.ActionActor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record AuditEvent(
        UUID id,
        UUID operationId,
        short sequenceNumber,
        ActionActor actor,
        String entityType,
        UUID entityId,
        String action,
        List<AuditEventPayloadEntry> payload,
        LocalDateTime occurredAt
) {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");

    public AuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        Objects.requireNonNull(actor, "actor must not be null");
        validateCode(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId must not be null");
        validateCode(action, "action");
        payload = List.copyOf(Objects.requireNonNull(payload, "payload must not be null"));
        rejectDuplicatePayloadKeys(payload);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AuditEvent from(
            UUID operationId,
            short sequenceNumber,
            ActionActor actor,
            String entityType,
            UUID entityId,
            String action,
            List<AuditEventPayloadEntry> payload,
            LocalDateTime occurredAt
    ) {
        return new AuditEvent(
                UUID.randomUUID(), operationId, sequenceNumber, actor, entityType, entityId, action, payload, occurredAt
        );
    }

    private static void validateCode(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (!CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be an uppercase code");
        }
    }

    private static void rejectDuplicatePayloadKeys(List<AuditEventPayloadEntry> payload) {
        Set<String> keys = new HashSet<>();
        for (AuditEventPayloadEntry entry : payload) {
            if (!keys.add(entry.key())) {
                throw new IllegalArgumentException("payload contains duplicate key " + entry.key());
            }
        }
    }
}
