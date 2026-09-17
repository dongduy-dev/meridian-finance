package com.meridian.platform.document.application.dto;

import com.meridian.platform.document.domain.model.IntakeEvidenceType;

import java.io.InputStream;
import java.util.UUID;

public record UploadIntakeEvidenceCommand(
        UUID assistedOriginationCaseId,
        IntakeEvidenceType evidenceType,
        UUID uploadRequestId,
        UUID expectedCurrentVersionId,
        String originalFilename,
        String declaredMimeType,
        InputStream content
) {
}
