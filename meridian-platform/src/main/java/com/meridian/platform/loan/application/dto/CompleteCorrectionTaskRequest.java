package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CompleteCorrectionTaskRequest(@NotNull UUID completionRequestId) {
}
