package com.meridian.platform.loan.application.port.in;

import java.util.UUID;

public interface AuthorizeAssistedOriginationEvidenceUseCase {

    AuthorizedAssistedOrigination authorizeEvidenceRead(UUID caseId);

    AuthorizedAssistedOrigination authorizeEvidenceMutation(UUID caseId);

    record AuthorizedAssistedOrigination(UUID caseId, String productCode) {
    }
}
