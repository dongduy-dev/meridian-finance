package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.CustomerOfferDecision;
import com.meridian.platform.loan.domain.model.StaffAssistedOfferResponse;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staff_assisted_offer_responses")
public class StaffAssistedOfferResponseJpaEntity {
    @Id private UUID id;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "approved_offer_id", nullable = false) private UUID approvedOfferId;
    @Enumerated(EnumType.STRING) @Column(name = "action", nullable = false) private CustomerOfferDecision action;
    @Column(name = "evidence_document_version_id", nullable = false) private UUID evidenceDocumentVersionId;
    @Column(name = "recorded_by_staff_user_id", nullable = false) private UUID recordedByStaffUserId;
    @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt;

    protected StaffAssistedOfferResponseJpaEntity() {}
    StaffAssistedOfferResponseJpaEntity(StaffAssistedOfferResponse value) {
        id = value.id(); requestId = value.requestId(); loanApplicationId = value.loanApplicationId();
        customerId = value.customerId(); approvedOfferId = value.approvedOfferId(); action = value.action();
        evidenceDocumentVersionId = value.evidenceDocumentVersionId();
        recordedByStaffUserId = value.recordedByStaffUserId(); recordedAt = value.recordedAt();
    }
    StaffAssistedOfferResponse toDomain() {
        return new StaffAssistedOfferResponse(id, requestId, loanApplicationId, customerId, approvedOfferId,
                action, evidenceDocumentVersionId, recordedByStaffUserId, recordedAt);
    }
}
