package com.meridian.platform.approval.application.dto;

import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CorrectionTaskRequest(
        CorrectionScope scope,
        CorrectionResponsibility responsibleParty,
        CorrectionDocumentType documentType,
        boolean createChecklistItem,
        UUID checklistItemId,
        UUID baselineDocumentVersionId,
        @Size(max = 500) String customerInstruction,
        @Size(max = 500) String staffInstruction
) {
}
