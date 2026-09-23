package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewReason;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "partner_eligibility_reviews")
public class PartnerEligibilityReviewJpaEntity {

    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;
    @Column(name = "effective_month", nullable = false, length = 7)
    private String effectiveMonth;
    @Column(name = "source_import_batch_id")
    private UUID sourceImportBatchId;
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_outcome", nullable = false)
    private EmployeeVerificationOutcome triggerOutcome;
    @Column(name = "requested_employee_code", nullable = false, length = 50)
    private String requestedEmployeeCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartnerEligibilityReviewStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_outcome")
    private EmployeeVerificationOutcome decisionOutcome;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_reason")
    private PartnerEligibilityReviewReason decisionReason;
    @Column(name = "selected_partner_employee_id")
    private UUID selectedPartnerEmployeeId;
    @Column(name = "selected_import_batch_id")
    private UUID selectedImportBatchId;
    @Column(name = "reviewer_user_id")
    private UUID reviewerUserId;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PartnerEligibilityReviewJpaEntity() {
    }

    public PartnerEligibilityReviewJpaEntity(PartnerEligibilityReview review) {
        apply(review);
    }

    public void updateFrom(PartnerEligibilityReview review) {
        apply(review);
    }

    private void apply(PartnerEligibilityReview review) {
        id = review.id();
        customerId = review.customerId();
        partnerCompanyId = review.partnerCompanyId();
        effectiveMonth = review.effectiveMonth();
        sourceImportBatchId = review.sourceImportBatchId();
        triggerOutcome = review.triggerOutcome();
        requestedEmployeeCode = review.requestedEmployeeCode();
        status = review.status();
        decisionOutcome = review.decisionOutcome();
        decisionReason = review.decisionReason();
        selectedPartnerEmployeeId = review.selectedPartnerEmployeeId();
        selectedImportBatchId = review.selectedImportBatchId();
        reviewerUserId = review.reviewerUserId();
        reviewedAt = review.reviewedAt();
        createdAt = review.createdAt();
        updatedAt = review.updatedAt();
    }

    public PartnerEligibilityReview toDomain() {
        return new PartnerEligibilityReview(
                id, customerId, partnerCompanyId, effectiveMonth, sourceImportBatchId,
                triggerOutcome, requestedEmployeeCode, status, decisionOutcome, decisionReason,
                selectedPartnerEmployeeId, selectedImportBatchId, reviewerUserId, reviewedAt,
                createdAt, updatedAt
        );
    }
}
