package com.meridian.platform.document.application.dto;

import java.util.List;
import java.util.UUID;

public record IntakeEvidenceDto(
        UUID intakeDocumentId,
        UUID assistedOriginationCaseId,
        String evidenceType,
        UUID currentVersionId,
        List<IntakeEvidenceVersionDto> versions
) {
    public IntakeEvidenceDto {
        versions = List.copyOf(versions);
    }
}
