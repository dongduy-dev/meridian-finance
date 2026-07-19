package com.meridian.platform.document.application.dto;

import com.meridian.platform.document.domain.model.DocumentUploaderActorType;

import java.io.InputStream;
import java.util.UUID;

public record UploadDocumentCommand(
        UUID loanApplicationId,
        UUID checklistItemId,
        UUID uploadRequestId,
        UUID expectedCurrentVersionId,
        String originalFilename,
        String declaredMimeType,
        InputStream content,
        DocumentUploaderActorType uploaderActorType,
        UUID uploaderUserId,
        UUID uploaderCustomerId
) {
}
