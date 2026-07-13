package com.meridian.platform.customer.application.dto;

public record CustomerProfileDto(
        String fullName,
        String phoneNumber,
        String residentialAddress,
        String employmentStatus,
        String employerName,
        boolean termsConsentAccepted,
        boolean dataProcessingConsentAccepted
) {
}
