package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_correction_requests")
public class LoanCorrectionRequestJpaEntity {
    @Id private UUID id;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Column(name = "source_review_cycle_id") private UUID sourceReviewCycleId;
    @Column(name = "source_action", nullable = false) private String sourceAction;
    @Enumerated(EnumType.STRING) @Column(name = "reason_code", nullable = false) private CorrectionReasonCode reasonCode;
    @Column(name = "created_by_user_id", nullable = false) private UUID createdByUserId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private LoanCorrectionRequestStatus status;
    @Column(name = "resubmission_request_id") private UUID resubmissionRequestId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "ready_at") private LocalDateTime readyAt;
    @Column(name = "resubmitted_at") private LocalDateTime resubmittedAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected LoanCorrectionRequestJpaEntity() {
    }

    public LoanCorrectionRequestJpaEntity(LoanCorrectionRequest request) {
        this.id = request.id();
        updateFrom(request);
    }

    public void updateFrom(LoanCorrectionRequest request) {
        loanApplicationId = request.loanApplicationId();
        sourceReviewCycleId = request.sourceReviewCycleId();
        sourceAction = request.sourceAction();
        reasonCode = request.reasonCode();
        createdByUserId = request.createdByUserId();
        status = request.status();
        resubmissionRequestId = request.resubmissionRequestId();
        createdAt = request.createdAt();
        readyAt = request.readyAt();
        resubmittedAt = request.resubmittedAt();
        updatedAt = request.resubmittedAt() != null ? request.resubmittedAt()
                : request.readyAt() != null ? request.readyAt() : request.createdAt();
    }

    public LoanCorrectionRequest toDomain() {
        return new LoanCorrectionRequest(id, loanApplicationId, sourceReviewCycleId, sourceAction, reasonCode,
                createdByUserId, status, resubmissionRequestId, createdAt, readyAt, resubmittedAt);
    }

    public UUID getLoanApplicationId() { return loanApplicationId; }
    public LoanCorrectionRequestStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
