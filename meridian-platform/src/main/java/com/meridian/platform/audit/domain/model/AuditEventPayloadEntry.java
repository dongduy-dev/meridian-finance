package com.meridian.platform.audit.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record AuditEventPayloadEntry(String key, String value) {

    private static final int MAX_VALUE_LENGTH = 128;
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");

    public AuditEventPayloadEntry {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("key must be an uppercase code");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("value is too long");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("value must not contain control characters");
        }
    }
}
