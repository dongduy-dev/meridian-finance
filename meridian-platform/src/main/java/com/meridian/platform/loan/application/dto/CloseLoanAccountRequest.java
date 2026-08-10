package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CloseLoanAccountRequest(@NotNull UUID requestId) {
    @Override
    public String toString() {
        return "CloseLoanAccountRequest[administrativeEvidence=redacted]";
    }
}
