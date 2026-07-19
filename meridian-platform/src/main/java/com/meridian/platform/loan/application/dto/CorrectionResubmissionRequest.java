package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CorrectionResubmissionRequest(@NotNull UUID resubmissionRequestId) {
}
