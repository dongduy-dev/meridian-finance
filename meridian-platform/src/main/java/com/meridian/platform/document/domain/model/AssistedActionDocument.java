package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record AssistedActionDocument(
        UUID id,
        UUID loanApplicationId,
        AssistedActionEvidenceType evidenceType,
        UUID approvedOfferId,
        AssistedOfferDecision declaredOfferDecision,
        UUID loanContractId,
        Integer contractVersion,
        UUID currentVersionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public AssistedActionDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(loanApplicationId);
        Objects.requireNonNull(evidenceType);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        boolean offerEvidence = evidenceType == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE;
        boolean validOfferTarget = approvedOfferId != null && declaredOfferDecision != null
                && loanContractId == null && contractVersion == null;
        boolean validContractTarget = approvedOfferId == null && declaredOfferDecision == null
                && loanContractId != null && contractVersion != null && contractVersion > 0;
        if (offerEvidence ? !validOfferTarget : !validContractTarget) {
            throw new IllegalArgumentException("Assisted-action evidence target is invalid.");
        }
    }

    public AssistedActionDocument withCurrentVersion(UUID versionId, LocalDateTime now) {
        return new AssistedActionDocument(id, loanApplicationId, evidenceType, approvedOfferId,
                declaredOfferDecision, loanContractId, contractVersion, Objects.requireNonNull(versionId),
                createdAt, Objects.requireNonNull(now));
    }
}
