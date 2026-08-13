package com.meridian.platform.loan.application.dto;

import java.util.UUID;

public record SubmissionEvidenceRequirementDto(
        UUID checklistItemId,
        String documentType,
        String requirementStatus
) {
}
