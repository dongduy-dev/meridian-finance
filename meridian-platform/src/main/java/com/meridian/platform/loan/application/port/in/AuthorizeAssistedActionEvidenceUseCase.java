package com.meridian.platform.loan.application.port.in;

import java.util.UUID;

public interface AuthorizeAssistedActionEvidenceUseCase {

    void authorizeOfferEvidence(UUID loanApplicationId, UUID approvedOfferId, String declaredDecision);

    void authorizeContractEvidence(UUID loanApplicationId, UUID loanContractId, int contractVersion);

    void authorizeCancellationEvidence(UUID loanApplicationId, UUID correctionRequestId);
}
