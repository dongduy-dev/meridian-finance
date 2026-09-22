package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.StaffAssistedContractAcknowledgment;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staff_assisted_contract_acknowledgments")
public class StaffAssistedContractAcknowledgmentJpaEntity {
    @Id private UUID id;
    @Column(name = "acknowledgment_request_id", nullable = false) private UUID acknowledgmentRequestId;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "loan_contract_id", nullable = false) private UUID loanContractId;
    @Column(name = "contract_version", nullable = false) private int contractVersion;
    @Column(name = "evidence_document_version_id", nullable = false) private UUID evidenceDocumentVersionId;
    @Column(name = "recorded_by_staff_user_id", nullable = false) private UUID recordedByStaffUserId;
    @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt;

    protected StaffAssistedContractAcknowledgmentJpaEntity() {}
    StaffAssistedContractAcknowledgmentJpaEntity(StaffAssistedContractAcknowledgment value) {
        id = value.id(); acknowledgmentRequestId = value.acknowledgmentRequestId();
        loanApplicationId = value.loanApplicationId(); customerId = value.customerId();
        loanContractId = value.loanContractId(); contractVersion = value.contractVersion();
        evidenceDocumentVersionId = value.evidenceDocumentVersionId();
        recordedByStaffUserId = value.recordedByStaffUserId(); recordedAt = value.recordedAt();
    }
    StaffAssistedContractAcknowledgment toDomain() {
        return new StaffAssistedContractAcknowledgment(id, acknowledgmentRequestId, loanApplicationId,
                customerId, loanContractId, contractVersion, evidenceDocumentVersionId,
                recordedByStaffUserId, recordedAt);
    }
}
