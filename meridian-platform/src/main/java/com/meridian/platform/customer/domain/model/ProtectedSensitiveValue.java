package com.meridian.platform.customer.domain.model;

import java.util.Objects;

public final class ProtectedSensitiveValue {

    private final String ciphertext;
    private final String fingerprint;
    private final String lastFour;

    public ProtectedSensitiveValue(String ciphertext, String fingerprint, String lastFour) {
        this.ciphertext = requireText(ciphertext, "ciphertext");
        this.fingerprint = requireText(fingerprint, "fingerprint");
        this.lastFour = requireLastFour(lastFour);
    }

    public String ciphertext() {
        return ciphertext;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String lastFour() {
        return lastFour;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String requireLastFour(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("lastFour is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 4) {
            throw new IllegalArgumentException("lastFour must not exceed 4 characters");
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProtectedSensitiveValue that)) {
            return false;
        }
        return ciphertext.equals(that.ciphertext)
                && fingerprint.equals(that.fingerprint)
                && lastFour.equals(that.lastFour);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ciphertext, fingerprint, lastFour);
    }

    @Override
    public String toString() {
        return "ProtectedSensitiveValue[redacted]";
    }
}