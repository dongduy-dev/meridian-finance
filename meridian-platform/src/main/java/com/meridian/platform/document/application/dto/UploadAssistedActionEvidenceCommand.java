package com.meridian.platform.document.application.dto;

import com.meridian.platform.document.domain.model.AssistedActionEvidenceType;
import com.meridian.platform.document.domain.model.AssistedOfferDecision;

import java.io.InputStream;
import java.util.UUID;

public record UploadAssistedActionEvidenceCommand(
        UUID loanApplicationId,
        AssistedActionEvidenceType evidenceType,
        UUID approvedOfferId,
        AssistedOfferDecision declaredOfferDecision,
        UUID loanContractId,
        Integer contractVersion,
        UUID correctionRequestId,
        UUID uploadRequestId,
        UUID expectedCurrentVersionId,
        String originalFilename,
        String declaredMimeType,
        InputStream content
) {
}
