package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.DocumentContentDto;

import java.util.UUID;

public interface ReadDocumentContentUseCase {
    DocumentContentDto read(UUID loanApplicationId, UUID checklistItemId, UUID documentVersionId);
}
