package com.meridian.platform.shared.domain.audit;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record BusinessAuditPayload(Map<String, String> values) {

    private static final int MAX_VALUE_LENGTH = 120;
    private static final Pattern UPPERCASE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    public BusinessAuditPayload {
        Objects.requireNonNull(values, "values must not be null");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        values.forEach((jsonName, value) -> {
            validateText(jsonName, "payload key");
            BusinessAuditPayloadKey key = BusinessAuditPayloadKey.fromJsonName(jsonName);
            validateValue(key, value);
            if (copy.put(jsonName, value) != null) {
                throw new BusinessRuleViolationException(
                        "DUPLICATE_AUDIT_PAYLOAD_KEY",
                        "Audit payload keys must be unique."
                );
            }
        });
        values = Collections.unmodifiableMap(copy);
    }

    public static BusinessAuditPayload empty() {
        return new BusinessAuditPayload(Map.of());
    }

    public static BusinessAuditPayload fromStored(Map<String, String> values) {
        return new BusinessAuditPayload(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    static BusinessRuleViolationException invalidPayload(String message) {
        return new BusinessRuleViolationException("INVALID_AUDIT_PAYLOAD", message);
    }

    private static void validateValue(BusinessAuditPayloadKey key, String value) {
        validateText(value, "payload value");
        switch (key.valueType()) {
            case UUID -> validateUuid(value);
            case CODE -> validateCode(value);
        }
    }

    private static void validateUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalidPayload("Audit payload UUID value is malformed.");
        }
    }

    private static void validateCode(String value) {
        if (!UPPERCASE_CODE_PATTERN.matcher(value).matches()) {
            throw invalidPayload("Audit payload code value must be an uppercase code.");
        }
    }

    private static void validateText(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) {
            throw invalidPayload(label + " must not be blank.");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw invalidPayload(label + " exceeds the maximum safe length.");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw invalidPayload(label + " must not contain control characters.");
        }
    }

    public static final class Builder {

        private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

        public Builder put(BusinessAuditPayloadKey key, UUID value) {
            return putString(key, Objects.requireNonNull(value, "value must not be null").toString());
        }

        public Builder put(BusinessAuditPayloadKey key, Enum<?> value) {
            return putString(key, Objects.requireNonNull(value, "value must not be null").name());
        }

        private Builder putString(BusinessAuditPayloadKey key, String value) {
            Objects.requireNonNull(key, "key must not be null");
            String jsonName = key.jsonName();
            if (values.containsKey(jsonName)) {
                throw new BusinessRuleViolationException(
                        "DUPLICATE_AUDIT_PAYLOAD_KEY",
                        "Audit payload keys must be unique."
                );
            }
            values.put(jsonName, value);
            return this;
        }

        public BusinessAuditPayload build() {
            return new BusinessAuditPayload(values);
        }
    }
}
