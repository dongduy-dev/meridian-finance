package com.meridian.platform.loan.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface LoanAssistedActionEvidencePort {

    EvidenceSnapshot requireCurrentOfferEvidence(
            UUID loanApplicationId, UUID approvedOfferId, String decision, UUID documentVersionId);

    EvidenceSnapshot requireCurrentContractEvidence(
            UUID loanApplicationId, UUID loanContractId, int contractVersion, UUID documentVersionId);

    EvidenceSnapshot requireCurrentCancellationEvidence(
            UUID loanApplicationId, UUID correctionRequestId, UUID documentVersionId);

    Optional<EvidenceSnapshot> findOfferEvidence(UUID loanApplicationId, UUID approvedOfferId);

    Optional<EvidenceSnapshot> findContractEvidence(UUID loanApplicationId, UUID loanContractId, int contractVersion);

    Optional<EvidenceSnapshot> findCancellationEvidence(UUID loanApplicationId, UUID correctionRequestId);

    record EvidenceSnapshot(
            UUID documentId,
            UUID documentVersionId,
            String evidenceType,
            UUID approvedOfferId,
            String declaredOfferDecision,
            UUID loanContractId,
            Integer contractVersion,
            UUID correctionRequestId,
            int versionNumber,
            String detectedMimeType,
            long byteSize,
            LocalDateTime uploadedAt
    ) {
    }
}
