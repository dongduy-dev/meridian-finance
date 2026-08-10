package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CancelLoanApplicationRequest(@NotNull UUID requestId) {
    @Override
    public String toString() {
        return "CancelLoanApplicationRequest[cancellationEvidence=redacted]";
    }
}
