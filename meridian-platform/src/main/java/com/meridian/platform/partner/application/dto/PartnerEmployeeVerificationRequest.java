package com.meridian.platform.partner.application.dto;

import jakarta.validation.constraints.NotBlank;

public record PartnerEmployeeVerificationRequest(
        @NotBlank String identityReference,
        @NotBlank String employeeCode
) {
}
