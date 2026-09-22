package com.meridian.platform.document.application.port.out;

import java.util.UUID;

public interface LoanAssistedActionAuthorizationPort {

    void authorizeOfferEvidence(UUID loanApplicationId, UUID approvedOfferId, String declaredDecision);

    void authorizeContractEvidence(UUID loanApplicationId, UUID loanContractId, int contractVersion);

    void authorizeCancellationEvidence(UUID loanApplicationId, UUID correctionRequestId);
}
