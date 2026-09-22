package com.meridian.platform.partner.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PartnerEligibilityReviewDto(
        UUID reviewId,
        UUID customerId,
        PartnerCompanySummary partnerCompany,
        String effectiveMonth,
        UUID sourceImportBatchId,
        String triggerOutcome,
        String requestedEmployeeCode,
        String status,
        String decisionOutcome,
        String decisionReason,
        EmployeeCandidate selectedEmployee,
        UUID reviewerUserId,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<EmployeeCandidate> candidates,
        boolean approvalAvailable,
        boolean rejectionAvailable,
        String nonReviewableReason
) {
    public PartnerEligibilityReviewDto {
        candidates = List.copyOf(candidates);
    }

    public record PartnerCompanySummary(UUID id, String companyCode, String name, String status) {
    }

    public record EmployeeCandidate(
            UUID partnerEmployeeId,
            UUID importBatchId,
            String employeeCode,
            String employmentStatus,
            boolean active
    ) {
    }
}
