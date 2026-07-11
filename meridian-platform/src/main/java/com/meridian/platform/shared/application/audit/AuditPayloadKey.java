package com.meridian.platform.shared.application.audit;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public enum AuditPayloadKey {
    LOAN_APPLICATION_ID(ValueType.UUID),
    PRODUCT_CODE(ValueType.CODE),
    SALARY_ADVANCE_LIMIT_ID(ValueType.UUID),
    MOVEMENT_ID(ValueType.UUID),
    MOVEMENT_TYPE(ValueType.CODE),
    RECOMMENDATION_ID(ValueType.UUID),
    RECOMMENDATION_ACTION(ValueType.CODE),
    APPROVAL_DECISION_ID(ValueType.UUID),
    APPROVAL_DECISION_ACTION(ValueType.CODE),
    APPROVED_OFFER_ID(ValueType.UUID),
    SOURCE_POLICY_ID(ValueType.UUID);

    private static final int MAX_VALUE_LENGTH = 128;
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");

    private final ValueType valueType;

    AuditPayloadKey(ValueType valueType) {
        this.valueType = valueType;
    }

    void validateValue(String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("value is too long");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("value must not contain control characters");
        }
        switch (valueType) {
            case UUID -> validateUuid(value);
            case CODE -> validateCode(value);
        }
    }

    private void validateUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name() + " must be a UUID", exception);
        }
    }

    private void validateCode(String value) {
        if (!CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name() + " must be an uppercase code");
        }
    }

    private enum ValueType {
        UUID,
        CODE
    }
}
