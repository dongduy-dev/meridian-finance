package com.meridian.platform.document.application.dto;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentWaiverReasonCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReviewDocumentRequest(
        @NotNull UUID reviewRequestId,
        @NotNull UUID documentVersionId,
        @NotNull DocumentReviewOutcome outcome,
        DocumentWaiverReasonCode waiverReasonCode,
        @Size(max = 2000) String restrictedStaffNotes,
        CorrectionReasonCode correctionReasonCode,
        @Size(max = 500) String customerInstruction
) {
}
