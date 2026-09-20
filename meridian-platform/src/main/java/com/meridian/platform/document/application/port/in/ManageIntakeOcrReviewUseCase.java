package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.FinalizeIntakeOcrReviewRequest;
import com.meridian.platform.document.application.dto.IntakeOcrReviewDto;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;

import java.util.UUID;

public interface ManageIntakeOcrReviewUseCase {

    IntakeOcrReviewDto get(UUID caseId, IntakeEvidenceType evidenceType, UUID versionId);

    IntakeOcrReviewDto finalizeReview(
            UUID caseId,
            IntakeEvidenceType evidenceType,
            UUID versionId,
            FinalizeIntakeOcrReviewRequest request
    );
}
