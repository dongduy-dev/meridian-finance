package com.meridian.platform.approval.application.event;

import java.util.UUID;

public record CorrectionTaskFacts(
        String scope,
        String responsibleParty,
        String documentType,
        boolean createChecklistItem,
        UUID checklistItemId,
        UUID baselineDocumentVersionId,
        String customerInstruction,
        String staffInstruction
) {
}
