package com.meridian.platform.customer.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerProfile(
        UUID id,
        UUID customerId,
        String fullName,
        ProtectedSensitiveValue identityReference,
        String phoneNumber,
        String residentialAddress,
        String employmentStatus,
        String employerName,
        boolean termsConsentAccepted,
        boolean dataProcessingConsentAccepted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public CustomerProfile {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        fullName = normalizeRequired(fullName, "fullName");
        if (identityReference == null) {
            throw new IllegalArgumentException("identityReference is required");
        }
        phoneNumber = normalizeRequired(phoneNumber, "phoneNumber");
        residentialAddress = normalizeRequired(residentialAddress, "residentialAddress");
        employmentStatus = normalizeRequired(employmentStatus, "employmentStatus");
        employerName = normalizeOptional(employerName);
    }

    public boolean isComplete() {
        return !fullName.isBlank()
                && identityReference != null
                && !phoneNumber.isBlank()
                && !residentialAddress.isBlank()
                && !employmentStatus.isBlank()
                && termsConsentAccepted
                && dataProcessingConsentAccepted;
    }

    public CustomerProfile withTimestamps(LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CustomerProfile(
                id,
                customerId,
                fullName,
                identityReference,
                phoneNumber,
                residentialAddress,
                employmentStatus,
                employerName,
                termsConsentAccepted,
                dataProcessingConsentAccepted,
                createdAt,
                updatedAt);
    }

    @Override
    public String toString() {
        return "CustomerProfile[id=" + id + ", customerId=" + customerId + ", identityReference=redacted]";
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}