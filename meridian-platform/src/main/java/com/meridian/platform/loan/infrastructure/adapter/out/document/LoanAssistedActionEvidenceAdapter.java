package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.in.AssistedActionEvidenceUseCase;
import com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class LoanAssistedActionEvidenceAdapter implements LoanAssistedActionEvidencePort {

    private final AssistedActionEvidenceUseCase evidence;

    public LoanAssistedActionEvidenceAdapter(AssistedActionEvidenceUseCase evidence) {
        this.evidence = evidence;
    }

    @Override
    public EvidenceSnapshot requireCurrentOfferEvidence(
            UUID loanApplicationId, UUID approvedOfferId, String decision, UUID documentVersionId
    ) {
        return snapshot(evidence.requireCurrentOfferEvidence(loanApplicationId, approvedOfferId, decision, documentVersionId));
    }

    @Override
    public EvidenceSnapshot requireCurrentContractEvidence(
            UUID loanApplicationId, UUID loanContractId, int contractVersion, UUID documentVersionId
    ) {
        return snapshot(evidence.requireCurrentContractEvidence(loanApplicationId, loanContractId, contractVersion, documentVersionId));
    }

    @Override
    public EvidenceSnapshot requireCurrentCancellationEvidence(
            UUID loanApplicationId, UUID correctionRequestId, UUID documentVersionId
    ) {
        return snapshot(evidence.requireCurrentCancellationEvidence(loanApplicationId, correctionRequestId, documentVersionId));
    }

    @Override
    public Optional<EvidenceSnapshot> findOfferEvidence(UUID loanApplicationId, UUID approvedOfferId) {
        return evidence.findOfferEvidence(loanApplicationId, approvedOfferId).map(LoanAssistedActionEvidenceAdapter::snapshot);
    }

    @Override
    public Optional<EvidenceSnapshot> findContractEvidence(UUID loanApplicationId, UUID loanContractId, int contractVersion) {
        return evidence.findContractEvidence(loanApplicationId, loanContractId, contractVersion)
                .map(LoanAssistedActionEvidenceAdapter::snapshot);
    }

    @Override
    public Optional<EvidenceSnapshot> findCancellationEvidence(UUID loanApplicationId, UUID correctionRequestId) {
        return evidence.findCancellationEvidence(loanApplicationId, correctionRequestId)
                .map(LoanAssistedActionEvidenceAdapter::snapshot);
    }

    private static EvidenceSnapshot snapshot(AssistedActionEvidenceUseCase.EvidenceSnapshot source) {
        return new EvidenceSnapshot(source.documentId(), source.documentVersionId(), source.evidenceType(),
                source.approvedOfferId(), source.declaredOfferDecision(), source.loanContractId(), source.contractVersion(),
                source.correctionRequestId(), source.versionNumber(), source.detectedMimeType(), source.byteSize(), source.uploadedAt());
    }
}
