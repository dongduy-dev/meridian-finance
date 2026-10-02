package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.DocumentContentDto;
import com.meridian.platform.document.application.dto.StaffAssistedActionEvidenceDto;
import com.meridian.platform.document.domain.model.AssistedActionEvidenceType;

import java.util.List;
import java.util.UUID;

public interface ReadStaffAssistedActionEvidenceUseCase {
    List<StaffAssistedActionEvidenceDto> query(UUID loanApplicationId);

    DocumentContentDto read(UUID loanApplicationId, AssistedActionEvidenceType evidenceType, UUID documentVersionId);
}
