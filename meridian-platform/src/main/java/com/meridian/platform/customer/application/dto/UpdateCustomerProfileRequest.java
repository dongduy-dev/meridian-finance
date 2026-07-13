package com.meridian.platform.customer.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCustomerProfileRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 100) String identityReference,
        @NotBlank @Size(max = 50) String phoneNumber,
        @NotBlank @Size(max = 500) String residentialAddress,
        @NotBlank @Size(max = 50) String employmentStatus,
        @Size(max = 200) String employerName,
        @NotNull Boolean termsConsentAccepted,
        @NotNull Boolean dataProcessingConsentAccepted
) {
}
