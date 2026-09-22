package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.AssistedActionDocument;
import com.meridian.platform.document.domain.model.AssistedActionEvidenceType;
import com.meridian.platform.document.domain.model.AssistedOfferDecision;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assisted_action_documents")
public class AssistedActionDocumentJpaEntity {
    @Id private UUID id;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_type", nullable = false)
    private AssistedActionEvidenceType evidenceType;
    @Column(name = "approved_offer_id") private UUID approvedOfferId;
    @Enumerated(EnumType.STRING) @Column(name = "declared_offer_decision")
    private AssistedOfferDecision declaredOfferDecision;
    @Column(name = "loan_contract_id") private UUID loanContractId;
    @Column(name = "contract_version") private Integer contractVersion;
    @Column(name = "correction_request_id") private UUID correctionRequestId;
    @Column(name = "current_version_id") private UUID currentVersionId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected AssistedActionDocumentJpaEntity() {}
    AssistedActionDocumentJpaEntity(AssistedActionDocument value) { update(value); }

    void update(AssistedActionDocument value) {
        id = value.id(); loanApplicationId = value.loanApplicationId(); evidenceType = value.evidenceType();
        approvedOfferId = value.approvedOfferId(); declaredOfferDecision = value.declaredOfferDecision();
        loanContractId = value.loanContractId(); contractVersion = value.contractVersion();
        correctionRequestId = value.correctionRequestId();
        currentVersionId = value.currentVersionId(); createdAt = value.createdAt(); updatedAt = value.updatedAt();
    }

    AssistedActionDocument toDomain() {
        return new AssistedActionDocument(id, loanApplicationId, evidenceType, approvedOfferId,
                declaredOfferDecision, loanContractId, contractVersion, correctionRequestId,
                currentVersionId, createdAt, updatedAt);
    }
}
