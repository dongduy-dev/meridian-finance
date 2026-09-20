package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.IntakeOcrJobDto;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;

import java.util.UUID;

public interface ManageIntakeOcrUseCase {

    IntakeOcrJobDto start(UUID caseId, IntakeEvidenceType evidenceType, UUID versionId);

    IntakeOcrJobDto getStatus(UUID caseId, IntakeEvidenceType evidenceType, UUID versionId);
}
