package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RevealDisbursementDestinationRequest(
        @NotNull @Positive Integer expectedContractVersion
) {
}
