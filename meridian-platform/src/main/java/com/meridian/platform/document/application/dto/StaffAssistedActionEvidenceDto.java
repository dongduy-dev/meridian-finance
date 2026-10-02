package com.meridian.platform.document.application.dto;

import java.util.List;
import java.util.UUID;

public record StaffAssistedActionEvidenceDto(
        UUID documentId, String evidenceType, UUID approvedOfferId, String declaredOfferDecision,
        UUID loanContractId, Integer contractVersion, UUID correctionRequestId,
        StaffDocumentChecklistDto.VersionDto currentVersion,
        List<StaffDocumentChecklistDto.VersionDto> versionHistory
) {
    public StaffAssistedActionEvidenceDto {
        versionHistory = List.copyOf(versionHistory);
    }
}
