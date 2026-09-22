package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.domain.model.CustomerOfferDecision;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RecordAssistedOfferResponseRequest(
        @NotNull UUID requestId,
        @NotNull UUID expectedApprovedOfferId,
        @NotNull CustomerOfferDecision action,
        @NotNull UUID evidenceDocumentVersionId
) {
}
