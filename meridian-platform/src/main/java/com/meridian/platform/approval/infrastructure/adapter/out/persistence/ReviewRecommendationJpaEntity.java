package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.ReviewRecommendation;
import com.meridian.platform.approval.domain.model.ReviewRecommendationAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_recommendations")
public class ReviewRecommendationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "review_cycle_id", nullable = false)
    private UUID reviewCycleId;

    @Column(name = "loan_officer_user_id", nullable = false)
    private UUID loanOfficerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false)
    private ReviewRecommendationAction recommendation;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code")
    private CorrectionReasonCode reasonCode;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ReviewRecommendationJpaEntity() {
    }

    public ReviewRecommendationJpaEntity(ReviewRecommendation recommendation) {
        this.id = recommendation.id();
        this.loanApplicationId = recommendation.loanApplicationId();
        this.reviewCycleId = recommendation.reviewCycleId();
        this.loanOfficerUserId = recommendation.loanOfficerUserId();
        this.recommendation = recommendation.action();
        this.reason = recommendation.reason();
        this.reasonCode = recommendation.reasonCode();
        this.internalNotes = recommendation.internalNotes();
        this.submittedAt = recommendation.submittedAt();
        this.createdAt = LocalDateTime.now();
    }

    public ReviewRecommendation toDomain() {
        return new ReviewRecommendation(
                id,
                loanApplicationId,
                reviewCycleId,
                loanOfficerUserId,
                recommendation,
                reason,
                reasonCode,
                internalNotes,
                submittedAt
        );
    }
}
