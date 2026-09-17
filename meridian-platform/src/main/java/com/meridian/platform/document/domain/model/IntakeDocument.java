package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record IntakeDocument(
        UUID id,
        UUID assistedOriginationCaseId,
        IntakeEvidenceType evidenceType,
        UUID currentVersionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public IntakeDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(assistedOriginationCaseId);
        Objects.requireNonNull(evidenceType);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public IntakeDocument withCurrentVersion(UUID versionId, LocalDateTime now) {
        return new IntakeDocument(id, assistedOriginationCaseId, evidenceType,
                Objects.requireNonNull(versionId), createdAt, Objects.requireNonNull(now));
    }
}
