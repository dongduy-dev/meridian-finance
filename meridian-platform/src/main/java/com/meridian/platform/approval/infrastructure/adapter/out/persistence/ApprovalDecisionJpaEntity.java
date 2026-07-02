package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_decisions")
public class ApprovalDecisionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "review_recommendation_id", nullable = false)
    private UUID reviewRecommendationId;

    @Column(name = "approver_user_id", nullable = false)
    private UUID approverUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false)
    private ApprovalDecisionAction decision;

    @Column(name = "reason")
    private String reason;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ApprovalDecisionJpaEntity() {
    }

    public ApprovalDecisionJpaEntity(ApprovalDecision approvalDecision) {
        this.id = approvalDecision.id();
        this.loanApplicationId = approvalDecision.loanApplicationId();
        this.reviewRecommendationId = approvalDecision.reviewRecommendationId();
        this.approverUserId = approvalDecision.approverUserId();
        this.decision = approvalDecision.action();
        this.reason = approvalDecision.reason();
        this.internalNotes = approvalDecision.internalNotes();
        this.decidedAt = approvalDecision.decidedAt();
        this.createdAt = LocalDateTime.now();
    }

    public ApprovalDecision toDomain() {
        return new ApprovalDecision(
                id,
                loanApplicationId,
                reviewRecommendationId,
                approverUserId,
                decision,
                reason,
                internalNotes,
                decidedAt
        );
    }
}
