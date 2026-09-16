package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeLoanProductActivationRequest(@NotNull Boolean active) {
}
