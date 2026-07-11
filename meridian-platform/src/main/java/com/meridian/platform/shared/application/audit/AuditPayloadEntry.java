package com.meridian.platform.shared.application.audit;

import java.util.Objects;

public record AuditPayloadEntry(AuditPayloadKey key, String value) {

    public AuditPayloadEntry {
        Objects.requireNonNull(key, "key must not be null");
        key.validateValue(value);
    }
}
