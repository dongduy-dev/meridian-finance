package com.meridian.platform.partner.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PartnerEmployeeVerificationRequest(
        @NotNull UUID customerId,
        @NotBlank String identityReference,
        @NotBlank String employeeCode
) {
}
