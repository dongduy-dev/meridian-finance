package com.meridian.platform.shared.application.audit;

import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;

import java.util.Objects;
import java.util.UUID;

public record BusinessAuditEntry(
        BusinessAuditAction action,
        BusinessAuditEntityType entityType,
        UUID entityId,
        BusinessAuditPayload payload
) {

    public BusinessAuditEntry {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        payload = payload == null ? BusinessAuditPayload.empty() : payload;
    }

    public static BusinessAuditEntry of(
            BusinessAuditAction action,
            BusinessAuditEntityType entityType,
            UUID entityId
    ) {
        return new BusinessAuditEntry(action, entityType, entityId, BusinessAuditPayload.empty());
    }
}
