package com.meridian.platform.document.application.port.out;

import java.util.UUID;

public interface LoanAssistedOriginationPort {

    AuthorizedIntake authorizeRead(UUID caseId);

    AuthorizedIntake authorizeMutation(UUID caseId);

    record AuthorizedIntake(UUID caseId, String productCode) {
    }
}
