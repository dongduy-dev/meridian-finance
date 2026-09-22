package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.AssistedActionEvidenceVersionDto;
import com.meridian.platform.document.application.dto.UploadAssistedActionEvidenceCommand;

public interface ManageAssistedActionEvidenceUseCase {

    AssistedActionEvidenceVersionDto upload(UploadAssistedActionEvidenceCommand command);
}
