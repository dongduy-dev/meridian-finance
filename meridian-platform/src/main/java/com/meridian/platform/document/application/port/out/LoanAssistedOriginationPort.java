package com.meridian.platform.document.application.port.out;

import java.util.UUID;

public interface LoanAssistedOriginationPort {

    AuthorizedIntake authorizeRead(UUID caseId);

    AuthorizedIntake authorizeMutation(UUID caseId);

    default UUID authorizeIdentityVerification(UUID caseId, boolean requireOpen) {
        throw new UnsupportedOperationException("Customer identity verification binding is not implemented.");
    }

    record AuthorizedIntake(UUID caseId, String productCode) {
    }
}
