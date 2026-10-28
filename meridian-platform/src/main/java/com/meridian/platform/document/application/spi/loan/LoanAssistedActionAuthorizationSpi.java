package com.meridian.platform.document.application.spi.loan;

import java.util.UUID;

public interface LoanAssistedActionAuthorizationSpi {

    void authorizeOfferEvidence(UUID loanApplicationId, UUID approvedOfferId, String declaredDecision);

    void authorizeContractEvidence(UUID loanApplicationId, UUID loanContractId, int contractVersion);

    void authorizeCancellationEvidence(UUID loanApplicationId, UUID correctionRequestId);
}
