package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record StoredDocument(
        UUID id,
        UUID checklistItemId,
        UUID currentVersionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public StoredDocument {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(checklistItemId, "checklistItemId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public StoredDocument withCurrentVersion(UUID versionId, LocalDateTime changedAt) {
        return new StoredDocument(
                id,
                checklistItemId,
                Objects.requireNonNull(versionId, "versionId must not be null"),
                createdAt,
                Objects.requireNonNull(changedAt, "changedAt must not be null")
        );
    }
}
