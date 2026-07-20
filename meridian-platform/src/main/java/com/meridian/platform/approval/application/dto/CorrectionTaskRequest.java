package com.meridian.platform.approval.application.dto;

import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.document.domain.model.DocumentType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CorrectionTaskRequest(
        CorrectionScope scope,
        CorrectionResponsibility responsibleParty,
        DocumentType documentType,
        boolean createChecklistItem,
        UUID checklistItemId,
        UUID baselineDocumentVersionId,
        @Size(max = 500) String customerInstruction,
        @Size(max = 500) String staffInstruction
) {
}
