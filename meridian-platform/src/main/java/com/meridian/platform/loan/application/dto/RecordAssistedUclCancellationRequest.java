package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RecordAssistedUclCancellationRequest(
        @NotNull UUID requestId,
        @NotNull UUID expectedCorrectionRequestId,
        @NotNull UUID evidenceDocumentVersionId
) {
    @Override
    public String toString() {
        return "RecordAssistedUclCancellationRequest[cancellationEvidence=redacted]";
    }
}
