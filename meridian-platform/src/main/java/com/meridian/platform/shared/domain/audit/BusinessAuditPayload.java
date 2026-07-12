package com.meridian.platform.shared.domain.audit;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BusinessAuditPayload(Map<String, String> values) {

    private static final int MAX_VALUE_LENGTH = 120;

    public BusinessAuditPayload {
        Objects.requireNonNull(values, "values must not be null");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            validateText(key, "payload key");
            validateText(value, "payload value");
            copy.put(key, value);
        });
        values = Collections.unmodifiableMap(copy);
    }

    public static BusinessAuditPayload empty() {
        return new BusinessAuditPayload(Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void validateText(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) {
            throw new BusinessRuleViolationException(
                    "INVALID_AUDIT_PAYLOAD",
                    label + " must not be blank."
            );
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new BusinessRuleViolationException(
                    "INVALID_AUDIT_PAYLOAD",
                    label + " exceeds the maximum safe length."
            );
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessRuleViolationException(
                    "INVALID_AUDIT_PAYLOAD",
                    label + " must not contain control characters."
            );
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
