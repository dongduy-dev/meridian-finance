package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.IntakeEvidenceDto;
import com.meridian.platform.document.application.dto.DocumentContentDto;
import com.meridian.platform.document.application.dto.IntakeEvidenceVersionDto;
import com.meridian.platform.document.application.dto.UploadIntakeEvidenceCommand;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;

import java.util.List;
import java.util.UUID;

public interface ManageIntakeEvidenceUseCase {

    List<IntakeEvidenceDto> findEvidence(UUID assistedOriginationCaseId);

    DocumentContentDto readContent(UUID assistedOriginationCaseId, IntakeEvidenceType evidenceType, UUID versionId);

    IntakeEvidenceVersionDto upload(UploadIntakeEvidenceCommand command);
}
