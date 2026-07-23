package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.domain.model.ContractSupersessionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record PrepareLoanContractRequest(
        @NotNull UUID preparationRequestId,
        @NotNull @PositiveOrZero Integer expectedCurrentContractVersion,
        ContractSupersessionReason supersessionReasonCode
) {
}
