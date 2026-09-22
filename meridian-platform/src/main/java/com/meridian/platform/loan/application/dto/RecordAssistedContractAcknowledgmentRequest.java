package com.meridian.platform.loan.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record RecordAssistedContractAcknowledgmentRequest(
        @NotNull UUID acknowledgmentRequestId,
        @NotNull UUID contractId,
        @Positive int expectedContractVersion,
        @NotNull UUID evidenceDocumentVersionId
) {
}
