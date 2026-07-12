package com.meridian.platform.shared.application.audit;

import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.util.List;
import java.util.Objects;

public record BusinessAuditEvent(
        BusinessOperationContext operationContext,
        List<BusinessAuditEntry> entries
) {

    public BusinessAuditEvent {
        Objects.requireNonNull(operationContext, "operationContext must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Audit event entries must not be empty.");
        }
        entries = List.copyOf(entries);
    }

    public static BusinessAuditEvent single(
            BusinessOperationContext operationContext,
            BusinessAuditEntry entry
    ) {
        return new BusinessAuditEvent(operationContext, List.of(entry));
    }
}
