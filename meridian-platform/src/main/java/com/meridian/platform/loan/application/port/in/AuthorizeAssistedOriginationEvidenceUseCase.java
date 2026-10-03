package com.meridian.platform.loan.application.port.in;

import java.util.UUID;

public interface AuthorizeAssistedOriginationEvidenceUseCase {

    AuthorizedAssistedOrigination authorizeEvidenceRead(UUID caseId);

    AuthorizedAssistedOrigination authorizeEvidenceMutation(UUID caseId);

    default UUID authorizeIdentityVerification(UUID caseId, boolean requireOpen) {
        throw new UnsupportedOperationException("Customer identity verification binding is not implemented.");
    }

    record AuthorizedAssistedOrigination(UUID caseId, String productCode) {
    }
}
