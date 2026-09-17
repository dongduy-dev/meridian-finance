package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.domain.model.ProductCode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAssistedOriginationCaseRequest(
        @NotNull ProductCode productCode,
        UUID customerId
) {
}
