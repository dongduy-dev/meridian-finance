package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AcknowledgeLoanContractRequest(
        @NotNull UUID acknowledgmentRequestId,
        @NotNull @Positive Integer expectedContractVersion
) {
}
