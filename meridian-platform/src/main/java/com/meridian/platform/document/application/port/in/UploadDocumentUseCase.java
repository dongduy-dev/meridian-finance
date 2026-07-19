package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;

public interface UploadDocumentUseCase {
    DocumentVersionDto upload(UploadDocumentCommand command);
}
