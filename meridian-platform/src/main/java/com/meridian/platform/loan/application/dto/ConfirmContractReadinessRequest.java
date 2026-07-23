package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ConfirmContractReadinessRequest(
        @NotNull UUID confirmationRequestId,
        @NotNull @Positive Integer expectedContractVersion
) {
}
