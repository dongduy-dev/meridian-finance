package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.IntakeEvidenceDto;
import com.meridian.platform.document.application.dto.IntakeEvidenceVersionDto;
import com.meridian.platform.document.application.dto.UploadIntakeEvidenceCommand;

import java.util.List;
import java.util.UUID;

public interface ManageIntakeEvidenceUseCase {

    List<IntakeEvidenceDto> findEvidence(UUID assistedOriginationCaseId);

    IntakeEvidenceVersionDto upload(UploadIntakeEvidenceCommand command);
}
