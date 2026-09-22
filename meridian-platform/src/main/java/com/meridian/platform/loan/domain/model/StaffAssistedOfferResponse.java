package com.meridian.platform.loan.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record StaffAssistedOfferResponse(
        UUID id,
        UUID requestId,
        UUID loanApplicationId,
        UUID customerId,
        UUID approvedOfferId,
        CustomerOfferDecision action,
        UUID evidenceDocumentVersionId,
        UUID recordedByStaffUserId,
        LocalDateTime recordedAt
) {
    public StaffAssistedOfferResponse {
        Objects.requireNonNull(id);
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(loanApplicationId);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(approvedOfferId);
        Objects.requireNonNull(action);
        Objects.requireNonNull(evidenceDocumentVersionId);
        Objects.requireNonNull(recordedByStaffUserId);
        Objects.requireNonNull(recordedAt);
    }
}
