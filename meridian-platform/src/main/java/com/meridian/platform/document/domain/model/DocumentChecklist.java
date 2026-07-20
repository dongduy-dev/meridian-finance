package com.meridian.platform.document.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DocumentChecklist(
        UUID id,
        UUID loanApplicationId,
        DocumentChecklistStage stage,
        List<DocumentChecklistItem> items,
        LocalDateTime createdAt
) {
    public DocumentChecklist {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
