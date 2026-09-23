package com.meridian.platform.partner.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record PartnerEligibilityReview(
        UUID id,
        UUID customerId,
        UUID partnerCompanyId,
        String effectiveMonth,
        UUID sourceImportBatchId,
        EmployeeVerificationOutcome triggerOutcome,
        String requestedEmployeeCode,
        PartnerEligibilityReviewStatus status,
        EmployeeVerificationOutcome decisionOutcome,
        PartnerEligibilityReviewReason decisionReason,
        UUID selectedPartnerEmployeeId,
        UUID selectedImportBatchId,
        UUID reviewerUserId,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public PartnerEligibilityReview {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");
        Objects.requireNonNull(effectiveMonth, "effectiveMonth must not be null");
        Objects.requireNonNull(triggerOutcome, "triggerOutcome must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static PartnerEligibilityReview pending(
            UUID id,
            UUID customerId,
            UUID partnerCompanyId,
            String effectiveMonth,
            UUID sourceImportBatchId,
            EmployeeVerificationOutcome triggerOutcome,
            String requestedEmployeeCode,
            LocalDateTime createdAt
    ) {
        if (!isManualTrigger(triggerOutcome)) {
            throw new IllegalArgumentException("Review trigger outcome is not manually reviewable.");
        }
        return new PartnerEligibilityReview(
                id, customerId, partnerCompanyId, effectiveMonth, sourceImportBatchId,
                triggerOutcome, requestedEmployeeCode, PartnerEligibilityReviewStatus.PENDING,
                null, null, null, null, null, null, createdAt, createdAt
        );
    }

    public PartnerEligibilityReview approve(
            PartnerEmployee employee,
            PartnerEligibilityReviewReason reason,
            UUID reviewerUserId,
            LocalDateTime reviewedAt
    ) {
        requirePending();
        Objects.requireNonNull(employee, "employee must not be null");
        requireReason(reason, PartnerEligibilityReviewDecision.APPROVE);
        return new PartnerEligibilityReview(
                id, customerId, partnerCompanyId, effectiveMonth, sourceImportBatchId,
                triggerOutcome, requestedEmployeeCode, PartnerEligibilityReviewStatus.APPROVED,
                EmployeeVerificationOutcome.MANUAL_REVIEW_APPROVED, reason,
                employee.id(), employee.importBatchId(), Objects.requireNonNull(reviewerUserId),
                Objects.requireNonNull(reviewedAt), createdAt, reviewedAt
        );
    }

    public PartnerEligibilityReview reject(
            PartnerEligibilityReviewReason reason,
            UUID reviewerUserId,
            LocalDateTime reviewedAt
    ) {
        requirePending();
        requireReason(reason, PartnerEligibilityReviewDecision.REJECT);
        return new PartnerEligibilityReview(
                id, customerId, partnerCompanyId, effectiveMonth, sourceImportBatchId,
                triggerOutcome, requestedEmployeeCode, PartnerEligibilityReviewStatus.REJECTED,
                EmployeeVerificationOutcome.MANUAL_REVIEW_REJECTED, reason,
                null, null, Objects.requireNonNull(reviewerUserId), Objects.requireNonNull(reviewedAt),
                createdAt, reviewedAt
        );
    }

    public PartnerEligibilityReview supersede(LocalDateTime supersededAt) {
        requirePending();
        return new PartnerEligibilityReview(
                id, customerId, partnerCompanyId, effectiveMonth, sourceImportBatchId,
                triggerOutcome, requestedEmployeeCode, PartnerEligibilityReviewStatus.SUPERSEDED,
                null, null, null, null, null, null, createdAt, Objects.requireNonNull(supersededAt)
        );
    }

    public boolean isPending() {
        return status == PartnerEligibilityReviewStatus.PENDING;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Only a pending Partner eligibility review can be resolved.");
        }
    }

    private static void requireReason(
            PartnerEligibilityReviewReason reason,
            PartnerEligibilityReviewDecision decision
    ) {
        if (reason == null || !reason.supports(decision)) {
            throw new IllegalArgumentException("Review reason does not support the selected outcome.");
        }
    }

    private static boolean isManualTrigger(EmployeeVerificationOutcome outcome) {
        return outcome == EmployeeVerificationOutcome.NOT_FOUND
                || outcome == EmployeeVerificationOutcome.MULTIPLE_MATCHES
                || outcome == EmployeeVerificationOutcome.PENDING_MANUAL_REVIEW;
    }
}
