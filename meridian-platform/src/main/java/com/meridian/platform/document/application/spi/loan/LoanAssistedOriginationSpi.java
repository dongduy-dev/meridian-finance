package com.meridian.platform.document.application.spi.loan;

import java.util.UUID;

public interface LoanAssistedOriginationSpi {

    AuthorizedIntake authorizeRead(UUID caseId);

    AuthorizedIntake authorizeMutation(UUID caseId);

    UUID authorizeIdentityVerification(UUID caseId, boolean requireOpen);

    record AuthorizedIntake(UUID caseId, String productCode) {
    }
}
