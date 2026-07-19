package com.meridian.platform.document.application.dto;

import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentWaiverReasonCode;

import java.util.UUID;

public record ReviewDocumentCommand(
        UUID loanApplicationId,
        UUID checklistItemId,
        UUID documentVersionId,
        UUID reviewRequestId,
        DocumentReviewOutcome outcome,
        DocumentWaiverReasonCode waiverReasonCode,
        String restrictedStaffNotes,
        UUID reviewerUserId,
        boolean waiverAuthorized
) {
}
